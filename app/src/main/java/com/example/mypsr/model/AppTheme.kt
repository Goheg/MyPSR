package com.example.mypsr.model

enum class AppTheme(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode");

    companion object {
        fun fromName(name: String): AppTheme {
            return try {
                valueOf(name)
            } catch (_: Exception) {
                SYSTEM
            }
        }
    }
}
