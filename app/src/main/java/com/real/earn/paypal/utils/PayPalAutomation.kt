package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.delay

/**
 * Main automation flow — one full cycle per number.
 * Uses NodeFinder + PayPalActions + PayPalClearData + AirplaneModeController.
 */
object PayPalAutomation {

    /**
     * Run automation for a batch of numbers.
     * Returns pair (successCount, failCount) for this batch.
     */
    suspend fun runBatch(
        service: AccessibilityService,
        numbers: List<String>,
        countryName: String,
        countryIso: String,
        dialingCode: String,
        onNumberStart: (String) -> Unit,
        onNumberSuccess: (String) -> Unit,
        onNumberFail: (String) -> Unit
    ): Pair<Int, Int> {

        var success = 0
        var fail = 0

        for (num in numbers) {
            // Respect pause / stop
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
            while (AutomationState.getStatus() == AutomationState.Status.PAUSED) delay(500)
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break

            onNumberStart(num)
            AutomationState.log("▶ Trying: $num")

            // 1. Clear PayPal data first (fresh state)
            PayPalClearData.clear(service)
            delay(800)

            // 2. Airplane ON → OFF (fresh IP)
            AirplaneModeController.setAirplaneMode(service, true, 2500)
            AirplaneModeController.setAirplaneMode(service, false, 2000)

            // 3. Launch PayPal
            if (!PayPalActions.launchPayPal(service)) {
                onNumberFail(num)
                fail++
                continue
            }

            // 4. Wait for either Get Started or Number screen
            val screenReady = PayPalActions.handleGetStartedIfPresent(service, 10000)
            if (!screenReady) {
                AutomationState.log("✘ Number screen nahi aayi")
                onNumberFail(num)
                fail++
                cleanupAndNext(service)
                continue
            }

            // 5. Wait for Continue loader to stop
            PayPalActions.waitForContinueReady(service, 8000)

            // 6. Dismiss any popup (Gmail etc.)
            PayPalActions.dismissPopupsIfAny(service)

            // 7. Open country selector
            if (PayPalActions.openCountrySelector(service)) {
                // 8. Search + select country
                PayPalActions.selectCountry(service, countryName, countryIso)
                delay(1500)

                // 9. Post-select popup dismiss
                PayPalActions.dismissPopupsIfAny(service)

                // 10. Wait for loader after select
                PayPalActions.waitForContinueReady(service, 6000)
            } else {
                AutomationState.log("⚠ Country selector nahi mila (skip)")
            }

            // 11. Fill number
            if (!PayPalActions.fillNumber(service, num, dialingCode)) {
                onNumberFail(num)
                fail++
                cleanupAndNext(service)
                continue
            }

            // 12. Tap Continue
            PayPalActions.tapContinue(service)

            // 13. Wait for result
            val result = PayPalActions.waitForResult(service, 10000)

            when (result) {
                PayPalActions.Result.OTP_SCREEN -> {
                    AutomationState.log("✔ OTP screen — SUCCESS: $num")
                    // Tap Resend 2x with 5s waits
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

            // 14. Cleanup for next round
            cleanupAndNext(service)
        }

        return Pair(success, fail)
    }

    private suspend fun cleanupAndNext(service: AccessibilityService) {
        if (AutomationState.getStatus() == AutomationState.Status.STOPPING) return

        // Close PayPal
        PayPalActions.closePayPal(service)
        delay(500)

        // Clear data
        PayPalClearData.clear(service)
        delay(600)

        // Airplane ON → OFF
        AirplaneModeController.setAirplaneMode(service, true, 2500)
        AirplaneModeController.setAirplaneMode(service, false, 2000)
    }
}
