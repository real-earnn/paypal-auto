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
        flightPosition: Int,
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

            // 1. Clear PayPal data
            PayPalClearData.clear(service)
            delay(1000)

            // 2. Auto Flight toggle — if enabled by user
            if (autoFlightMode && flightPosition in 1..6) {
                AirplaneModeController.toggleFlightCycle(service, flightPosition)
            } else {
                AutomationState.log("ℹ Flight mode skip (disabled)")
            }

            // 3. Launch PayPal
            if (!PayPalActions.launchPayPal(service)) {
                onNumberFail(num); fail++
                continue
            }

            // 4. Wait for Get Started → number screen
            if (!PayPalActions.waitForNumberScreen(service, 15000)) {
                AutomationState.log("✘ Number screen nahi aayi")
                onNumberFail(num); fail++
                cleanupAndNext(service, autoFlightMode, flightPosition)
                continue
            }

            // 5. Dismiss popups
            PayPalActions.dismissPopupsIfAny(service)
            delay(300)

            // 6. Country selector
            if (!PayPalActions.openCountrySelector(service, 6000)) {
                AutomationState.log("⚠ Country selector fail")
                onNumberFail(num); fail++
                cleanupAndNext(service, autoFlightMode, flightPosition)
                continue
            }

            // 7. Select country
            if (!PayPalActions.selectCountry(service, countryName, countryIso)) {
                AutomationState.log("✘ Country select fail")
                onNumberFail(num); fail++
                cleanupAndNext(service, autoFlightMode, flightPosition)
                continue
            }

            // 8. Wait for return
            delay(1500)
            PayPalActions.dismissPopupsIfAny(service)
            PayPalActions.waitForNumberScreen(service, 6000)

            // 9. Fill number
            if (!PayPalActions.fillNumber(service, num, dialingCode)) {
                onNumberFail(num); fail++
                cleanupAndNext(service, autoFlightMode, flightPosition)
                continue
            }

            // 10. Continue
            PayPalActions.tapContinue(service)

            // 11. Result
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

            cleanupAndNext(service, autoFlightMode, flightPosition)
        }

        return Pair(success, fail)
    }

    private suspend fun cleanupAndNext(
        service: AccessibilityService,
        autoFlightMode: Boolean,
        flightPosition: Int
    ) {
        if (AutomationState.getStatus() == AutomationState.Status.STOPPING) return
        PayPalActions.closePayPal(service)
        delay(600)
        PayPalClearData.clear(service)
        delay(600)

        // Note: we do NOT do flight again here — it happens at start of next loop
        // unless user disabled it.
    }
}
