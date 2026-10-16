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
        //  ROUND SETUP — ek dafa (flight + clear + launch + country)
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
        //  INNER LOOP — multiple numbers same session
        //  Exit: red error OR all numbers tried OR stop
        // ══════════════════════════════════════════════════════════════════
        for (num in numbers) {
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
            while (AutomationState.getStatus() == AutomationState.Status.PAUSED) delay(300)
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break

            onNumberStart(num)
            AutomationState.log("─────────────")
            AutomationState.log("▶ $num")

            // ① Type number (typeNumber khud clear karta hai stale text)
            if (!StepUtils.retryStep("Number type") {
                    PayPalActions.typeNumber(service, num, dialingCode)
                }) {
                AutomationState.log("✘ Number type fail")
                onNumberFail(num); fail++
                continue  // 🟢 Next number same session
            }

            // ② Continue
            if (!StepUtils.retryStep("Continue") {
                    PayPalActions.tapContinue(service)
                }) {
                AutomationState.log("✘ Continue fail")
                onNumberFail(num); fail++
                continue  // 🟢 Next number same session
            }
            AutomationState.log("ℹ Continue tapped — waiting result…")

            // ③ Wait for result
            when (PayPalActions.checkResult(service, 10000)) {

                // ─── SUCCESS: OTP screen ───
                PayPalActions.Result.OTP_SCREEN -> {
                    AutomationState.log("✔ OTP screen — SUCCESS: $num")
                    onNumberSuccess(num); success++

                    // Resend 2x (5s each)
                    PayPalActions.tapResendTwice(service)

                    // Red error appeared during/after resend?
                    StepUtils.sleepMs(2000)
                    if (PayPalActions.hasRedError(service)) {
                        AutomationState.log("✘ Red error after resend — ROUND END")
                        break  // 🛑 Exit inner loop → new round
                    }

                    // No error → close popup + clear field, next number
                    AutomationState.log("ℹ No error — closing resend popup + clearing field")
                    PayPalActions.closeResendPopup(service)
                    PayPalActions.clearNumberField(service)
                    StepUtils.sleepMs(500)
                    // fall through to next iteration
                }

                // ─── FAIL: Red error on Continue ───
                PayPalActions.Result.ERROR -> {
                    AutomationState.log("✘ Red error on Continue — ROUND END")
                    onNumberFail(num); fail++
                    break  // 🛑 Exit inner loop → new round
                }

                // ─── TIMEOUT ───
                PayPalActions.Result.TIMEOUT -> {
                    AutomationState.log("⏱ Timeout — treating as fail")
                    onNumberFail(num); fail++
                    // Best effort: close popup + clear field, try next number
                    PayPalActions.closeResendPopup(service)
                    PayPalActions.clearNumberField(service)
                    StepUtils.sleepMs(500)
                    // next iteration
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
