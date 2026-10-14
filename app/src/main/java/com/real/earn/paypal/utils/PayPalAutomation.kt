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

            // ── 1. Clear PayPal data (also kills Settings) ──
            PayPalClearData.clear(service)
            delay(1000)

            // ── 2. Airplane ON → OFF ──
            AirplaneModeController.setAirplaneMode(service, true, 2500)
            AirplaneModeController.setAirplaneMode(service, false, 2000)

            // ── 3. Launch PayPal ──
            if (!PayPalActions.launchPayPal(service)) {
                onNumberFail(num); fail++
                continue
            }

            // ── 4. Wait for Get Started → click → wait for number screen ──
            if (!PayPalActions.waitForNumberScreen(service, 15000)) {
                AutomationState.log("✘ Number screen nahi aayi")
                onNumberFail(num); fail++
                cleanupAndNext(service)
                continue
            }

            // ── 5. Dismiss any popup (Gmail etc.) ──
            PayPalActions.dismissPopupsIfAny(service)
            delay(300)

            // ── 6. Open country selector (click "US" at top-right) ──
            if (!PayPalActions.openCountrySelector(service, 6000)) {
                AutomationState.log("⚠ Country selector nahi khula")
                onNumberFail(num); fail++
                cleanupAndNext(service)
                continue
            }

            // ── 7. Search + select country ──
            if (!PayPalActions.selectCountry(service, countryName, countryIso)) {
                AutomationState.log("✘ Country select fail")
                onNumberFail(num); fail++
                cleanupAndNext(service)
                continue
            }

            // ── 8. Wait for number screen to return ──
            delay(1500)
            PayPalActions.dismissPopupsIfAny(service)
            PayPalActions.waitForNumberScreen(service, 6000)

            // ── 9. Fill number ──
            if (!PayPalActions.fillNumber(service, num, dialingCode)) {
                onNumberFail(num); fail++
                cleanupAndNext(service)
                continue
            }

            // ── 10. Tap Continue ──
            PayPalActions.tapContinue(service)

            // ── 11. Wait for result ──
            val result = PayPalActions.waitForResult(service, 10000)

            when (result) {
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

            cleanupAndNext(service)
        }

        return Pair(success, fail)
    }

    private suspend fun cleanupAndNext(service: AccessibilityService) {
        if (AutomationState.getStatus() == AutomationState.Status.STOPPING) return
        PayPalActions.closePayPal(service)
        delay(600)
        PayPalClearData.clear(service)
        delay(600)
        AirplaneModeController.setAirplaneMode(service, true, 2500)
        AirplaneModeController.setAirplaneMode(service, false, 2000)
    }
}
