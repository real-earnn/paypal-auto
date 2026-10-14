package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.delay

object PayPalAutomation {

    suspend fun runBatch(
        service: AccessibilityService,
        numbers: List<String>,
        countryName: String,
        countryIso: String,
        dialingCode: String,
        autoFlightMode: Boolean,
        onNumberStart: suspend (String) -> Unit,
        onNumberSuccess: suspend (String) -> Unit,
        onNumberFail: suspend (String) -> Unit
    ): Pair<Int, Int> {

        var success = 0
        var fail = 0

        for (num in numbers) {
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
            while (AutomationState.getStatus() == AutomationState.Status.PAUSED) delay(500)
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break

            onNumberStart(num)
            AutomationState.log("▶ Trying: $num")

            // ── STEP 1: Clear PayPal data ──
            PayPalClearData.clear(service)
            delay(1200)

            // ── STEP 2: Flight cycle (if enabled) ──
            if (autoFlightMode) {
                AirplaneModeController.toggleCycle(service)
            } else {
                AutomationState.log("ℹ Flight skip")
            }
            delay(800)

            // ── STEP 3: Launch PayPal ──
            if (!PayPalActions.launchPayPal(service)) {
                onNumberFail(num); fail++
                continue
            }

            // ── STEP 4: Wait for number screen ──
            if (!PayPalActions.waitForNumberScreen(service, 15000)) {
                AutomationState.log("✘ Number screen miss")
                onNumberFail(num); fail++
                cleanup(service)
                continue
            }

            // ── STEP 5: Dismiss any popup ──
            PayPalActions.dismissPopupsIfAny(service)
            delay(300)

            // ── STEP 6: Open country selector ──
            if (!PayPalActions.openCountrySelector(service, 6000)) {
                AutomationState.log("✘ Country selector open fail")
                onNumberFail(num); fail++
                cleanup(service)
                continue
            }
            AutomationState.log("ℹ Dialog open")

            // ── STEP 7: Select country ──
            if (!PayPalActions.selectCountry(service, countryName, countryIso)) {
                AutomationState.log("✘ Country select fail")
                onNumberFail(num); fail++
                cleanup(service)
                continue
            }
            AutomationState.log("✔ Country set: $countryIso")

            // ── STEP 8: Wait for number screen to return ──
            delay(1200)
            PayPalActions.dismissPopupsIfAny(service)
            PayPalActions.waitForNumberScreen(service, 6000)

            // ── STEP 9: Fill number ──
            if (!PayPalActions.fillNumber(service, num, dialingCode)) {
                onNumberFail(num); fail++
                cleanup(service)
                continue
            }

            // ── STEP 10: Continue ──
            if (!PayPalActions.tapContinue(service)) {
                onNumberFail(num); fail++
                cleanup(service)
                continue
            }

            // ── STEP 11: Wait result ──
            when (PayPalActions.waitForResult(service, 10000)) {
                PayPalActions.Result.OTP_SCREEN -> {
                    AutomationState.log("✔ OTP screen — SUCCESS: $num")
                    PayPalActions.tapResendTwice(service)
                    onNumberSuccess(num)
                    success++
                }
                PayPalActions.Result.ERROR -> {
                    AutomationState.log("✘ Error — FAIL: $num")
                    onNumberFail(num); fail++
                }
                PayPalActions.Result.TIMEOUT -> {
                    AutomationState.log("⏱ Timeout — FAIL: $num")
                    onNumberFail(num); fail++
                }
            }

            cleanup(service)
        }

        return Pair(success, fail)
    }

    private suspend fun cleanup(service: AccessibilityService) {
        if (AutomationState.getStatus() == AutomationState.Status.STOPPING) return
        PayPalActions.closePayPal(service)
        delay(600)
        PayPalClearData.clear(service)
        delay(500)
    }
}
