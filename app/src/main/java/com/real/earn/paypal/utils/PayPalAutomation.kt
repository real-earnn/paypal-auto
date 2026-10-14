package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.delay

object PayPalAutomation {

    suspend fun runBatch(
        service: AccessibilityService,package com.real.earn.paypal.utils

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

            // ① Flight (retry 3 times)
            if (autoFlightMode) {
                val ok = StepUtils.retryStep("Flight cycle",
                    { AirplaneModeController.strictToggle(service) })
                if (!ok) {
                    AutomationState.log("✘ [ABORT] Flight failed")
                    onNumberFail(num); fail++
                    continue
                }
            }
            StepUtils.sleepMs(1000)

            // ② Clear data (retry 3 times)
            val clearOk = StepUtils.retryStep("Clear data",
                { PayPalClearData.strictClear(service) })
            if (!clearOk) {
                AutomationState.log("✘ [ABORT] Clear data failed")
                onNumberFail(num); fail++
                continue
            }
            StepUtils.sleepMs(1200)

            // ③ Launch (retry 3 times)
            val launchOk = StepUtils.retryStep("Launch PayPal",
                { PayPalActions.launch(service) })
            if (!launchOk) {
                AutomationState.log("✘ [ABORT] Launch failed")
                onNumberFail(num); fail++
                continue
            }
            StepUtils.sleepMs(1000)

            // ④ Number screen (retry 3 times)
            val numScreenOk = StepUtils.retryStep("Number screen",
                { PayPalActions.handleGetStartedAndWaitNumber(service, 8000) })
            if (!numScreenOk) {
                AutomationState.log("✘ [ABORT] Number screen timeout")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }
            StepUtils.sleepMs(800)

            PayPalActions.dismissPopupsIfAny(service)
            StepUtils.sleepMs(500)

            // ⑤ US button (retry)
            val usOk = StepUtils.retryStep("US button",
                { PayPalActions.clickUsButton(service, 5000) })
            if (!usOk) {
                AutomationState.log("✘ [ABORT] US button fail")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }
            StepUtils.sleepMs(800)

            // ⑥ Country select (retry)
            val countryOk = StepUtils.retryStep("Country select",
                { PayPalActions.typeAndSelectCountry(service, countryName, countryIso) })
            if (!countryOk) {
                AutomationState.log("✘ [ABORT] Country select fail")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }
            AutomationState.log("✔ Country: $countryIso")
            StepUtils.sleepMs(800)

            // ⑦ Gmail popup after country
            PayPalActions.waitAndClosePopup(service, 4000)
            StepUtils.sleepMs(700)

            // ⑧ Number type (retry)
            val numTypeOk = StepUtils.retryStep("Number type",
                { PayPalActions.typeNumber(service, num, dialingCode) })
            if (!numTypeOk) {
                AutomationState.log("✘ [ABORT] Number type fail")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }
            StepUtils.sleepMs(700)

            // ⑨ Continue (retry)
            val contOk = StepUtils.retryStep("Continue",
                { PayPalActions.tapContinue(service) })
            if (!contOk) {
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
            delay(800)
        }

        return Pair(success, fail)
    }
}
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
