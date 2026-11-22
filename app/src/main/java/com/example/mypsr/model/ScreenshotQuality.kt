package com.example.mypsr.model

enum class ScreenshotQuality(val qualityValue: Int, val label: String) {
    HIGH(80, "High (80%)"),
    MEDIUM(50, "Medium (50%)"),
    LOW(25, "Low (25%)");

    companion object {
        fun fromQualityValue(value: Int): ScreenshotQuality {
            return entries.find { it.qualityValue == value } ?: HIGH
        }
    }
}
