package com.real.earn.paypal.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.real.earn.paypal.App
import com.real.earn.paypal.R

/**
 * Foreground Service — yeh automation loop ko background mein chalaye rakhega.
 * Phase 6 mein iska poora loop logic aayega.
 */
class AutomationService : Service() {

    companion object {
        private const val TAG = "AutomationSvc"
        private const val NOTIF_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AutomationService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AutomationService started")
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "AutomationService destroyed")
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, App.CHANNEL_AUTOMATION)
            .setContentTitle(getString(R.string.foreground_notif_title))
            .setContentText(getString(R.string.foreground_notif_text))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
