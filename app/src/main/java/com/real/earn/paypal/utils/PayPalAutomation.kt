package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.delay

object PayPalAutomation {

    /**
     * Run automation for a batch of numbers.
     * Returns pair (successCount, failCount) for this batch.
     * Callbacks are suspend so callers can do async work (e.g. backend release).
     */
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

            // 1. Clear PayPal data first
            PayPalClearData.clear(service)
            delay(800)

            // 2. Airplane ON → OFF
            AirplaneModeController.setAirplaneMode(service, true, 2500)
            AirplaneModeController.setAirplaneMode(service, false, 2000)

            // 3. Launch PayPal
            if (!PayPalActions.launchPayPal(service)) {
                onNumberFail(num)
                fail++
                continue
            }

            // 4. Wait for Get Started / Number screen
            val screenReady = PayPalActions.handleGetStartedIfPresent(service, 10000)
            if (!screenReady) {
                AutomationState.log("✘ Number screen nahi aayi")
                onNumberFail(num)
                fail++
                cleanupAndNext(service)
                continue
            }

            // 5. Wait for Continue loader
            PayPalActions.waitForContinueReady(service, 8000)

            // 6. Dismiss popups
            PayPalActions.dismissPopupsIfAny(service)

            // 7. Country selector
            if (PayPalActions.openCountrySelector(service)) {
                PayPalActions.selectCountry(service, countryName, countryIso)
                delay(1500)
                PayPalActions.dismissPopupsIfAny(service)
                PayPalActions.waitForContinueReady(service, 6000)
            } else {
                AutomationState.log("⚠ Country selector nahi mila (skip)")
            }

            // 8. Fill number
            if (!PayPalActions.fillNumber(service, num, dialingCode)) {
                onNumberFail(num)
                fail++
                cleanupAndNext(service)
                continue
            }

            // 9. Tap Continue
            PayPalActions.tapContinue(service)

            // 10. Wait for result
            val result = PayPalActions.waitForResult(service, 10000)

            when (result) {
                PayPalActions.Result.OTP_SCREEN -> {
                    AutomationState.log("✔ OTP screen — SUCCESS: $num")
                    PayPalActions.tapResendTwice(service)
                    onNumberSuccess(num)
                    success++
                }
                PayPalActions.Result.ERROR -> {
                    AutomationState.log("✘ Error red — FAIL: $num")
                    onNumberFail(num)
                    fail++
                }
                PayPalActions.Result.TIMEOUT -> {
                    AutomationState.log("⏱ No response in 10s — FAIL: $num")
                    onNumberFail(num)
                    fail++
                }
                PayPalActions.Result.UNKNOWN -> {
                    onNumberFail(num)
                    fail++
                }
            }

            cleanupAndNext(service)
        }

        return Pair(success, fail)
    }

    private suspend fun cleanupAndNext(service: AccessibilityService) {
        if (AutomationState.getStatus() == AutomationState.Status.STOPPING) return
        PayPalActions.closePayPal(service)
        delay(500)
        PayPalClearData.clear(service)
        delay(600)
        AirplaneModeController.setAirplaneMode(service, true, 2500)
        AirplaneModeController.setAirplaneMode(service, false, 2000)
    }
}
