package com.Mitchell.phonestopwatch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class StopwatchService : Service() {

    companion object {
        private const val TAG = "PhoneStopwatch"
        private const val CHANNEL_ID = "stopwatch_channel"
        private const val NOTIFICATION_ID = 1
        private const val TICK_MS = 5_000L

        // Xiaomi 13: punch-hole camera at top-center, ~12dp diameter
        // Overlay sits to the right of camera hole
        private const val CAMERA_RIGHT_OFFSET_DP = 22f  // dp right of screen center
        private const val TEXT_SIZE_SP = 12f
        private const val Y_OFFSET_DP = -330f  // shift upward
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: TextView? = null
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
        createOverlayView()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        handler.removeCallbacks(tickRunnable)
        removeOverlayView()
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

    private fun createOverlayView() {
        overlayView = TextView(this).apply {
            text = "0:00"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP)
            setShadowLayer(3f, 0f, 1f, Color.BLACK)
        }
    }

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

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth / 2 + offsetPx
            val yOffsetPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, Y_OFFSET_DP, metrics
            ).toInt()
            y = statusBarHeight / 2 - textSizePx / 2 + yOffsetPx
        }
    }

    private fun showOverlay() {
        if (isShowing || overlayView == null) return
        try {
            windowManager.addView(overlayView, buildOverlayParams())
            isShowing = true
            Log.d(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "showOverlay failed", e)
        }
    }

    private fun hideOverlay() {
        if (!isShowing || overlayView == null) return
        try {
            windowManager.removeView(overlayView)
            isShowing = false
            Log.d(TAG, "Overlay hidden")
        } catch (e: Exception) {
            Log.e(TAG, "hideOverlay failed", e)
        }
    }

    private fun removeOverlayView() {
        hideOverlay()
        overlayView = null
    }

    private fun updateDisplay() {
        if (unlockTime == 0L) return
        val elapsedSec = (System.currentTimeMillis() - unlockTime) / 1_000L
        val minutes = elapsedSec / 60
        val seconds = elapsedSec % 60
        val text = "%d:%02d".format(minutes, seconds)
        overlayView?.text = text
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
