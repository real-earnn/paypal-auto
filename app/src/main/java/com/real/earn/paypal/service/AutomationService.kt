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
import com.real.earn.paypal.api.ApiClient
import com.real.earn.paypal.api.GetNumberBody
import com.real.earn.paypal.api.ReleaseNumberBody
import com.real.earn.paypal.auth.AuthManager
import com.real.earn.paypal.utils.AppPrefs
import com.real.earn.paypal.utils.OverlayManager
import com.real.earn.paypal.utils.PayPalAutomation
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

    private var serviceName: String = ""
    private var countryName: String = ""
    private var countryCodeIso: String = ""
    private var dialingCode: String = ""

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL = App.CHANNEL_AUTOMATION
        private const val ACTION_START = "com.real.earn.paypal.START"
        private const val ACTION_PAUSE = "com.real.earn.paypal.PAUSE"
        private const val ACTION_RESUME = "com.real.earn.paypal.RESUME"
        private const val ACTION_STOP = "com.real.earn.paypal.STOP"
        private const val ACTION_STOP_AND_CLOSE = "com.real.earn.paypal.STOP_AND_CLOSE"

        fun start(context: Context, service: String, country: String, countryCode: String, dialingCode: String, price: Double) {
            val i = Intent(context, AutomationService::class.java).apply {
                action = ACTION_START
                putExtra("service", service)
                putExtra("country", country)
                putExtra("countryCode", countryCode)
                putExtra("dialingCode", dialingCode)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else context.startService(i)
        }

        fun pause(context: Context) = context.startService(
            Intent(context, AutomationService::class.java).setAction(ACTION_PAUSE))
        fun resume(context: Context) = context.startService(
            Intent(context, AutomationService::class.java).setAction(ACTION_RESUME))
        fun stop(context: Context) = context.startService(
            Intent(context, AutomationService::class.java).setAction(ACTION_STOP))
        fun stopAndClose(context: Context) = context.startService(
            Intent(context, AutomationService::class.java).setAction(ACTION_STOP_AND_CLOSE))
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
                countryCodeIso = intent.getStringExtra("countryCode") ?: ""
                dialingCode = intent.getStringExtra("dialingCode") ?: ""
                showOverlay()
                startLoop()
            }
            ACTION_PAUSE -> doPause()
            ACTION_RESUME -> doResume()
            ACTION_STOP -> stopAutomation(closeActivity = false)
            ACTION_STOP_AND_CLOSE -> stopAutomation(closeActivity = true)
        }
        return START_STICKY
    }

    private fun doPause() {
        if (AutomationState.getStatus() == AutomationState.Status.RUNNING) {
            AutomationState.setStatus(AutomationState.Status.PAUSED)
            AutomationState.log("⏸ Paused")
            overlay?.updatePausedVisuals(true)
            updateNotification("Paused")
        }
    }

    private fun doResume() {
        if (AutomationState.getStatus() == AutomationState.Status.PAUSED) {
            AutomationState.setStatus(AutomationState.Status.RUNNING)
            AutomationState.log("▶ Resumed")
            overlay?.updatePausedVisuals(false)
            updateNotification("Running")
        }
    }

    private fun startLoop() {
        if (loopJob?.isActive == true) return

        AutomationState.reset()
        AutomationState.setStatus(AutomationState.Status.RUNNING)
        AutomationState.log("🚀 Automation: $serviceName / $countryName")
        updateNotification("Running")

        loopJob = scope.launch {
            try {
                val a11y = PayPalAccessibilityService.instance
                if (a11y == null) {
                    AutomationState.log("✘ Accessibility OFF")
                    AutomationState.setStatus(AutomationState.Status.STOPPED)
                    return@launch
                }
                AutomationState.log("✔ Accessibility connected")

                val auth = AuthManager(applicationContext)
                val prefs = AppPrefs(applicationContext)
                val autoFlight = prefs.autoFlightMode
                AutomationState.log(if (autoFlight) "✈ Auto Flight: ON" else "✈ Auto Flight: OFF")

                while (AutomationState.getStatus() != AutomationState.Status.STOPPING) {
                    while (AutomationState.getStatus() == AutomationState.Status.PAUSED) delay(300)
                    if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break

                    AutomationState.log("📞 Fetching 5 numbers...")
                    val nums = fetchNumbers(auth)
                    if (nums.isEmpty()) {
                        AutomationState.log("⚠ No numbers — wait 15s")
                        delay(15000); continue
                    }
                    AutomationState.log("✔ ${nums.size} numbers")

                    val (s, f) = PayPalAutomation.runBatch(
                        service = a11y,
                        numbers = nums,
                        countryName = countryName,
                        countryIso = countryCodeIso,
                        dialingCode = dialingCode,
                        autoFlightMode = autoFlight,
                        onNumberStart = { num ->
                            AutomationState.updateStats {
                                it.copy(
                                    totalAttempts = it.totalAttempts + 1,
                                    currentService = serviceName,
                                    currentCountry = countryName,
                                    currentNumber = num
                                )
                            }
                        },
                        onNumberSuccess = { _ ->
                            AutomationState.updateStats { it.copy(successCount = it.successCount + 1) }
                        },
                        onNumberFail = { num ->
                            AutomationState.updateStats { it.copy(failCount = it.failCount + 1) }
                            releaseNumbers(auth, listOf(num), "otp_failed")
                        }
                    )

                    AutomationState.log("📊 Batch: Success=$s Fail=$f")
                    if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
                    delay(800)
                }

                AutomationState.log("⏹ Loop exit")
                AutomationState.setStatus(AutomationState.Status.STOPPED)
                updateNotification("Stopped")
            } catch (e: Exception) {
                AutomationState.log("✘ Loop error: ${e.message}")
                AutomationState.setStatus(AutomationState.Status.STOPPED)
                updateNotification("Error")
            }
        }
    }

    private suspend fun fetchNumbers(auth: AuthManager): List<String> {
        return try {
            val res = ApiClient.service(auth).getNumbers(
                GetNumberBody(service = serviceName, country = countryName, count = 5)
            )
            if (res.isSuccessful && res.body()?.success == true) {
                res.body()?.numbers ?: emptyList()
            } else {
                AutomationState.log("✘ getNumber HTTP ${res.code()}")
                emptyList()
            }
        } catch (e: Exception) {
            AutomationState.log("✘ getNumber: ${e.message}")
            emptyList()
        }
    }

    private suspend fun releaseNumbers(auth: AuthManager, nums: List<String>, reason: String) {
        try {
            ApiClient.service(auth).releaseNumbers(ReleaseNumberBody(numbers = nums, reason = reason))
        } catch (_: Exception) {}
    }

    private fun stopAutomation(closeActivity: Boolean) {
        AutomationState.setStatus(AutomationState.Status.STOPPING)
        AutomationState.log("⏹ Stopping…")
        loopJob?.cancel(); loopJob = null
        AutomationState.setStatus(AutomationState.Status.STOPPED)
        AutomationState.log("⏹ Stopped")
        updateNotification("Stopped")
        overlay?.hide(); overlay = null

        if (closeActivity) {
            AutomationState.requestClose()
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showOverlay() {
        if (overlay != null) return
        overlay = OverlayManager(this).apply {
            updatePausedVisuals(false)
            onToggle = {
                when (AutomationState.getStatus()) {
                    AutomationState.Status.RUNNING -> doPause()
                    AutomationState.Status.PAUSED -> doResume()
                    else -> {}
                }
            }
            onClose = {
                stopAutomation(closeActivity = true)
            }
            show()
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
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
        loopJob?.cancel(); scope.cancel()
        overlay?.hide(); overlay = null
        super.onDestroy()
    }
}
