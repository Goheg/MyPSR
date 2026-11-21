package com.example.mypsr.model

/**
 * Domain model representing a fully captured step with description and screenshot data.
 */
data class CapturedStep(
    val id: Long = 0,
    val sessionId: Long = 0,
    val stepNumber: Int,
    val actionDescription: String,
    val packageName: String,
    val appName: String,
    val timestamp: Long,
    val screenshotPath: String? = null,
    val clickX: Int? = null,
    val clickY: Int? = null,
    val targetBoundsJson: String? = null,
    val isSensitive: Boolean = false,
    val redactionBoxes: List<RedactionBox> = emptyList()
)
