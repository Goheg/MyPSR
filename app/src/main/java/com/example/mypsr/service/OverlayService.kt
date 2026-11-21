package com.example.mypsr.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.mypsr.model.RecordingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class OverlayService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var isExpanded = false
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var actionButtonContainer: LinearLayout
    private lateinit var pauseResumeButton: TextView
    private lateinit var stopButton: TextView

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createOverlayView()
        observeRecordingState()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createOverlayView() {
        val rootLayout = FrameLayout(this)

        val cardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 18, 28, 18)

            // Sleek dark futuristic background with rounded corners and cyan border
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#121824"))
                cornerRadius = 48f
                setStroke(3, Color.parseColor("#00E5FF"))
            }
        }

        // Status Indicator Dot (Pulsing Red when recording, Amber when paused)
        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(24, 24).apply {
                marginEnd = 16
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF5252"))
            }
        }
        cardLayout.addView(statusDot)

        // Status Text & Step Counter
        statusText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 16
            }
            text = "REC (0)"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        cardLayout.addView(statusText)

        // Action Buttons Container (Pause/Resume, Stop)
        actionButtonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.VISIBLE
        }

        // Pause/Resume Button
        pauseResumeButton = TextView(this).apply {
            text = "Pause"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 12f
            setPadding(20, 10, 20, 10)
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#1E2638"))
            }
            setOnClickListener {
                if (SessionManager.recordingState.value == RecordingState.RECORDING) {
                    SessionManager.pauseSession()
                } else if (SessionManager.recordingState.value == RecordingState.PAUSED) {
                    SessionManager.resumeSession()
                }
            }
        }
        actionButtonContainer.addView(pauseResumeButton)

        // Stop Button
        stopButton = TextView(this).apply {
            text = "Stop"
            setTextColor(Color.parseColor("#FF5252"))
            textSize = 12f
            setPadding(20, 10, 20, 10)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = 12
            }
            background = GradientDrawable().apply {
                cornerRadius = 24f
                setColor(Color.parseColor("#1E2638"))
            }
            setOnClickListener {
                SessionManager.stopSession(applicationContext)
            }
        }
        actionButtonContainer.addView(stopButton)

        cardLayout.addView(actionButtonContainer)
        rootLayout.addView(cardLayout)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 60
            y = 200
        }

        // Touch Listener for Draggable Overlay Widget
        rootLayout.setOnTouchListener { _, event ->
            val params = layoutParams ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(overlayView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val diffX = abs(event.rawX - initialTouchX)
                    val diffY = abs(event.rawY - initialTouchY)
                    // Toggle expand/collapse on tap if drag motion was minimal
                    if (diffX < 10 && diffY < 10) {
                        toggleExpandedState()
                    }
                    true
                }
                else -> false
            }
        }

        overlayView = rootLayout
        windowManager?.addView(overlayView, layoutParams)
    }

    private fun toggleExpandedState() {
        isExpanded = !isExpanded
        actionButtonContainer.visibility = if (isExpanded) View.VISIBLE else View.GONE
    }

    private fun observeRecordingState() {
        serviceScope.launch {
            SessionManager.recordingState.collectLatest { state ->
                when (state) {
                    RecordingState.RECORDING -> {
                        (statusDot.background as? GradientDrawable)?.setColor(Color.parseColor("#FF5252"))
                        pauseResumeButton.text = "Pause"
                        pauseResumeButton.setTextColor(Color.parseColor("#00E5FF"))
                    }
                    RecordingState.PAUSED -> {
                        (statusDot.background as? GradientDrawable)?.setColor(Color.parseColor("#FFB300"))
                        pauseResumeButton.text = "Resume"
                        pauseResumeButton.setTextColor(Color.parseColor("#FFB300"))
                    }
                    RecordingState.STOPPED, RecordingState.IDLE -> {
                        stopSelf()
                    }
                }
            }
        }

        serviceScope.launch {
            SessionManager.stepCount.collectLatest { count ->
                val stateText = if (SessionManager.recordingState.value == RecordingState.PAUSED) "PAUSED" else "REC"
                statusText.text = "$stateText ($count)"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        overlayView?.let {
            windowManager?.removeView(it)
        }
        overlayView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
