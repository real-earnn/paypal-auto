package com.real.earn.paypal.utils

import android.content.Context
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.delay

/**
 * 🟢 System actions via ADB shell (fast, device-agnostic).
 *
 * All functions return:
 *   - null  → ADB not paired / not connected → caller should fallback to accessibility
 *   - true  → command succeeded
 *   - false → command failed (fallback recommended)
 */
object SystemActionsViaAdb {

    private const val FLIGHT_CYCLES = 5
    private const val FLIGHT_DELAY_MS = 1200L

    /**
     * Cycle airplane mode ON/OFF [cycles] times so the IP changes.
     * Uses `cmd connectivity airplane-mode` (API 29+), falls back to `settings put global`.
     */
    suspend fun cycleAirplaneMode(context: Context, cycles: Int = FLIGHT_CYCLES): Boolean? {
        if (!AdbPairingManager.isConnected(context)) return null

        AutomationState.log("✈ ADB flight mode cycle ×$cycles")
        for (i in 1..cycles) {
            val on = setAirplaneMode(context, true)
            if (on == null) return null
            if (!on) return false
            delay(FLIGHT_DELAY_MS)

            val off = setAirplaneMode(context, false)
            if (off == null) return null
            if (!off) return false
            delay(FLIGHT_DELAY_MS)

            AutomationState.log("  ↳ cycle $i/$cycles done")
        }
        AutomationState.log("✔ ADB flight cycle finished")
        return true
    }

    private suspend fun setAirplaneMode(context: Context, enable: Boolean): Boolean? {
        val primary = if (enable)
            "cmd connectivity airplane-mode enable"
        else
            "cmd connectivity airplane-mode disable"

        val primaryResult = AdbPairingManager.executeShell(context, primary)
        if (primaryResult != null && primaryResult.exitCode == 0) return true

        // Fallback for OEMs where `cmd connectivity` is blocked
        val fallback = if (enable)
            "settings put global airplane_mode_on 1; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true"
        else
            "settings put global airplane_mode_on 0; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false"

        val fallbackResult = AdbPairingManager.executeShell(context, fallback)
        return fallbackResult?.exitCode == 0
    }

    /** Clear all data of [packageName] via `pm clear`. */
    suspend fun clearAppData(context: Context, packageName: String): Boolean? {
        if (!AdbPairingManager.isConnected(context)) return null
        AutomationState.log("🧹 ADB pm clear $packageName")
        val res = AdbPairingManager.executeShell(context, "pm clear $packageName")
        return res?.exitCode == 0
    }

    /** Force-stop an app. */
    suspend fun forceStopApp(context: Context, packageName: String): Boolean? {
        if (!AdbPairingManager.isConnected(context)) return null
        val res = AdbPairingManager.executeShell(context, "am force-stop $packageName")
        return res?.exitCode == 0
    }

    /** Launch an app's main activity via monkey. */
    suspend fun launchApp(context: Context, packageName: String): Boolean? {
        if (!AdbPairingManager.isConnected(context)) return null
        AutomationState.log("🚀 ADB launch $packageName")
        val res = AdbPairingManager.executeShell(context, "monkey -p $packageName 1")
        return res?.exitCode == 0
    }
}
