package com.real.earn.paypal.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.real.earn.paypal.App
import com.real.earn.paypal.AutomationActivity
import com.real.earn.paypal.R
import com.real.earn.paypal.utils.AirplaneModeController
import com.real.earn.paypal.utils.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AutomationService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loopJob: Job? = null
    private var overlay: OverlayManager? = null

    // Params
    private var serviceName: String = ""
    private var countryName: String = ""
    private var countryCode: String = ""
    private var dialingCode: String = ""
    private var price: Double = 0.0

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL = App.CHANNEL_AUTOMATION

        private const val ACTION_START = "com.real.earn.paypal.START"
        private const val ACTION_PAUSE = "com.real.earn.paypal.PAUSE"
        private const val ACTION_RESUME = "com.real.earn.paypal.RESUME"
        private const val ACTION_STOP = "com.real.earn.paypal.STOP"

        fun start(context: Context, service: String, country: String, countryCode: String, dialingCode: String, price: Double) {
            val i = Intent(context, AutomationService::class.java).apply {
                action = ACTION_START
                putExtra("service", service)
                putExtra("country", country)
                putExtra("countryCode", countryCode)
                putExtra("dialingCode", dialingCode)
                putExtra("price", price)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun pause(context: Context) {
            context.startService(Intent(context, AutomationService::class.java).setAction(ACTION_PAUSE))
        }

        fun resume(context: Context) {
            context.startService(Intent(context, AutomationService::class.java).setAction(ACTION_RESUME))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, AutomationService::class.java).setAction(ACTION_STOP))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("Initializing…"))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                serviceName = intent.getStringExtra("service") ?: ""
                countryName = intent.getStringExtra("country") ?: ""
                countryCode = intent.getStringExtra("countryCode") ?: ""
                dialingCode = intent.getStringExtra("dialingCode") ?: ""
                price = intent.getDoubleExtra("price", 0.0)

                showOverlay()
                startLoop()
            }
            ACTION_PAUSE -> {
                if (AutomationState.getStatus() == AutomationState.Status.RUNNING) {
                    AutomationState.setStatus(AutomationState.Status.PAUSED)
                    AutomationState.log("⏸ Paused")
                    overlay?.setPauseText("▶ Resume")
                    updateNotification("Paused")
                }
            }
            ACTION_RESUME -> {
                if (AutomationState.getStatus() == AutomationState.Status.PAUSED) {
                    AutomationState.setStatus(AutomationState.Status.RUNNING)
                    AutomationState.log("▶ Resumed")
                    overlay?.setPauseText("⏸ Pause")
                    updateNotification("Running")
                }
            }
            ACTION_STOP -> {
                stopAutomation(closeActivity = false)
            }
        }
        return START_STICKY
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return

        AutomationState.reset()
        AutomationState.setStatus(AutomationState.Status.RUNNING)
        AutomationState.log("🚀 Automation started: $serviceName / $countryName")
        updateNotification("Running")

        loopJob = scope.launch {
            try {
                // ⬇️ PHASE 5B MEIN YAHAN ASLI LOOP AAYEGA
                // Filhal skeleton: sirf logs dikhata hai
                val a11y = PayPalAccessibilityService.instance
                if (a11y == null) {
                    AutomationState.log("✘ Accessibility service ON nahi hai")
                    AutomationState.setStatus(AutomationState.Status.STOPPED)
                    return@launch
                }

                AutomationState.log("✔ Accessibility service connected")

                // Demo loop — 5 iterations, 2 sec each
                for (i in 1..5) {
                    if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
                    waitIfPaused()
                    AutomationState.updateStats { it.copy(totalAttempts = it.totalAttempts + 1, currentNumber = "+92 300 000 000$i") }
                    AutomationState.log("• Demo step $i")
                    delay(2000)
                }

                AutomationState.log("✅ Demo loop complete")
                AutomationState.setStatus(AutomationState.Status.STOPPED)
                updateNotification("Stopped")
            } catch (e: Exception) {
                AutomationState.log("✘ Loop error: ${e.message}")
                AutomationState.setStatus(AutomationState.Status.STOPPED)
            }
        }
    }

    private suspend fun waitIfPaused() {
        while (AutomationState.getStatus() == AutomationState.Status.PAUSED) {
            delay(500)
        }
    }

    private fun stopAutomation(closeActivity: Boolean) {
        AutomationState.setStatus(AutomationState.Status.STOPPING)
        AutomationState.log("⏹ Stopping…")
        loopJob?.cancel()
        loopJob = null
        AutomationState.setStatus(AutomationState.Status.STOPPED)
        AutomationState.log("⏹ Stopped")
        updateNotification("Stopped")
        overlay?.hide()
        overlay = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        if (closeActivity) {
            // no-op
        }
    }

    private fun showOverlay() {
        if (overlay != null) return
        overlay = OverlayManager(this).apply {
            onPause = {
                val cur = AutomationState.getStatus()
                if (cur == AutomationState.Status.PAUSED) {
                    resume(applicationContext)
                } else if (cur == AutomationState.Status.RUNNING) {
                    pause(applicationContext)
                }
            }
            onStop = {
                stop(applicationContext)
            }
            onStopAndClose = {
                stop(applicationContext)
                // Activity close handled by Activity itself observing status
            }
            show()
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AutomationActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.foreground_notif_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        loopJob?.cancel()
        scope.cancel()
        overlay?.hide()
        overlay = null
        super.onDestroy()
    }
}
