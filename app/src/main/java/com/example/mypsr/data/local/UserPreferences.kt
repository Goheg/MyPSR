package com.example.mypsr.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.mypsr.model.AppTheme
import com.example.mypsr.model.ScreenshotQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferences private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _qualityFlow = MutableStateFlow(getScreenshotQuality())
    val qualityFlow: StateFlow<ScreenshotQuality> = _qualityFlow.asStateFlow()

    private val _themeFlow = MutableStateFlow(getAppTheme())
    val themeFlow: StateFlow<AppTheme> = _themeFlow.asStateFlow()

    fun getScreenshotQuality(): ScreenshotQuality {
        val valInt = prefs.getInt(KEY_QUALITY, ScreenshotQuality.HIGH.qualityValue)
        return ScreenshotQuality.fromQualityValue(valInt)
    }

    fun setScreenshotQuality(quality: ScreenshotQuality) {
        prefs.edit().putInt(KEY_QUALITY, quality.qualityValue).apply()
        _qualityFlow.value = quality
    }

    fun getAppTheme(): AppTheme {
        val name = prefs.getString(KEY_THEME, AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
        return AppTheme.fromName(name)
    }

    fun setAppTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_THEME, theme.name).apply()
        _themeFlow.value = theme
    }

    companion object {
        private const val PREFS_NAME = "mypsr_user_preferences"
        private const val KEY_QUALITY = "key_screenshot_quality"
        private const val KEY_THEME = "key_app_theme"

        @Volatile
        private var INSTANCE: UserPreferences? = null

        fun getInstance(context: Context): UserPreferences {
            return INSTANCE ?: synchronized(this) {
                val instance = UserPreferences(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
