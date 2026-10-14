package com.real.earn.paypal.voice

import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.util.Log

/**
 * Voice Interaction Service — makes this app eligible to be the "Default Assistant".
 * Once set as assistant, app can control airplane mode without root/ADB on Android 7+.
 */
class PayPalInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        Log.d(TAG, "VoiceInteractionService ready — Assist App available")
    }

    companion object {
        private const val TAG = "PayPalVoiceSvc"
    }
}

/**
 * Session Service — Android instantiates this when the assistant is triggered.
 */
class PayPalInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return PayPalInteractionSession(this)
    }
}

/**
 * Session — actual airplane mode toggling logic goes here.
 */
class PayPalInteractionSession(context: Context) : VoiceInteractionSession(context) {

    /**
     * Toggle airplane mode via the system — only works when this app
     * is set as the Default Assistant.
     */
    fun setAirplaneMode(enable: Boolean): Boolean {
        return try {
            val intent = android.content.Intent(Settings.ACTION_VOICE_CONTROL_AIRPLANE_MODE)
            intent.putExtra(Settings.EXTRA_AIRPLANE_MODE_ENABLED, enable)
            startVoiceActivity(intent)
            Log.d(TAG, "Airplane mode intent fired: enable=$enable")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle airplane mode: ${e.message}")
            false
        }
    }

    companion object {
        private const val TAG = "PayPalVoiceSession"
    }
}
