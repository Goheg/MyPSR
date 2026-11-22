package com.example.mypsr.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.IntentCompat
import com.example.mypsr.R
import com.example.mypsr.data.local.AppDatabase
import com.example.mypsr.data.repository.SessionRepository
import com.example.mypsr.data.repository.SessionRepositoryImpl
import com.example.mypsr.model.CapturedStep
import com.example.mypsr.model.RedactionBox
import com.example.mypsr.model.StepEvent
import com.example.mypsr.util.ImageUtils
import com.example.mypsr.util.StorageUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CaptureService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = 320

    private var currentSessionId: Long = 0L
    private var currentStepNumber = 1

    private var lastCaptureTime = 0L
    private val debounceMs = 400L

    private lateinit var repository: SessionRepository

    override fun onCreate() {
        super.onCreate()
        instance = this
        val db = AppDatabase.getInstance(applicationContext)
        repository = SessionRepositoryImpl(applicationContext, db.sessionDao(), db.stepDao())
        initScreenMetrics()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_START_RECORDING -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = IntentCompat.getParcelableExtra(intent, EXTRA_RESULT_DATA, Intent::class.java)
                val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, 0L)

                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    startForegroundServiceWithNotification()
                    initMediaProjection(resultCode, resultData)
                    currentSessionId = sessionId
                    currentStepNumber = 1
                } else {
                    Log.e(TAG, "MediaProjection consent missing or cancelled")
                    stopSelf()
                }
            }

            ACTION_STOP_RECORDING -> {
                stopRecording()
            }
        }

        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val notification = buildNotification("Mobile PSR Recording Active")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            } else {
                0
            }
            startForeground(NOTIFICATION_ID, notification, fgsType)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initMediaProjection(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "MediaProjection stopped by system or user")
                SessionManager.onMediaProjectionRevoked()
                stopRecording()
            }
        }, Handler(Looper.getMainLooper()))

        setupVirtualDisplay()
    }

    private fun setupVirtualDisplay() {
        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "PSR_VirtualDisplay",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )
    }

    fun captureStep(stepEvent: StepEvent) {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < debounceMs) {
            Log.d(TAG, "Capture throttled: too rapid")
            return
        }
        lastCaptureTime = now

        if (!StorageUtils.isStorageAvailable(applicationContext)) {
            Log.e(TAG, "Storage threshold reached (<50MB available). Auto-stopping session.")
            SessionManager.onStorageThresholdReached()
            stopRecording()
            return
        }

        serviceScope.launch(Dispatchers.Default) {
            val screenshotBytes = acquireScreenshot(stepEvent.bounds, stepEvent.isSensitive)
            val screenshotPath = if (screenshotBytes != null) {
                repository.saveScreenshot(currentSessionId, currentStepNumber, screenshotBytes)
            } else null

            val autoRedactions = if (stepEvent.isSensitive && stepEvent.bounds != null) {
                listOf(
                    RedactionBox(
                        left = stepEvent.bounds.left.toFloat() / screenWidth,
                        top = stepEvent.bounds.top.toFloat() / screenHeight,
                        right = stepEvent.bounds.right.toFloat() / screenWidth,
                        bottom = stepEvent.bounds.bottom.toFloat() / screenHeight,
                        isAutoMask = true
                    )
                )
            } else emptyList()

            val step = CapturedStep(
                sessionId = currentSessionId,
                stepNumber = currentStepNumber,
                actionDescription = stepEvent.description,
                packageName = stepEvent.packageName,
                appName = stepEvent.appName,
                timestamp = stepEvent.timestamp,
                screenshotPath = screenshotPath,
                clickX = stepEvent.bounds?.centerX(),
                clickY = stepEvent.bounds?.centerY(),
                targetBoundsJson = stepEvent.bounds?.let { "${it.left},${it.top},${it.right},${it.bottom}" },
                isSensitive = stepEvent.isSensitive,
                redactionBoxes = autoRedactions
            )

            repository.insertStep(currentSessionId, step)
            currentStepNumber++
        }
    }

    private suspend fun acquireScreenshot(
        targetBounds: Rect?,
        isSensitive: Boolean
    ): ByteArray? {
        return withContext(Dispatchers.Default) {
            val reader = imageReader ?: return@withContext null
            var image: Image? = null
            var bitmap: Bitmap? = null
            try {
                Thread.sleep(100)
                image = reader.acquireLatestImage()
                if (image != null) {
                    bitmap = ImageUtils.imageToBitmap(image)
                    if (bitmap != null) {
                        var processedBitmap = bitmap
                        if (isSensitive && targetBounds != null && !targetBounds.isEmpty) {
                            processedBitmap = ImageUtils.applyBoundsRedaction(bitmap, targetBounds)
                        }
                        val quality = com.example.mypsr.data.local.UserPreferences.getInstance(applicationContext).getScreenshotQuality().qualityValue
                        ImageUtils.compressBitmapToJpeg(processedBitmap, quality = quality)
                    } else null
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to capture screenshot: ${e.message}")
                null
            } finally {
                image?.close()
                bitmap?.recycle()
            }
        }
    }

    private fun stopRecording() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up projection resources: ${e.message}")
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun initScreenMetrics() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            screenDensity = resources.displayMetrics.densityDpi
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
        }
    }

    private fun buildNotification(title: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Capturing UI steps & screenshots for report")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PSR Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification displayed while Mobile PSR is recording user steps."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        stopRecording()
    }

    companion object {
        const val TAG = "CaptureService"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "psr_recording_channel"

        const val ACTION_START_RECORDING = "com.example.mypsr.ACTION_START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.mypsr.ACTION_STOP_RECORDING"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_SESSION_ID = "EXTRA_SESSION_ID"

        private var instance: CaptureService? = null

        fun getInstance(): CaptureService? = instance
    }
}
