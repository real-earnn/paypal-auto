package com.real.earn.paypal.utils

import android.content.Context
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.real.earn.paypal.service.AutomationState

/**
 * Plays system alarm ringtone + vibration to alert the human that
 * a manual captcha solve is needed.
 *
 * No audio file needed — uses the phone's built-in alarm sound.
 */
object CaptchaAlert {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var isPlaying = false

    fun startAlert(context: Context) {
        if (isPlaying) return

        try {
            // ── Ringtone: try alarm → notification → ringtone ──
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            ringtone = RingtoneManager.getRingtone(context, uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ringtone?.isLooping = true
                ringtone?.audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone?.play()
        } catch (e: Exception) {
            AutomationState.log("🔔 Ringtone fail: ${e.message}")
        }

        try {
            // ── Vibration pattern: long-long-long ──
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
