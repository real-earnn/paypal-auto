package com.real.earn.paypal.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.real.earn.paypal.service.AutomationState

object CaptchaAlert {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var isPlaying = false

    fun startAlert(context: Context) {
        if (isPlaying) return

        // Check user preference
        val prefs = AppPrefs(context)
        if (!prefs.captchaAlarmEnabled) {
            AutomationState.log("🔔 Captcha alarm disabled by user")
            return
        }

        // ── Pick URI: user-chosen or default ──
        val uri: Uri? = try {
            val customUri = prefs.captchaAlarmUri
            if (!customUri.isNullOrEmpty()) {
                Uri.parse(customUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            }
        } catch (e: Exception) {
            null
        }

        if (uri == null) {
            AutomationState.log("🔔 No alarm URI available")
            return
        }

        // ── Ringtone ──
        try {
            ringtone = RingtoneManager.getRingtone(context, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
                ringtone?.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone?.play()
        } catch (e: Exception) {
            AutomationState.log("🔔 Ringtone fail: ${e.message}")
        }

        // ── Vibration ──
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            val pattern = longArrayOf(0, 800, 400, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (_: Exception) {}

        isPlaying = true
        AutomationState.log("🔔 Captcha alert ON (human solve needed)")
    }

    fun stopAlert() {
        if (!isPlaying) return
        try { ringtone?.stop() } catch (_: Exception) {}
        ringtone = null
        try { vibrator?.cancel() } catch (_: Exception) {}
        vibrator = null
        isPlaying = false
        AutomationState.log("🔔 Captcha alert OFF")
    }

    fun isAlerting(): Boolean = isPlaying
}
