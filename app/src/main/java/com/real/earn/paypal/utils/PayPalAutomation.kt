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

        if (numbers.isEmpty()) return Pair(0, 0)

        if (autoFlightMode) {
            val ok = StepUtils.retryStep("Flight cycle") { AirplaneModeController.strictToggle(service) }
            if (!ok) return Pair(0, numbers.size)
        }

        val clearOk = StepUtils.retryStep("Clear data") { PayPalClearData.strictClear(service, "com.paypal.android.p2pmobile") }
        if (!clearOk) return Pair(0, numbers.size)
        StepUtils.sleepMs(800)

        val launchOk = StepUtils.retryStep("Launch") { PayPalActions.launch(service) }
        if (!launchOk) return Pair(0, numbers.size)

        PayPalActions.tapGetStarted(service, 12000)

        if (!PayPalActions.clickUsButton(service)) {
            PayPalActions.closePayPal(service)
            return Pair(0, numbers.size)
        }

        if (!StepUtils.retryStep("Country select") { PayPalActions.typeAndSelectCountry(service, countryName, countryIso) }) {
            PayPalActions.closePayPal(service)
            return Pair(0, numbers.size)
        }

        var consecutiveFails = 0

        for (num in numbers) {
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
            while (AutomationState.getStatus() == AutomationState.Status.PAUSED) delay(300)
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break

            if (consecutiveFails >= 3) {
                AutomationState.log("✘ 3 consecutive failures — ending round")
                break
            }

            onNumberStart(num)
            AutomationState.log("─────────────")
            AutomationState.log("▶ $num")

            if (!StepUtils.retryStep("Number type") { PayPalActions.typeNumber(service, num, dialingCode) }) {
                onNumberFail(num); fail++
                consecutiveFails++
                continue
            }

            if (!StepUtils.retryStep("Continue") { PayPalActions.tapContinue(service) }) {
                onNumberFail(num); fail++
                consecutiveFails++
                continue
            }

            var finalResult = PayPalActions.Result.TIMEOUT
            var captchaRetries = 0

            while (captchaRetries < 6) {
                if (StepUtils.shouldStop()) break
                val r = PayPalActions.checkResult(service, 15000)
                if (r == PayPalActions.Result.RETRY_CONTINUE) {
                    captchaRetries++
                    StepUtils.sleepMs(1000)
                    if (!PayPalActions.tapContinue(service)) { StepUtils.sleepMs(2000); continue }
                    StepUtils.sleepMs(1500)
                    continue
                }
                finalResult = r
                break
            }

            when (finalResult) {
                PayPalActions.Result.OTP_SCREEN -> {
                    AutomationState.log("✔ OTP screen — SUCCESS: $num")
                    onNumberSuccess(num); success++
                    consecutiveFails = 0

                    PayPalActions.tapResendTwice(service)

                    StepUtils.sleepMs(2000)
                    if (PayPalActions.hasRedError(service)) {
                        onNumberFail(num); fail++
                        consecutiveFails++
                        PayPalActions.closeResendPopup(service)
                        PayPalActions.clearNumberField(service)
                        StepUtils.sleepMs(200)
                        continue
                    }

                    // 🟢 FIX: Try to close popup, verify if it actually closed
                    AutomationState.log("ℹ No error — closing resend popup + clearing field")
                    var closeAttempts = 0
                    while (closeAttempts < 3 && PayPalActions.isResendVisible(service)) {
                        closeAttempts++
                        AutomationState.log("  ↳ Close attempt #$closeAttempts")
                        PayPalActions.closeResendPopup(service)
                        StepUtils.sleepMs(800)
                    }

                    // 🟢 If still visible, force with BACK press
                    if (PayPalActions.isResendVisible(service)) {
                        AutomationState.log("  ↳ Popup stuck! Forcing BACK press")
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        StepUtils.sleepMs(1000)
                    }

                    PayPalActions.clearNumberField(service)
                    StepUtils.sleepMs(500)
                }

                PayPalActions.Result.ERROR -> {
                    onNumberFail(num); fail++
                    consecutiveFails++
                    PayPalActions.closeResendPopup(service)
                    PayPalActions.clearNumberField(service)
                    StepUtils.sleepMs(200)
                }

                PayPalActions.Result.PASSWORD_SCREEN -> {
                    AutomationState.log("🔒 Password screen detected for $num")
                    if (PayPalActions.handlePasswordScreen(service)) {
                        onNumberSuccess(num); success++
                        consecutiveFails = 0
                    } else {
                        onNumberFail(num); fail++
                        consecutiveFails++
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        StepUtils.sleepMs(800)
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        StepUtils.sleepMs(800)
                    }
                }

                PayPalActions.Result.STUCK -> {
                    AutomationState.log("⏳ STUCK detected on input screen! Aborting round for Reset.")
                    onNumberFail(num); fail++
                    consecutiveFails++
                    break
                }

                PayPalActions.Result.TIMEOUT -> {
                    onNumberFail(num); fail++
                    consecutiveFails++
                    PayPalActions.closeResendPopup(service)
                    PayPalActions.clearNumberField(service)
                    StepUtils.sleepMs(200)
                }

                else -> {
                    onNumberFail(num); fail++
                    consecutiveFails++
                }
            }
        }

        PayPalActions.closePayPal(service)
        StepUtils.sleepMs(500)

        return Pair(success, fail)
    }
}
