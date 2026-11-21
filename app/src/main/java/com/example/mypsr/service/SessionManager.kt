package com.example.mypsr.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.example.mypsr.data.local.AppDatabase
import com.example.mypsr.data.repository.SessionRepository
import com.example.mypsr.data.repository.SessionRepositoryImpl
import com.example.mypsr.model.RecordingState
import com.example.mypsr.model.StepEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SessionManager {

    private const val TAG = "SessionManager"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _recordingState = MutableStateFlow(RecordingState.IDLE)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _currentSessionId = MutableStateFlow<Long>(0L)
    val currentSessionId: StateFlow<Long> = _currentSessionId.asStateFlow()

    private val _stepCount = MutableStateFlow(0)
    val stepCount: StateFlow<Int> = _stepCount.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var repository: SessionRepository? = null

    private fun getRepository(context: Context): SessionRepository {
        return repository ?: run {
            val db = AppDatabase.getInstance(context.applicationContext)
            val repo = SessionRepositoryImpl(context.applicationContext, db.sessionDao(), db.stepDao())
            repository = repo
            repo
        }
    }

    fun startSession(context: Context, resultCode: Int, data: Intent) {
        if (_recordingState.value == RecordingState.RECORDING) return

        val repo = getRepository(context)
        scope.launch {
            try {
                val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                val sessionTitle = "Recording $timestampStr"
                val sessionId = repo.createSession(sessionTitle)

                _currentSessionId.value = sessionId
                _stepCount.value = 0
                _recordingState.value = RecordingState.RECORDING
                _errorMessage.value = null

                // Start CaptureService
                val captureIntent = Intent(context, CaptureService::class.java).apply {
                    action = CaptureService.ACTION_START_RECORDING
                    putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(CaptureService.EXTRA_RESULT_DATA, data)
                    putExtra(CaptureService.EXTRA_SESSION_ID, sessionId)
                }
                context.startForegroundService(captureIntent)

                // Start OverlayService if permission is granted
                if (Settings.canDrawOverlays(context)) {
                    val overlayIntent = Intent(context, OverlayService::class.java)
                    context.startService(overlayIntent)
                }

                Log.d(TAG, "Recording session started with ID: $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start recording session: ${e.message}")
                _recordingState.value = RecordingState.IDLE
                _errorMessage.value = "Failed to start recording: ${e.message}"
            }
        }
    }

    fun pauseSession() {
        if (_recordingState.value == RecordingState.RECORDING) {
            _recordingState.value = RecordingState.PAUSED
            Log.d(TAG, "Recording session paused")
        }
    }

    fun resumeSession() {
        if (_recordingState.value == RecordingState.PAUSED) {
            _recordingState.value = RecordingState.RECORDING
            Log.d(TAG, "Recording session resumed")
        }
    }

    fun stopSession(context: Context) {
        if (_recordingState.value == RecordingState.IDLE || _recordingState.value == RecordingState.STOPPED) return

        _recordingState.value = RecordingState.STOPPED
        val sessionId = _currentSessionId.value

        // Stop services
        val captureIntent = Intent(context, CaptureService::class.java).apply {
            action = CaptureService.ACTION_STOP_RECORDING
        }
        context.startService(captureIntent)

        val overlayIntent = Intent(context, OverlayService::class.java)
        context.stopService(overlayIntent)

        Log.d(TAG, "Recording session $sessionId stopped. Steps captured: ${_stepCount.value}")

        _recordingState.value = RecordingState.IDLE
    }

    fun onStepEventCaptured(event: StepEvent) {
        if (_recordingState.value != RecordingState.RECORDING) return

        val captureService = CaptureService.getInstance()
        if (captureService != null) {
            captureService.captureStep(event)
            _stepCount.value += 1
        } else {
            Log.w(TAG, "CaptureService instance not available when step event occurred")
        }
    }

    fun onMediaProjectionRevoked() {
        Log.w(TAG, "MediaProjection revoked mid-session")
        _recordingState.value = RecordingState.PAUSED
        _errorMessage.value = "Screen capture permission was revoked or stopped."
    }

    fun onStorageThresholdReached() {
        Log.w(TAG, "Low storage threshold reached")
        _recordingState.value = RecordingState.PAUSED
        _errorMessage.value = "Storage space low (< 50MB). Session paused and saved."
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val prefString = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        return prefString.contains(context.packageName)
    }

    fun isOverlayPermissionGranted(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }
}
