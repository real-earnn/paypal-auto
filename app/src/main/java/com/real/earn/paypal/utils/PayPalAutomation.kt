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

            // ② Clear data
            val clearOk = PayPalClearData.strictClear(service)
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

            // ④ Get Started
            PayPalActions.tapGetStarted(service, 12000)

            // ⑤ US button
            val usOk = PayPalActions.clickUsButton(service)
            if (!usOk) {
                AutomationState.log("✘ [ABORT] US button")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }

            // ⑥ Country select
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

            // ⑦ Number type
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
            AutomationState.log("ℹ Continue tapped — waiting for captcha/next screen…")

            // ══════════════════════════════════════════════════════════════════
            // ⑨ 🟢 UNIFIED WAIT: wait for captcha loading OR OTP screen OR error
            //    - Up to 30s initial wait (captcha loading takes time)
            //    - If captcha found → wait for solve (up to 120s)
            //    - If OTP screen appears → SUCCESS
            //    - If error → FAIL
            // ══════════════════════════════════════════════════════════════════
            val outcome = waitForOutcome(service)

            when (outcome) {
                Outcome.OTP_SCREEN -> {
                    AutomationState.log("✔ SUCCESS (OTP screen)")
                    PayPalActions.tapResendTwice(service)
                    onNumberSuccess(num)
                    success++
                }
                Outcome.ERROR -> {
                    AutomationState.log("✘ Red error — FAIL")
                    onNumberFail(num); fail++
                }
                Outcome.TIMEOUT -> {
                    AutomationState.log("⏱ Timeout — FAIL")
                    onNumberFail(num); fail++
                }
            }

            PayPalActions.closePayPal(service)
            delay(800)
        }

        return Pair(success, fail)
    }

    // ══════════════════════════════════════════════════════
    //  OUTCOME HANDLER
    // ══════════════════════════════════════════════════════
    private enum class Outcome { OTP_SCREEN, ERROR, TIMEOUT }

    private suspend fun waitForOutcome(service: AccessibilityService): Outcome {
        // ── Phase 1: wait up to 30s for either captcha OR result ──
        val phase1End = System.currentTimeMillis() + 30_000
        var captchaSeen = false
        var captchaWaitStarted = 0L

        while (System.currentTimeMillis() < phase1End) {
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) return Outcome.TIMEOUT

            StepUtils.sleepMs(600)

            val root = service.rootInActiveWindow ?: continue

            // ── Result signals first (fast path) ──
            if (NodeFinder.containsAny(root, "Resend Code", "Resend code", "Resend")) {
                return Outcome.OTP_SCREEN
            }
            if (NodeFinder.containsAny(root,
                    "Something went wrong", "can't be used", "not valid",
                    "invalid", "Enter a valid", "try again", "Try again")) {
                return Outcome.ERROR
            }

            // ── Captcha detection ──
            val isCaptcha = NodeFinder.containsAny(root,
                "I am human", "Security Challenge", "hCaptcha",
                "Verify you are human", "Verification", "Choose the", "Select all")

            if (isCaptcha) {
                if (!captchaSeen) {
                    AutomationState.log("🛡️ Captcha appearing — waiting for solve…")
                    captchaSeen = true
                    captchaWaitStarted = System.currentTimeMillis()
                }
            }

            // If captcha detected and it's gone now → solved
            if (captchaSeen && !isCaptcha) {
                AutomationState.log("✔ Captcha solved — checking result…")
                StepUtils.sleepMs(2500)
                // After captcha, wait up to 15s for result
                return checkResultWithWait(service, 15000)
            }

            // If captcha still visible after 120s → give up
            if (captchaSeen && System.currentTimeMillis() - captchaWaitStarted > 120_000) {
                AutomationState.log("⚠ Captcha wait exceeded 120s")
                return Outcome.TIMEOUT
            }
        }

        // ── Phase 2: 30s passed, no result yet — check once more ──
        if (captchaSeen) {
            // Still waiting on captcha
            AutomationState.log("⚠ Timeout waiting for captcha solve")
            return Outcome.TIMEOUT
        }

        // No captcha and no result → try a final result check
        AutomationState.log("⚠ No screen signal in 30s — final check")
        return checkResultWithWait(service, 5000)
    }

    private suspend fun checkResultWithWait(service: AccessibilityService, timeoutMs: Long): Outcome {
        val end = System.currentTimeMillis() + timeoutMs
        var errorFlag = false

        while (System.currentTimeMillis() < end) {
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) return Outcome.TIMEOUT

            StepUtils.sleepMs(500)
            val root = service.rootInActiveWindow ?: continue

            if (NodeFinder.containsAny(root, "Resend Code", "Resend code", "Resend")) {
                return Outcome.OTP_SCREEN
            }
            if (!errorFlag && NodeFinder.containsAny(root,
                    "Something went wrong", "can't be used", "not valid",
                    "invalid", "Enter a valid", "try again", "Try again")) {
                errorFlag = true
            }
        }

        return if (errorFlag) Outcome.ERROR else Outcome.TIMEOUT
    }
}
