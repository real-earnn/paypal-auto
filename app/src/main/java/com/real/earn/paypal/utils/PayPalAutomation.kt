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
            AutomationState.log("─────────────")
            AutomationState.log("▶ NUMBER: $num")

            // ① Flight ON → 1s → OFF → HOME
            if (autoFlightMode) {
                AirplaneModeController.strictToggle(service)
                delay(700)
            }

            // ② Clear data
            val ok1 = PayPalClearData.strictClear(service)
            if (!ok1) {
                AutomationState.log("✘ Clear failed — skip")
                onNumberFail(num); fail++
                continue
            }
            delay(700)

            // ③ Launch
            if (!PayPalActions.launch(service)) {
                AutomationState.log("✘ Launch failed")
                onNumberFail(num); fail++
                continue
            }

            // ④ Get Started → number screen
            if (!PayPalActions.handleGetStartedAndWaitNumber(service, 15000)) {
                AutomationState.log("✘ Number screen not ready")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }

            // Small settle + dismiss any popup before US click
            delay(500)
            PayPalActions.dismissPopupsIfAny(service)

            // ⑤ Click US button
            if (!PayPalActions.clickUsButton(service, 8000)) {
                AutomationState.log("✘ US button fail")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }

            // ⑥ Search + select country
            if (!PayPalActions.typeAndSelectCountry(service, countryName, countryIso)) {
                AutomationState.log("✘ Country select fail")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }
            AutomationState.log("✔ Country set: $countryIso")

            // ⑦ After country select — wait for Gmail popup, close it
            AutomationState.log("ℹ Waiting for Gmail popup…")
            PayPalActions.waitAndClosePopup(service, 6000)
            delay(800)

            // ⑧ Type number
            if (!PayPalActions.typeNumber(service, num, dialingCode)) {
                AutomationState.log("✘ Number type fail")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }

            // ⑨ Continue
            if (!PayPalActions.tapContinue(service)) {
                AutomationState.log("✘ Continue fail")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }
            AutomationState.log("✔ Continue — waiting result…")

            // ⑩ Result
            when (PayPalActions.checkResult(service, 10000)) {
                PayPalActions.Result.OTP_SCREEN -> {
                    AutomationState.log("✔ OTP SCREEN — SUCCESS")
                    PayPalActions.tapResendTwice(service)
                    onNumberSuccess(num)
                    success++
                }
                PayPalActions.Result.ERROR -> {
                    AutomationState.log("✘ RED ERROR — FAIL")
                    onNumberFail(num); fail++
                }
                PayPalActions.Result.TIMEOUT -> {
                    AutomationState.log("⏱ TIMEOUT — FAIL")
                    onNumberFail(num); fail++
                }
            }

            // Close PayPal before next iteration
            PayPalActions.closePayPal(service)
            delay(800)
        }

        return Pair(success, fail)
    }
}
