package com.example.mypsr.ui.viewer

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.example.mypsr.data.local.AppDatabase
import com.example.mypsr.data.repository.SessionRepositoryImpl
import com.example.mypsr.generator.HtmlReportGenerator
import com.example.mypsr.ui.theme.CyberCyan
import com.example.mypsr.util.ShareUtils
import kotlinx.coroutines.flow.firstOrNull
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportViewerScreen(
    sessionId: Long = 0L,
    fileUri: Uri? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var reportFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(sessionId, fileUri) {
        if (fileUri != null) {
            val path = fileUri.path
            if (path != null) {
                reportFile = File(path)
            }
            isLoading = false
        } else if (sessionId > 0L) {
            val db = AppDatabase.getInstance(context)
            val repository = SessionRepositoryImpl(context, db.sessionDao(), db.stepDao())
            val session = repository.getSessionById(sessionId)
            if (session != null) {
                if (!session.htmlReportPath.isNullOrBlank()) {
                    val file = File(session.htmlReportPath)
                    if (file.exists()) {
                        reportFile = file
                    }
                }

                if (reportFile == null) {
                    val steps = repository.getStepsForSession(sessionId).firstOrNull() ?: emptyList()
                    if (steps.isNotEmpty()) {
                        reportFile = HtmlReportGenerator.generateHtmlReport(context, session, steps)
                    }
                }
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = reportFile?.name ?: "PSR Report Reader", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    reportFile?.let { file ->
                        IconButton(onClick = { ShareUtils.shareReportFile(context, file) }) {
                            Icon(Icons.Default.Share, contentDescription = "Share Report", tint = CyberCyan)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = CyberCyan
                )
            } else if (reportFile != null && reportFile!!.exists()) {
                AndroidWebViewReport(file = reportFile!!)
            } else {
                Text(
                    text = "Unable to load HTML report file.",
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AndroidWebViewReport(file: File) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                }
                loadUrl("file://${file.absolutePath}")
            }
        },
        update = { webView ->
            webView.loadUrl("file://${file.absolutePath}")
        }
    )
}
