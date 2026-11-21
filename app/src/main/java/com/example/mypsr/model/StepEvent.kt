package com.example.mypsr.model

import android.graphics.Rect

/**
 * Raw UI interaction intercepted by CaptureAccessibilityService.
 */
data class StepEvent(
    val eventType: Int,
    val description: String,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val bounds: Rect? = null,
    val isSensitive: Boolean = false,
    val typedText: String? = null
)
