package com.example.mypsr.model

/**
 * Normalized or absolute bounding coordinates for a manual/auto redaction box on a screenshot.
 * Coordinates are represented as relative percentages (0.0 to 1.0) of bitmap dimensions.
 */
data class RedactionBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val isAutoMask: Boolean = false
)
