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
                val ok = AirplaneModeController.strictToggle(service)
                if (!ok) {
                    AutomationState.log("✘ [ABORT] Flight failed")
                    onNumberFail(num); fail++
                    continue
                }
            }

            // ② Clear
            if (!PayPalClearData.strictClear(service)) {
                AutomationState.log("✘ [ABORT] Clear data failed")
                onNumberFail(num); fail++
                continue
            }

            // ③ Launch
            if (!PayPalActions.launch(service)) {
                AutomationState.log("✘ [ABORT] Launch failed")
                onNumberFail(num); fail++
                continue
            }

            // ④ Number screen
            if (!PayPalActions.handleGetStartedAndWaitNumber(service, 10000)) {
                AutomationState.log("✘ [ABORT] Number screen timeout")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }

            // quick popup dismiss
            PayPalActions.dismissPopupsIfAny(service)

            // ⑤ US button
            if (!PayPalActions.clickUsButton(service, 6000)) {
                AutomationState.log("✘ [ABORT] US button fail")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }

            // ⑥ Country
            if (!PayPalActions.typeAndSelectCountry(service, countryName, countryIso)) {
                AutomationState.log("✘ [ABORT] Country select fail")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }
            AutomationState.log("✔ Country: $countryIso")

            // ⑦ Gmail popup after country
            PayPalActions.waitAndClosePopup(service, 4000)

            // ⑧ Number
            if (!PayPalActions.typeNumber(service, num, dialingCode)) {
                AutomationState.log("✘ [ABORT] Number type fail")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }

            // ⑨ Continue
            if (!PayPalActions.tapContinue(service)) {
                AutomationState.log("✘ [ABORT] Continue not found")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }
            AutomationState.log("ℹ Continue — waiting result…")

            // ⑩ Result
            when (PayPalActions.checkResult(service, 8000)) {
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
            delay(400)
        }

        return Pair(success, fail)
    }
}
