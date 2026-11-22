package com.example.mypsr.generator

import android.content.Context
import android.graphics.BitmapFactory
import com.example.mypsr.data.local.entity.SessionEntity
import com.example.mypsr.model.CapturedStep
import com.example.mypsr.util.ImageUtils
import com.example.mypsr.util.XssSanitizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HtmlReportGenerator {

    suspend fun generateHtmlReport(
        context: Context,
        session: SessionEntity,
        steps: List<CapturedStep>
    ): File = withContext(Dispatchers.IO) {
        val reportsDir = File(context.filesDir, "reports")
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }

        val htmlFile = File(reportsDir, "report_session_${session.id}.html")

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedDate = dateFormat.format(Date(session.createdAt))
        val escapedTitle = XssSanitizer.escapeHtml(session.title)

        val stepsHtmlBuilder = StringBuilder()

        steps.forEachIndexed { index, step ->
            val stepNum = index + 1
            val escapedDesc = XssSanitizer.escapeHtml(step.actionDescription)
            val escapedApp = XssSanitizer.escapeHtml(step.appName)
            val stepTime = dateFormat.format(Date(step.timestamp))

            // Load and encode screenshot to Base64
            val base64Img = if (!step.screenshotPath.isNullOrBlank()) {
                val imgFile = File(step.screenshotPath)
                if (imgFile.exists()) {
                    val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                    if (bitmap != null) {
                        var processedBitmap = bitmap
                        // Apply redaction boxes if present
                        if (step.redactionBoxes.isNotEmpty()) {
                            processedBitmap = ImageUtils.applyRedactions(bitmap, step.redactionBoxes)
                        }
                        val quality = com.example.mypsr.data.local.UserPreferences.getInstance(context).getScreenshotQuality().qualityValue
                        val jpegBytes = ImageUtils.compressBitmapToJpeg(processedBitmap, quality = quality)
                        if (processedBitmap != bitmap) {
                            processedBitmap.recycle()
                        }
                        bitmap.recycle()
                        ImageUtils.encodeBase64Jpeg(jpegBytes)
                    } else ""
                } else ""
            } else ""

            // Visual Click Location Marker if available
            val clickMarkerHtml = if (step.clickX != null && step.clickY != null) {
                """
                <div class="click-marker" title="Click Location"></div>
                """.trimIndent()
            } else ""

            val sensitiveBadge = if (step.isSensitive) {
                """<span class="badge badge-warning">Password / Sensitive Field Masked</span>"""
            } else ""

            stepsHtmlBuilder.append(
                """
                <div class="step-card" data-app="${escapedApp.lowercase()}" data-desc="${escapedDesc.lowercase()}">
                    <div class="step-header" onclick="toggleStep($stepNum)">
                        <div class="step-title-group">
                            <span class="step-badge">Step $stepNum</span>
                            <span class="app-badge">$escapedApp</span>
                            <span class="step-desc">$escapedDesc</span>
                        </div>
                        <div class="step-meta">
                            $sensitiveBadge
                            <span class="step-time">$stepTime</span>
                            <span class="toggle-icon" id="icon-$stepNum">▼</span>
                        </div>
                    </div>
                    <div class="step-content" id="step-content-$stepNum">
                        ${if (base64Img.isNotBlank()) """
                        <div class="image-container" onclick="openModal('$base64Img')">
                            <img src="$base64Img" alt="Step $stepNum Screenshot" class="screenshot-img" />
                            $clickMarkerHtml
                        </div>
                        """ else """
                        <div class="no-image">No Screenshot Captured</div>
                        """}
                    </div>
                </div>
                """.trimIndent()
            )
        }

        val fullHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>PSR Report - $escapedTitle</title>
                <style>
                    :root {
                        --bg-color: #0b0f19;
                        --card-bg: #131927;
                        --card-header: #1b2336;
                        --accent-color: #00e5ff;
                        --text-primary: #e2e8f0;
                        --text-secondary: #94a3b8;
                        --border-color: #1e293b;
                        --warning-color: #ffb300;
                    }
                    * { box-sizing: border-box; margin: 0; padding: 0; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                        background-color: var(--bg-color);
                        color: var(--text-primary);
                        padding: 24px 16px;
                        line-height: 1.5;
                    }
                    .container { max-width: 900px; margin: 0 auto; }
                    .header-panel {
                        background: var(--card-bg);
                        border: 1px solid var(--accent-color);
                        border-radius: 12px;
                        padding: 24px;
                        margin-bottom: 24px;
                        box-shadow: 0 4px 20px rgba(0, 229, 255, 0.15);
                    }
                    .header-title {
                        font-size: 24px;
                        font-weight: 700;
                        color: var(--accent-color);
                        margin-bottom: 8px;
                    }
                    .header-info {
                        display: flex;
                        flex-wrap: wrap;
                        gap: 16px;
                        font-size: 14px;
                        color: var(--text-secondary);
                        margin-bottom: 16px;
                    }
                    .controls-bar {
                        display: flex;
                        gap: 12px;
                        margin-top: 16px;
                    }
                    .search-input {
                        flex: 1;
                        padding: 10px 14px;
                        background: #090d16;
                        border: 1px solid var(--border-color);
                        border-radius: 8px;
                        color: var(--text-primary);
                        font-size: 14px;
                        outline: none;
                    }
                    .search-input:focus { border-color: var(--accent-color); }
                    .btn {
                        padding: 10px 16px;
                        background: var(--card-header);
                        border: 1px solid var(--border-color);
                        border-radius: 8px;
                        color: var(--text-primary);
                        font-size: 13px;
                        cursor: pointer;
                        transition: all 0.2s;
                    }
                    .btn:hover { border-color: var(--accent-color); color: var(--accent-color); }
                    .step-card {
                        background: var(--card-bg);
                        border: 1px solid var(--border-color);
                        border-radius: 10px;
                        margin-bottom: 16px;
                        overflow: hidden;
                    }
                    .step-header {
                        padding: 16px;
                        background: var(--card-header);
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        cursor: pointer;
                        user-select: none;
                    }
                    .step-title-group { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
                    .step-badge {
                        background: var(--accent-color);
                        color: #000;
                        font-weight: 700;
                        padding: 4px 10px;
                        border-radius: 6px;
                        font-size: 12px;
                    }
                    .app-badge {
                        background: #1e293b;
                        color: var(--accent-color);
                        padding: 4px 10px;
                        border-radius: 6px;
                        font-size: 12px;
                        border: 1px solid #334155;
                    }
                    .step-desc { font-weight: 600; font-size: 15px; }
                    .step-meta { display: flex; align-items: center; gap: 10px; font-size: 13px; color: var(--text-secondary); }
                    .badge-warning {
                        background: rgba(255, 179, 0, 0.2);
                        color: var(--warning-color);
                        border: 1px solid var(--warning-color);
                        padding: 2px 8px;
                        border-radius: 4px;
                        font-size: 11px;
                    }
                    .step-content { padding: 16px; text-align: center; }
                    .image-container {
                        position: relative;
                        display: inline-block;
                        max-width: 100%;
                        cursor: zoom-in;
                    }
                    .screenshot-img {
                        max-width: 100%;
                        max-height: 550px;
                        border-radius: 8px;
                        border: 1px solid var(--border-color);
                    }
                    .no-image { padding: 24px; color: var(--text-secondary); font-style: italic; }
                    .modal {
                        display: none;
                        position: fixed;
                        top: 0; left: 0; width: 100%; height: 100%;
                        background: rgba(0,0,0,0.9);
                        z-index: 1000;
                        justify-content: center;
                        align-items: center;
                    }
                    .modal img { max-width: 95%; max-height: 95%; border-radius: 8px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header-panel">
                        <div class="header-title">📱 Mobile PSR Step Report</div>
                        <div class="header-info">
                            <span><strong>Session:</strong> $escapedTitle</span>
                            <span><strong>Recorded:</strong> $formattedDate</span>
                            <span><strong>Total Steps:</strong> ${steps.size}</span>
                        </div>
                        <div class="controls-bar">
                            <input type="text" id="searchInput" class="search-input" placeholder="Search steps or app name..." onkeyup="filterSteps()" />
                            <button class="btn" onclick="toggleAll(true)">Expand All</button>
                            <button class="btn" onclick="toggleAll(false)">Collapse All</button>
                        </div>
                    </div>

                    <div id="stepsList">
                        ${stepsHtmlBuilder}
                    </div>
                </div>

                <div id="imageModal" class="modal" onclick="closeModal()">
                    <img id="modalImg" src="" alt="Zoomed Screenshot" />
                </div>

                <script>
                    function toggleStep(id) {
                        const content = document.getElementById('step-content-' + id);
                        const icon = document.getElementById('icon-' + id);
                        if (content.style.display === 'none') {
                            content.style.display = 'block';
                            icon.textContent = '▼';
                        } else {
                            content.style.display = 'none';
                            icon.textContent = '►';
                        }
                    }

                    function toggleAll(expand) {
                        const contents = document.querySelectorAll('.step-content');
                        const icons = document.querySelectorAll('.toggle-icon');
                        contents.forEach(c => c.style.display = expand ? 'block' : 'none');
                        icons.forEach(i => i.textContent = expand ? '▼' : '►');
                    }

                    function filterSteps() {
                        const query = document.getElementById('searchInput').value.toLowerCase();
                        const cards = document.querySelectorAll('.step-card');
                        cards.forEach(card => {
                            const app = card.getAttribute('data-app');
                            const desc = card.getAttribute('data-desc');
                            if (app.includes(query) || desc.includes(query)) {
                                card.style.display = 'block';
                            } else {
                                card.style.display = 'none';
                            }
                        });
                    }

                    function openModal(src) {
                        document.getElementById('modalImg').src = src;
                        document.getElementById('imageModal').style.display = 'flex';
                    }

                    function closeModal() {
                        document.getElementById('imageModal').style.display = 'none';
                    }
                </script>
            </body>
            </html>
        """.trimIndent()

        FileOutputStream(htmlFile).use { out ->
            out.write(fullHtml.toByteArray(Charsets.UTF_8))
            out.flush()
        }

        htmlFile
    }
}
