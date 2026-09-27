package com.example

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.example.data.PreferencesManager
import java.util.concurrent.Executors

class FloatingCameraOverlayService : Service(), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        startAsForegroundService()
        if (Settings.canDrawOverlays(this)) {
            initFloatingOverlay()
        } else {
            Log.e(TAG, "Cannot draw overlays; permission not granted")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return START_STICKY
    }

    private fun startAsForegroundService() {
        val channelId = "color_scroll_overlay_channel"
        val channelName = "Color Scroll Overlay"

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs hands-free camera color detection overlay"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Color Scroll Active")
            .setContentText("Hands-free scrolling listening via front camera")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                )
            } else {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                )
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initFloatingOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            dpToPx(130),
            dpToPx(170),
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 160
        }

        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(0xEE1A1C29.toInt())
            setPadding(8, 8, 8, 8)
        }

        val previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(110)
            )
        }
        rootLayout.addView(previewView)

        // Status HUD text
        val statusText = TextView(this).apply {
            text = "Detecting..."
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 10f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(24)
            ).apply {
                gravity = Gravity.BOTTOM
                setMargins(4, 0, 4, 28)
            }
        }
        rootLayout.addView(statusText)

        // Close overlay button
        val closeButton = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(0x88000000.toInt())
            layoutParams = FrameLayout.LayoutParams(dpToPx(28), dpToPx(28)).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            setOnClickListener {
                stopSelf()
            }
        }
        rootLayout.addView(closeButton)

        // Mini status bar for threshold
        val progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(6)
            ).apply {
                gravity = Gravity.BOTTOM
            }
        }
        rootLayout.addView(progressBar)

        // Drag to reposition
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        rootLayout.setOnTouchListener { _, event ->
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
                    windowManager?.updateViewLayout(rootLayout, params)
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(rootLayout, params)
            floatingView = rootLayout
            startCamera(previewView, statusText, progressBar)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating window view", e)
        }
    }

    private fun startCamera(
        previewView: PreviewView,
        statusText: TextView,
        progressBar: ProgressBar
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        val prefs = PreferencesManager.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val colorAnalyzer = ColorAnalyzer(
                    settingsProvider = { prefs.getSettings() },
                    onResult = { result ->
                        val text = when {
                            result.isCooldownActive -> "Cooldown ${(result.remainingCooldownMs / 1000f).let { String.format("%.1fs", it) }}"
                            result.upPercentage >= result.thresholdPercent -> "⬆️ UP (${String.format("%.0f%%", result.upPercentage)})"
                            result.downPercentage >= result.thresholdPercent -> "⬇️ DOWN (${String.format("%.0f%%", result.downPercentage)})"
                            else -> "Up: ${String.format("%.0f%%", result.upPercentage)} | Dn: ${String.format("%.0f%%", result.downPercentage)}"
                        }
                        val maxPercent = maxOf(result.upPercentage, result.downPercentage)
                        statusText.post {
                            statusText.text = text
                            progressBar.progress = (maxPercent * 5).toInt().coerceIn(0, 100)
                        }
                    }
                )

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, colorAnalyzer)
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind camera in overlay service", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
        floatingView?.let {
            windowManager?.removeView(it)
            floatingView = null
        }
        PreferencesManager.getInstance(this).updateFloatingOverlay(false)
        _isOverlayRunningFlow.value = false
        super.onDestroy()
        Log.i(TAG, "FloatingCameraOverlayService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    companion object {
        private const val TAG = "FloatingCameraOverlay"
        private const val NOTIFICATION_ID = 1001

        private val _isOverlayRunningFlow = kotlinx.coroutines.flow.MutableStateFlow(false)
        val isOverlayRunningFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = _isOverlayRunningFlow

        fun start(context: Context) {
            val intent = Intent(context, FloatingCameraOverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            _isOverlayRunningFlow.value = true
        }

        fun stop(context: Context) {
            val intent = Intent(context, FloatingCameraOverlayService::class.java)
            context.stopService(intent)
            _isOverlayRunningFlow.value = false
        }
    }
}
