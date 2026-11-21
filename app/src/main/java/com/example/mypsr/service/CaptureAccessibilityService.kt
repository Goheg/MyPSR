package com.example.mypsr.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.text.InputType
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.mypsr.model.StepEvent

class CaptureAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_VIEW_CLICKED &&
            eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED &&
            eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) {
            return
        }

        val packageName = event.packageName?.toString() ?: ""
        // Skip self-events from Mobile PSR app
        if (packageName == applicationContext.packageName) return

        val appName = getAppName(packageName)
        val nodeInfo = event.source

        val bounds = Rect()
        var isSensitive = false
        var typedText: String? = null

        if (nodeInfo != null) {
            nodeInfo.getBoundsInScreen(bounds)
            isSensitive = isNodeSensitive(nodeInfo)
            if (!isSensitive) {
                // Also check parent nodes for password flags
                var parent = nodeInfo.parent
                var depth = 0
                while (parent != null && depth < 3) {
                    if (isNodeSensitive(parent)) {
                        isSensitive = true
                        break
                    }
                    parent = parent.parent
                    depth++
                }
            }

            if (!isSensitive && eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
                typedText = event.text.joinToString("")
            }
        }

        val description = buildStepDescription(eventType, event, nodeInfo, appName, isSensitive)

        val stepEvent = StepEvent(
            eventType = eventType,
            description = description,
            packageName = packageName,
            appName = appName,
            timestamp = System.currentTimeMillis(),
            bounds = if (bounds.isEmpty) null else bounds,
            isSensitive = isSensitive,
            typedText = typedText
        )

        Log.d(TAG, "Captured StepEvent: $description (Sensitive: $isSensitive)")
        SessionManager.onStepEventCaptured(stepEvent)
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService Interrupted")
    }

    private fun isNodeSensitive(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return true

        val inputType = node.inputType
        val variation = inputType and InputType.TYPE_MASK_VARIATION
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) {
            return true
        }

        val text = node.text?.toString()?.lowercase() ?: ""
        val hint = node.hintText?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        val sensitiveKeywords = listOf("password", "pin", "cvv", "credit card", "passcode")
        return sensitiveKeywords.any { text.contains(it) || hint.contains(it) || contentDesc.contains(it) }
    }

    private fun buildStepDescription(
        eventType: Int,
        event: AccessibilityEvent,
        node: AccessibilityNodeInfo?,
        appName: String,
        isSensitive: Boolean
    ): String {
        if (isSensitive) {
            return "Interacted with sensitive/password field in $appName"
        }

        val elementLabel = getElementText(event, node)

        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                if (elementLabel.isNotBlank()) {
                    "Clicked '$elementLabel' in $appName"
                } else {
                    "Clicked element in $appName"
                }
            }
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                if (elementLabel.isNotBlank()) {
                    "Entered text into '$elementLabel' in $appName"
                } else {
                    "Entered text in $appName"
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                "Scrolled in $appName"
            }
            else -> "Interacted with $appName"
        }
    }

    private fun getElementText(event: AccessibilityEvent, node: AccessibilityNodeInfo?): String {
        node?.text?.toString()?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        node?.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        node?.hintText?.toString()?.takeIf { it.isNotBlank() }?.let { return it.trim() }

        event.text.joinToString(" ").takeIf { it.isNotBlank() }?.let { return it.trim() }
        return ""
    }

    private fun getAppName(packageName: String): String {
        if (packageName.isBlank()) return "App"
        return try {
            val pm = packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }
    }

    companion object {
        private const val TAG = "CaptureAccessibility"
    }
}
