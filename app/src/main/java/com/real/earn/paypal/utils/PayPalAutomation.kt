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

        // ══════════════════════════════════════════════════════════════════
        //  ROUND SETUP
        // ══════════════════════════════════════════════════════════════════
        if (autoFlightMode) {
            val ok = StepUtils.retryStep("Flight cycle") {
                AirplaneModeController.strictToggle(service)
            }
            if (!ok) {
                AutomationState.log("✘ [ABORT] Flight")
                return Pair(0, numbers.size)
            }
        }

        val clearOk = StepUtils.retryStep("Clear data") {
            PayPalClearData.strictClear(service)
        }
        if (!clearOk) {
            AutomationState.log("✘ [ABORT] Clear data")
            return Pair(0, numbers.size)
        }
        StepUtils.sleepMs(800)

        val launchOk = StepUtils.retryStep("Launch") {
            PayPalActions.launch(service)
        }
        if (!launchOk) {
            AutomationState.log("✘ [ABORT] Launch")
            return Pair(0, numbers.size)
        }

        PayPalActions.tapGetStarted(service, 12000)

        if (!PayPalActions.clickUsButton(service)) {
            AutomationState.log("✘ [ABORT] US button")
            PayPalActions.closePayPal(service)
            return Pair(0, numbers.size)
        }

        if (!StepUtils.retryStep("Country select") {
                PayPalActions.typeAndSelectCountry(service, countryName, countryIso)
            }) {
            AutomationState.log("✘ [ABORT] Country select")
            PayPalActions.closePayPal(service)
            return Pair(0, numbers.size)
        }
        AutomationState.log("✔ Country: $countryIso")

        // ══════════════════════════════════════════════════════════════════
        //  INNER LOOP — try next number on error (max 3 consecutive fails)
        // ══════════════════════════════════════════════════════════════════
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

            // ① Type number
            if (!StepUtils.retryStep("Number type") {
                    PayPalActions.typeNumber(service, num, dialingCode)
                }) {
                AutomationState.log("✘ Number type fail")
                onNumberFail(num); fail++
                consecutiveFails++
                continue
            }

            // ② Continue
            if (!StepUtils.retryStep("Continue") {
                    PayPalActions.tapContinue(service)
                }) {
                AutomationState.log("✘ Continue fail")
                onNumberFail(num); fail++
                consecutiveFails++
                continue
            }
            AutomationState.log("ℹ Continue tapped — waiting result…")

            // ③ Wait for result — captcha-aware
            var finalResult = PayPalActions.Result.TIMEOUT
            var captchaRetries = 0

            while (captchaRetries < 6) {
                if (StepUtils.shouldStop()) break

                val r = PayPalActions.checkResult(service, 15000)

                if (r == PayPalActions.Result.RETRY_CONTINUE) {
                    captchaRetries++
                    AutomationState.log("ℹ Captcha solved — Continue again (#$captchaRetries)")
                    StepUtils.sleepMs(1000)

                    if (!PayPalActions.tapContinue(service)) {
                        AutomationState.log("  ⚠ Re-tap Continue failed")
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

                // ─── SUCCESS: OTP screen ───
                PayPalActions.Result.OTP_SCREEN -> {
                    AutomationState.log("✔ OTP screen — SUCCESS: $num")
                    onNumberSuccess(num); success++
                    consecutiveFails = 0 // 🟢 Reset on success

                    // Resend 2x
                    PayPalActions.tapResendTwice(service)

                    StepUtils.sleepMs(2000)
                    if (PayPalActions.hasRedError(service)) {
                        AutomationState.log("✘ Red error after resend — clearing & trying next number")
                        onNumberFail(num); fail++
                        consecutiveFails++
                        PayPalActions.closeResendPopup(service)
                        PayPalActions.clearNumberField(service)
                        StepUtils.sleepMs(500)
                        continue
                    }

                    AutomationState.log("ℹ No error — closing resend popup + clearing field")
                    PayPalActions.closeResendPopup(service)
                    PayPalActions.clearNumberField(service)
                    StepUtils.sleepMs(500)
                }

                // ─── FAIL: Red error on Continue ───
                PayPalActions.Result.ERROR -> {
                    AutomationState.log("✘ Red error on Continue — clearing & trying next number")
                    onNumberFail(num); fail++
                    consecutiveFails++
                    PayPalActions.closeResendPopup(service)
                    PayPalActions.clearNumberField(service)
                    StepUtils.sleepMs(500)
                }

                // ─── TIMEOUT ───
                PayPalActions.Result.TIMEOUT -> {
                    AutomationState.log("⏱ Timeout — clearing & trying next number")
                    onNumberFail(num); fail++
                    consecutiveFails++
                    PayPalActions.closeResendPopup(service)
                    PayPalActions.clearNumberField(service)
                    StepUtils.sleepMs(500)
                }

                else -> {
                    AutomationState.log("⏱ Unexpected result — trying next number")
                    onNumberFail(num); fail++
                    consecutiveFails++
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════
        //  ROUND CLEANUP
        // ══════════════════════════════════════════════════════════════════
        PayPalActions.closePayPal(service)
        StepUtils.sleepMs(500)

        return Pair(success, fail)
    }
}
