package com.real.earn.paypal.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.real.earn.paypal.App
import com.real.earn.paypal.AutomationActivity
import com.real.earn.paypal.R
import com.real.earn.paypal.api.ApiClient
import com.real.earn.paypal.api.GetNumberBody
import com.real.earn.paypal.api.GetNumberByPrefixBody
import com.real.earn.paypal.api.ReleaseNumberBody
import com.real.earn.paypal.auth.AuthManager
import com.real.earn.paypal.utils.*
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
    private var wakeLock: PowerManager.WakeLock? = null
    private var screenLock: PowerManager.WakeLock? = null

    private var mode: String = "service"
    private var engineType: String = "paypal"
    private var serviceName: String = "PayPal"
    private var countryName: String = ""
    private var countryCodeIso: String = ""
    private var dialingCode: String = ""
    private var prefix: String = ""
    private var file: String = ""
    private var oldAccountMethod: Boolean = false

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL = App.CHANNEL_AUTOMATION
        private const val ACTION_START = "com.real.earn.paypal.START"
        private const val ACTION_PAUSE = "com.real.earn.paypal.PAUSE"
        private const val ACTION_RESUME = "com.real.earn.paypal.RESUME"
        private const val ACTION_STOP = "com.real.earn.paypal.STOP"
        private const val ACTION_STOP_AND_CLOSE = "com.real.earn.paypal.STOP_AND_CLOSE"

        fun start(
            context: Context, mode: String, service: String,
            country: String = "", countryCode: String = "",
            dialingCode: String = "", prefix: String = "", file: String = "",
            engineType: String = "paypal", oldAccountMethod: Boolean = false
        ) {
            val i = Intent(context, AutomationService::class.java).apply {
                action = ACTION_START
                putExtra("mode", mode); putExtra("service", service)
                putExtra("country", country); putExtra("countryCode", countryCode)
                putExtra("dialingCode", dialingCode); putExtra("prefix", prefix)
                putExtra("file", file); putExtra("engine_type", engineType)
                putExtra("old_account_method", oldAccountMethod)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun pause(context: Context) = context.startService(Intent(context, AutomationService::class.java).setAction(ACTION_PAUSE))
        fun resume(context: Context) = context.startService(Intent(context, AutomationService::class.java).setAction(ACTION_RESUME))
        fun stop(context: Context) = context.startService(Intent(context, AutomationService::class.java).setAction(ACTION_STOP))
        fun stopAndClose(context: Context) = context.startService(Intent(context, AutomationService::class.java).setAction(ACTION_STOP_AND_CLOSE))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification("Initializing…"))
        acquireWakeLock()
        acquireScreenLock()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RealEarn::AutoWakeLock").apply {
                setReferenceCounted(false); acquire(2 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {}
    }

    @Suppress("DEPRECATION")
    private fun acquireScreenLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            val flags = PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE
            screenLock = pm.newWakeLock(flags, "RealEarn::ScreenLock").apply {
                setReferenceCounted(false); acquire(2 * 60 * 60 * 1000L)
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    private fun releaseScreenLock() {
        try { if (screenLock?.isHeld == true) screenLock?.release() } catch (_: Exception) {}
        screenLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                mode = intent.getStringExtra("mode") ?: "service"
                engineType = intent.getStringExtra("engine_type") ?: "paypal"
                serviceName = intent.getStringExtra("service") ?: "PayPal"
                countryName = intent.getStringExtra("country") ?: ""
                countryCodeIso = intent.getStringExtra("countryCode") ?: ""
                dialingCode = intent.getStringExtra("dialingCode") ?: ""
                prefix = intent.getStringExtra("prefix") ?: ""
                file = intent.getStringExtra("file") ?: ""
                oldAccountMethod = intent.getBooleanExtra("old_account_method", false)
                showOverlay()
                startLoop()
            }
            ACTION_PAUSE -> doPause()
            ACTION_RESUME -> doResume()
            ACTION_STOP -> stopAutomation(false)
            ACTION_STOP_AND_CLOSE -> stopAutomation(true)
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

        if (oldAccountMethod) {
            AutomationState.setStatus(AutomationState.Status.PAUSED)
            AutomationState.log("🚀 TikTok Old Account: PAUSED. Navigate & tap Resume.")
        } else {
            AutomationState.setStatus(AutomationState.Status.RUNNING)
            val en = if (engineType == "tiktok") "TikTok" else "PayPal"
            AutomationState.log("🚀 $en: $serviceName / $countryName")
        }
        updateNotification("Running")

        loopJob = scope.launch {
            var popupOn = false
            var captchaOn = false
            var watchdogOn = false
            try {
                val a11y = PayPalAccessibilityService.instance
                if (a11y == null) {
                    AutomationState.log("✘ Accessibility OFF")
                    AutomationState.setStatus(AutomationState.Status.STOPPED)
                    return@launch
                }
                AutomationState.log("✔ Accessibility connected")

                PopupWatcher.start(scope, a11y); popupOn = true
                CaptchaWatcher.start(scope, a11y); captchaOn = true

                // 🟢 Accessibility Watchdog: detect if system kills the service
                val watchdogJob = scope.launch {
                    watchdogOn = true
                    while (true) {
                        delay(10000)
                        if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
                        if (PayPalAccessibilityService.instance == null) {
                            AutomationState.log("🚨 Accessibility service KILLED by system!")
                            AutomationState.log("ℹ Please re-enable from Settings → Accessibility")
                            updateNotification("⚠ Accessibility OFF — tap to re-enable")
                            AutomationState.setStatus(AutomationState.Status.STOPPED)
                            break
                        }
                    }
                }

                val auth = AuthManager(applicationContext)
                val prefs = AppPrefs(applicationContext)
                val autoFlight = prefs.autoFlightMode

                while (AutomationState.getStatus() != AutomationState.Status.STOPPING) {
                    while (AutomationState.getStatus() == AutomationState.Status.PAUSED) delay(300)
                    if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break

                    // Recheck accessibility each iteration
                    if (PayPalAccessibilityService.instance == null) {
                        AutomationState.log("✘ Accessibility lost — stopping")
                        break
                    }

                    AutomationState.log("📞 Fetching 5 numbers...")
                    val nums = fetchNumbers(auth)
                    if (nums.isEmpty()) {
                        AutomationState.log("⚠ No numbers — wait 15s")
                        delay(15000); continue
                    }
                    AutomationState.log("✔ ${nums.size} numbers")

                    val (s, f) = if (engineType == "tiktok" && oldAccountMethod) {
                        val res = TikTokOldAccountAutomation.runBatch(
                            service = a11y, numbers = nums,
                            onNumberStart = { num -> AutomationState.updateStats { it.copy(totalAttempts = it.totalAttempts + 1, currentService = serviceName, currentCountry = countryName, currentNumber = num) } },
                            onNumberSuccess = { _ -> AutomationState.updateStats { it.copy(successCount = it.successCount + 1) } },
                            onNumberFail = { num -> AutomationState.updateStats { it.copy(failCount = it.failCount + 1) }; releaseNumbers(auth, listOf(num), "otp_failed") }
                        )
                        if (res.first == -1 && res.second == -1) {
                            AutomationState.log("🔄 STUCK — resetting")
                            if (autoFlight) StepUtils.retryStep("Flight") { AirplaneModeController.strictToggle(a11y) }
                            val tiktokPkg = TikTokActions.getInstalledPackage(a11y)
                            StepUtils.retryStep("Clear") { PayPalClearData.strictClear(a11y, tiktokPkg) }
                            StepUtils.sleepMs(2000)
                            continue
                        }
                        res
                    } else if (engineType == "tiktok") {
                        TikTokAutomation.runBatch(
                            service = a11y, numbers = nums, countryName = countryName,
                            dialingCode = dialingCode.ifEmpty { "92" }, autoFlightMode = autoFlight,
                            onNumberStart = { num -> AutomationState.updateStats { it.copy(totalAttempts = it.totalAttempts + 1, currentService = serviceName, currentCountry = countryName, currentNumber = num) } },
                            onNumberSuccess = { _ -> AutomationState.updateStats { it.copy(successCount = it.successCount + 1) } },
                            onNumberFail = { num -> AutomationState.updateStats { it.copy(failCount = it.failCount + 1) }; releaseNumbers(auth, listOf(num), "otp_failed") }
                        )
                    } else {
                        PayPalAutomation.runBatch(
                            service = a11y, numbers = nums, countryName = countryName,
                            countryIso = countryCodeIso, dialingCode = dialingCode,
                            autoFlightMode = autoFlight,
                            onNumberStart = { num -> AutomationState.updateStats { it.copy(totalAttempts = it.totalAttempts + 1, currentService = serviceName, currentCountry = countryName, currentNumber = num) } },
                            onNumberSuccess = { _ -> AutomationState.updateStats { it.copy(successCount = it.successCount + 1) } },
                            onNumberFail = { num -> AutomationState.updateStats { it.copy(failCount = it.failCount + 1) }; releaseNumbers(auth, listOf(num), "otp_failed") }
                        )
                    }

                    AutomationState.log("📊 Batch: Success=$s Fail=$f")
                    if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
                    delay(800)
                }

                watchdogJob.cancel()
                AutomationState.log("⏹ Loop exit")
            } catch (e: Exception) {
                AutomationState.log("✘ Loop error: ${e.message}")
            } finally {
                if (popupOn) PopupWatcher.stop()
                if (captchaOn) CaptchaWatcher.stop()
                AutomationState.setStatus(AutomationState.Status.STOPPED)
                cleanupAfterStop()
            }
        }
    }

    private fun cleanupAfterStop() {
        scope.launch {
            try {
                updateNotification("Stopped")
                overlay?.hide(); overlay = null
                releaseWakeLock(); releaseScreenLock()
                stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
            } catch (_: Exception) {}
        }
    }

    private suspend fun fetchNumbers(auth: AuthManager): List<String> {
        return try {
            when (mode) {
                "auto" -> {
                    val res = ApiClient.service(auth).getNumbersByPrefix(GetNumberByPrefixBody(service = serviceName, auto = true, count = 5))
                    if (res.isSuccessful && res.body()?.success == true) {
                        val body = res.body()!!
                        countryCodeIso = body.countryCode; dialingCode = body.dialingCode
                        countryName = body.file.ifEmpty { "Unknown" }
                        body.numbers ?: emptyList()
                    } else emptyList()
                }
                "prefix" -> {
                    val res = ApiClient.service(auth).getNumbersByPrefix(GetNumberByPrefixBody(service = serviceName, prefix = prefix, file = file, auto = false, count = 5))
                    if (res.isSuccessful && res.body()?.success == true) {
                        val body = res.body()!!
                        countryCodeIso = body.countryCode; dialingCode = body.dialingCode
                        countryName = body.file.ifEmpty { file }
                        body.numbers ?: emptyList()
                    } else emptyList()
                }
                else -> {
                    val res = ApiClient.service(auth).getNumbers(GetNumberBody(service = serviceName, country = countryName, count = 5))
                    if (res.isSuccessful && res.body()?.success == true) {
                        val body = res.body()!!
                        countryCodeIso = body.countryCode; dialingCode = body.dialingCode
                        body.numbers ?: emptyList()
                    } else emptyList()
                }
            }
        } catch (e: Exception) { emptyList() }
    }

    private suspend fun releaseNumbers(auth: AuthManager, nums: List<String>, reason: String) {
        try { ApiClient.service(auth).releaseNumbers(ReleaseNumberBody(numbers = nums, reason = reason)) } catch (_: Exception) {}
    }

    private fun stopAutomation(closeActivity: Boolean) {
        val cur = AutomationState.getStatus()
        if (cur == AutomationState.Status.STOPPING || cur == AutomationState.Status.STOPPED) return
        AutomationState.setStatus(AutomationState.Status.STOPPING)
        AutomationState.log("⏹ Stopping…")
        PopupWatcher.stop(); CaptchaWatcher.stop()
        scope.launch {
            var waited = 0L
            while (loopJob?.isActive == true && waited < 6000) { delay(200); waited += 200 }
            loopJob?.cancel(); loopJob = null
            if (AutomationState.getStatus() != AutomationState.Status.STOPPED) {
                AutomationState.setStatus(AutomationState.Status.STOPPED)
                cleanupAfterStop()
            }
            if (closeActivity) AutomationState.requestClose()
        }
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
            onClose = { stopAutomation(true) }
            show()
        }
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, AutomationActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.foreground_notif_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true).setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW).build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        PopupWatcher.stop(); CaptchaWatcher.stop()
        loopJob?.cancel(); scope.cancel()
        overlay?.hide(); overlay = null
        releaseWakeLock(); releaseScreenLock()
        super.onDestroy()
    }
}
