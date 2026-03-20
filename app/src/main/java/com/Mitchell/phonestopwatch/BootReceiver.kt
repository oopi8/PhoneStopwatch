package com.Mitchell.phonestopwatch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("PhoneStopwatch", "Boot completed, starting service")
            context.startForegroundService(Intent(context, StopwatchService::class.java))
        }
    }
}
