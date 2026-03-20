package com.Mitchell.phonestopwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.content.res.Configuration
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class StopwatchService : Service() {

    companion object {
        private const val TAG = "PhoneStopwatch"
        private const val CHANNEL_ID = "stopwatch_channel"
        private const val NOTIFICATION_ID = 1
        private const val TICK_MS = 5_000L

        private const val CAMERA_RIGHT_OFFSET_DP = 22f
        private const val TEXT_SIZE_SP = 14f
        private const val Y_OFFSET_DP = 0f  // unused, y is computed directly
    }

    // Custom View: white fill + black stroke outline
    private inner class OutlinedTextView(ctx: Context) : View(ctx) {
        var displayText: String = "0:00"
            set(value) {
                field = value
                invalidate()
            }

        private val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP, ctx.resources.displayMetrics
        )

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = textSizePx
            typeface = Typeface.DEFAULT_BOLD
            style = Paint.Style.STROKE
            strokeWidth = textSizePx * 0.18f
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textSizePx
            typeface = Typeface.DEFAULT_BOLD
            style = Paint.Style.FILL
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val w = (strokePaint.measureText(displayText) + strokePaint.strokeWidth * 2).toInt()
            val h = (-fillPaint.ascent() + fillPaint.descent() + strokePaint.strokeWidth * 2).toInt()
            setMeasuredDimension(w, h)
        }

        override fun onDraw(canvas: Canvas) {
            val x = strokePaint.strokeWidth
            val y = -fillPaint.ascent() + strokePaint.strokeWidth
            canvas.drawText(displayText, x, y, strokePaint)
            canvas.drawText(displayText, x, y, fillPaint)
        }
    }

    private lateinit var windowManager: WindowManager
    private var timerView: OutlinedTextView? = null
    private val handler = Handler(Looper.getMainLooper())

    private var unlockTime = 0L
    private var isShowing = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_USER_PRESENT -> onUnlock()
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
            }
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            updateDisplay()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerScreenReceiver()
        timerView = OutlinedTextView(this)
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isShowing) {
            windowManager.updateViewLayout(timerView, buildOverlayParams())
            Log.d(TAG, "Config changed, overlay repositioned")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        handler.removeCallbacks(tickRunnable)
        if (isShowing) hideOverlay()
        timerView = null
        Log.d(TAG, "Service destroyed")
    }

    // ── Screen events ────────────────────────────────────────────────────────

    private fun onUnlock() {
        Log.d(TAG, "ACTION_USER_PRESENT")
        unlockTime = System.currentTimeMillis()
        showOverlay()
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, TICK_MS)
        updateDisplay()
    }

    private fun onScreenOff() {
        Log.d(TAG, "ACTION_SCREEN_OFF")
        handler.removeCallbacks(tickRunnable)
        hideOverlay()
        unlockTime = 0L
    }

    // ── Overlay ──────────────────────────────────────────────────────────────

    private fun buildOverlayParams(): WindowManager.LayoutParams {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val statusBarHeight = getStatusBarHeight()

        val offsetPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, CAMERA_RIGHT_OFFSET_DP, metrics
        ).toInt()
        val textSizePx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP, metrics
        ).toInt()

        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val finalX: Int
        val finalY: Int
        if (isLandscape) {
            // fullscreen video: status bar hidden, position top-right corner within screen
            finalX = screenWidth - offsetPx * 3
            finalY = textSizePx / 2
        } else {
            // portrait: enter status bar area with negative Y
            finalX = screenWidth / 2 + offsetPx
            finalY = -(statusBarHeight - textSizePx) / 2 - 60
        }
        Log.d(TAG, "Overlay params: x=$finalX y=$finalY statusBar=$statusBarHeight screenWidth=$screenWidth density=${metrics.density}")

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = finalX
            y = finalY
        }
    }

    private fun showOverlay() {
        if (isShowing || timerView == null) return
        try {
            windowManager.addView(timerView, buildOverlayParams())
            isShowing = true
            Log.d(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "showOverlay failed", e)
        }
    }

    private fun hideOverlay() {
        if (!isShowing || timerView == null) return
        try {
            windowManager.removeView(timerView)
            isShowing = false
            Log.d(TAG, "Overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "hideOverlay failed", e)
        }
    }

    private fun updateDisplay() {
        if (unlockTime == 0L) return
        val elapsedSec = (System.currentTimeMillis() - unlockTime) / 1_000L
        val minutes = elapsedSec / 60
        val seconds = elapsedSec % 60
        val text = "%d:%02d".format(minutes, seconds)
        timerView?.displayText = text
        Log.d(TAG, "Time: $text")
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun getStatusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 72
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Stopwatch",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            setShowBadge(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneStopwatch")
            .setContentText("运行中")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()
    }
}
