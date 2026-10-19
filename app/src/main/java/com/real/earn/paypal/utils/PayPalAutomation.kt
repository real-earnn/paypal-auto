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
            if (!ok) {
                AutomationState.log("✘ [ABORT] Flight")
                return Pair(0, numbers.size)
            }
        }

        val clearOk = StepUtils.retryStep("Clear data") {
            PayPalClearData.strictClear(service, "com.paypal.android.p2pmobile")
        }
        if (!clearOk) {
            AutomationState.log("✘ [ABORT] Clear data")
            return Pair(0, numbers.size)
        }
        StepUtils.sleepMs(800)

        val launchOk = StepUtils.retryStep("Launch PayPal") {
            PayPalActions.launch(service)
        }
        if (!launchOk) {
            AutomationState.log("✘ [ABORT] Launch")
            return Pair(0, numbers.size)
        }

        if (!PayPalActions.tapGetStarted(service, 20000)) {
            AutomationState.log("✘ [ABORT] Get Started not found after 20s")
            PayPalActions.closePayPal(service)
            return Pair(0, numbers.size)
        }

        AutomationState.log("ℹ Allowing time for Gmail popup cycle...")
        StepUtils.sleepMs(3000)
        PayPalActions.waitForGmailPopupToClose(service, 10000)

        if (!PayPalActions.clickUsButton(service, 25000)) {
            AutomationState.log("✘ [ABORT] US button not found")
            PayPalActions.closePayPal(service)
            return Pair(0, numbers.size)
        }

        StepUtils.sleepMs(2000)
        PayPalActions.waitForGmailPopupToClose(service, 5000)

        if (!StepUtils.retryStep("Country select") {
                PayPalActions.typeAndSelectCountry(service, countryName, countryIso)
            }) {
            AutomationState.log("✘ [ABORT] Country select failed")
            PayPalActions.closePayPal(service)
            return Pair(0, numbers.size)
        }
        AutomationState.log("✔ Country: $countryIso")

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

            // 🟢 FIX: Ensure we're on the number input screen before typing
            val preRoot = service.rootInActiveWindow
            if (preRoot != null) {
                val isPasswordScreen = NodeFinder.containsAny(preRoot, "Password", "More Options", "login")
                if (isPasswordScreen) {
                    AutomationState.log("  ⚠ On password screen, not typing. Pressing Back twice.")
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    StepUtils.sleepMs(800)
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                    StepUtils.sleepMs(800)
                    continue
                }
            }

            if (!StepUtils.retryStep("Number type") {
                    PayPalActions.typeNumber(service, num, dialingCode)
                }) {
                AutomationState.log("✘ Number type fail")
                onNumberFail(num); fail++
                consecutiveFails++
                continue
            }

            if (!StepUtils.retryStep("Continue") {
                    PayPalActions.tapContinue(service)
                }) {
                AutomationState.log("✘ Continue fail")
                onNumberFail(num); fail++
                consecutiveFails++
                continue
            }
            AutomationState.log("ℹ Continue tapped — waiting result…")

            var finalResult = PayPalActions.Result.TIMEOUT
            var captchaRetries = 0

            while (captchaRetries < 6) {
                if (StepUtils.shouldStop()) break
                val r = PayPalActions.checkResult(service, 15000)
                if (r == PayPalActions.Result.RETRY_CONTINUE) {
                    captchaRetries++
                    StepUtils.sleepMs(1000)
                    if (!PayPalActions.tapContinue(service)) {
                        StepUtils.sleepMs(2000)
                        continue
                    }
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
                        AutomationState.log("✘ Red error after resend")
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

                    if (PayPalActions.isResendVisible(service)) {
                        AutomationState.log("  ↳ Popup stuck! Forcing BACK press")
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        StepUtils.sleepMs(1000)
                    }

                    PayPalActions.clearNumberField(service)
                    StepUtils.sleepMs(500)
                }

                PayPalActions.Result.ERROR -> {
                    AutomationState.log("✘ Red error on Continue")
                    onNumberFail(num); fail++
                    consecutiveFails++
                    PayPalActions.closeResendPopup(service)
                    PayPalActions.clearNumberField(service)
                    StepUtils.sleepMs(200)
                }

                PayPalActions.Result.PASSWORD_SCREEN -> {
                    AutomationState.log("🔒 Password screen detected")
                    if (PayPalActions.handlePasswordScreen(service)) {
                        AutomationState.log("✔ Password flow completed")
                        onNumberSuccess(num); success++
                        consecutiveFails = 0
                    } else {
                        AutomationState.log("✘ Password flow failed")
                        onNumberFail(num); fail++
                        consecutiveFails++
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        StepUtils.sleepMs(800)
                        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                        StepUtils.sleepMs(800)
                    }
                }

                PayPalActions.Result.STUCK -> {
                    AutomationState.log("⏳ STUCK detected — aborting round for reset")
                    onNumberFail(num); fail++
                    consecutiveFails++
                    break
                }

                PayPalActions.Result.TIMEOUT -> {
                    AutomationState.log("⏱ Timeout")
                    onNumberFail(num); fail++
                    consecutiveFails++
                    PayPalActions.closeResendPopup(service)
                    PayPalActions.clearNumberField(service)
                    StepUtils.sleepMs(200)
                }

                else -> {
                    AutomationState.log("⏱ Unexpected result")
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
