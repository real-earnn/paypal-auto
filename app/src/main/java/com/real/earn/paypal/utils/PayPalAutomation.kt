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

        val clearOk = StepUtils.retryStep("Clear data") {
            PayPalClearData.strictClear(service, "com.paypal.android.p2pmobile")
        }
        if (!clearOk) return Pair(0, numbers.size)
        StepUtils.sleepMs(800)

        val launchOk = StepUtils.retryStep("Launch PayPal") { PayPalActions.launch(service) }
        if (!launchOk) return Pair(0, numbers.size)

        if (!PayPalActions.tapGetStarted(service, 20000)) {
            AutomationState.log("✘ [ABORT] Get Started not found")
            PayPalActions.closePayPal(service)
            return Pair(0, numbers.size)
        }

        AutomationState.log("ℹ Allowing Gmail popup cycle...")
        StepUtils.sleepMs(3000)
        PayPalActions.waitForGmailPopupToClose(service, 10000)

        if (!PayPalActions.clickUsButton(service, 25000)) {
            AutomationState.log("✘ [ABORT] US button")
            PayPalActions.closePayPal(service)
            return Pair(0, numbers.size)
        }

        StepUtils.sleepMs(2000)
        PayPalActions.waitForGmailPopupToClose(service, 5000)

        if (!StepUtils.retryStep("Country select") {
                PayPalActions.typeAndSelectCountry(service, countryName, countryIso)
            }) {
            AutomationState.log("✘ [ABORT] Country select")
            PayPalActions.closePayPal(service)
            return Pair(0, numbers.size)
        }

        var consecutiveFails = 0

        for (num in numbers) {
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
            while (AutomationState.getStatus() == AutomationState.Status.PAUSED) delay(300)
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
            if (consecutiveFails >= 3) break

            onNumberStart(num)
            AutomationState.log("─────────────")
            AutomationState.log("▶ $num")

            // 🟢 CRITICAL: Verify we're on number input before typing (not password/OTP)
            val preRoot = service.rootInActiveWindow
            if (preRoot != null) {
                val isWrongScreen = NodeFinder.containsAny(preRoot, "Password", "More Options", "Resend Code")
                if (isWrongScreen) {
                    AutomationState.log("  ⚠ Wrong screen detected — back to number")
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    StepUtils.sleepMs(1200)
                    continue
                }
            }

            if (!StepUtils.retryStep("Number type") {
                    PayPalActions.typeNumber(service, num, dialingCode)
                }) {
                onNumberFail(num); fail++
                consecutiveFails++
                continue
            }

            if (!StepUtils.retryStep("Continue") {
                    PayPalActions.tapContinue(service)
                }) {
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
                    onNumberSuccess(num); success++
                    consecutiveFails = 0
                    AutomationState.log("✔ OTP — SUCCESS: $num")

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

                    var closeAttempts = 0
                    while (closeAttempts < 3 && PayPalActions.isResendVisible(service)) {
                        closeAttempts++
                        AutomationState.log("  ↳ Close attempt #$closeAttempts")
                        PayPalActions.closeResendPopup(service)
                        StepUtils.sleepMs(800)
                    }
                    PayPalActions.clearNumberField(service)
                    StepUtils.sleepMs(500)
                }

                PayPalActions.Result.ERROR -> {
                    onNumberFail(num); fail++
                    consecutiveFails++
                    AutomationState.log("✘ Red error")
                    PayPalActions.closeResendPopup(service)
                    PayPalActions.clearNumberField(service)
                    StepUtils.sleepMs(200)
                }

                PayPalActions.Result.PASSWORD_SCREEN -> {
                    AutomationState.log("🔒 Password screen")
                    if (PayPalActions.handlePasswordScreen(service)) {
                        onNumberSuccess(num); success++
                        consecutiveFails = 0
                    } else {
                        onNumberFail(num); fail++
                        consecutiveFails++
                        // 🟢 Force reset signal from PayPalActions
                        if (PayPalActions.passwordFlowNeedsReset) {
                            AutomationState.log("🔄 FORCE RESET triggered by Password flow")
                            break // abort round, AutomationService will reset
                        }
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        StepUtils.sleepMs(800)
                    }
                }

                PayPalActions.Result.STUCK -> {
                    onNumberFail(num); fail++
                    consecutiveFails++
                    AutomationState.log("⏳ STUCK — aborting for reset")
                    break
                }

                PayPalActions.Result.TIMEOUT -> {
                    onNumberFail(num); fail++
                    consecutiveFails++
                    AutomationState.log("⏱ Timeout")
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
