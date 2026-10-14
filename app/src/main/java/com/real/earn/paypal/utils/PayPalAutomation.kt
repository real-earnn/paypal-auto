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
            while (AutomationState.getStatus() == AutomationState.Status.PAUSED) delay(300)
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break

            onNumberStart(num)
            AutomationState.log("─────────────")
            AutomationState.log("▶ $num")

            // ① Flight
            if (autoFlightMode) {
                val ok = StepUtils.retryStep("Flight cycle") {
                    AirplaneModeController.strictToggle(service)
                }
                if (!ok) {
                    AutomationState.log("✘ [ABORT] Flight")
                    onNumberFail(num); fail++
                    continue
                }
            }

            // ② Clear data (with 3 retries)
            val clearOk = StepUtils.retryStep("Clear data") {
                PayPalClearData.strictClear(service)
            }
            if (!clearOk) {
                AutomationState.log("✘ [ABORT] Clear data")
                onNumberFail(num); fail++
                continue
            }
            StepUtils.sleepMs(800)

            // ③ Launch
            val launchOk = StepUtils.retryStep("Launch") {
                PayPalActions.launch(service)
            }
            if (!launchOk) {
                AutomationState.log("✘ [ABORT] Launch")
                onNumberFail(num); fail++
                continue
            }

            // ④ Get Started — click instantly
            PayPalActions.tapGetStarted(service, 10000)

            // ⑤ US button — poll & hardcoded tap
            val usOk = StepUtils.retryStep("US button") {
                PayPalActions.clickUsButton(service, 8000)
            }
            if (!usOk) {
                AutomationState.log("✘ [ABORT] US button")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }

            // ⑥ Country select — by text "Search"
            val countryOk = StepUtils.retryStep("Country select") {
                PayPalActions.typeAndSelectCountry(service, countryName, countryIso)
            }
            if (!countryOk) {
                AutomationState.log("✘ [ABORT] Country select")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }
            AutomationState.log("✔ Country: $countryIso")

            // ⑦ Number type — by text "phone number"
            val numTypeOk = StepUtils.retryStep("Number type") {
                PayPalActions.typeNumber(service, num, dialingCode)
            }
            if (!numTypeOk) {
                AutomationState.log("✘ [ABORT] Number type")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }

            // ⑧ Continue
            val contOk = StepUtils.retryStep("Continue") {
                PayPalActions.tapContinue(service)
            }
            if (!contOk) {
                AutomationState.log("✘ [ABORT] Continue")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }
            AutomationState.log("ℹ Continue — waiting result…")

            // ⑨ Result
            when (PayPalActions.checkResult(service, 10000)) {
                PayPalActions.Result.OTP_SCREEN -> {
                    AutomationState.log("✔ SUCCESS")
                    PayPalActions.tapResendTwice(service)
                    onNumberSuccess(num)
                    success++
                }
                PayPalActions.Result.ERROR -> {
                    AutomationState.log("✘ Red error")
                    onNumberFail(num); fail++
                }
                PayPalActions.Result.TIMEOUT -> {
                    AutomationState.log("⏱ Timeout")
                    onNumberFail(num); fail++
                }
            }

            PayPalActions.closePayPal(service)
            delay(800)
        }

        return Pair(success, fail)
    }
}
