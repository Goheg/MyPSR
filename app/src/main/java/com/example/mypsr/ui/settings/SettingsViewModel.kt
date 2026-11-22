package com.example.mypsr.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.mypsr.data.local.UserPreferences
import com.example.mypsr.model.AppTheme
import com.example.mypsr.model.ScreenshotQuality
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = UserPreferences.getInstance(application)

    val qualityState: StateFlow<ScreenshotQuality> = prefs.qualityFlow
    val themeState: StateFlow<AppTheme> = prefs.themeFlow

    fun setQuality(quality: ScreenshotQuality) {
        prefs.setScreenshotQuality(quality)
    }

    fun setTheme(theme: AppTheme) {
        prefs.setAppTheme(theme)
    }
}
