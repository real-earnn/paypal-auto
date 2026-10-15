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
            AutomationState.log("ℹ Continue tapped")

            // ⑨ Captcha handling — wait for human to solve if puzzle
            val ok = handleCaptchaIfAny(service)
            if (!ok) {
                AutomationState.log("✘ [ABORT] Captcha not solved")
                onNumberFail(num); fail++
                PayPalActions.closePayPal(service)
                continue
            }

            // ⑩ Result
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

    // ══════════════════════════════════════════════════════
    //  CAPTCHA HANDLING
    //  - Simple checkbox → watcher clicks, we wait
    //  - Picture puzzle → alarm rings, WAIT for human (up to 120s)
    // ══════════════════════════════════════════════════════
    private suspend fun handleCaptchaIfAny(service: AccessibilityService): Boolean {
        StepUtils.sleepMs(1500)

        val root = service.rootInActiveWindow ?: return true

        val hasCaptcha = NodeFinder.containsAny(
            root,
            "I am human", "Security Challenge", "hCaptcha", "Verify you are human"
        )
        if (!hasCaptcha) {
            AutomationState.log("ℹ No captcha present")
            return true
        }

        AutomationState.log("🛡️ Captcha detected — waiting for solve…")

        // Wait up to 120 seconds for solve (either by watcher or human)
        val end = System.currentTimeMillis() + 120_000
        var lastLogAt = 0L
        var puzzleAnnounced = false

        while (System.currentTimeMillis() < end) {
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) {
                return false
            }

            StepUtils.sleepMs(800)

            val cur = service.rootInActiveWindow ?: continue

            // ── Puzzle active → log once ──
            if (CaptchaWatcher.picturePuzzleDetected && !puzzleAnnounced) {
                AutomationState.log("🛡️ Picture puzzle ON — waiting for HUMAN…")
                puzzleAnnounced = true
            }

            // ── Captcha still present? ──
            val still = NodeFinder.containsAny(
                cur,
                "I am human", "Security Challenge", "hCaptcha", "Verify you are human"
            )

            if (!still) {
                AutomationState.log("✔ Captcha solved — continuing")
                StepUtils.sleepMs(2500)   // let next screen load
                return true
            }

            // Progress log every 10s
            val elapsed = System.currentTimeMillis() - (end - 120_000)
            if (elapsed - lastLogAt > 10000) {
                val remaining = ((end - System.currentTimeMillis()) / 1000).toInt()
                if (puzzleAnnounced) {
                    AutomationState.log("🛡️ Still waiting for HUMAN to solve… (${remaining}s left)")
                } else {
                    AutomationState.log("🛡️ Still waiting for auto-solve… (${remaining}s left)")
                }
                lastLogAt = elapsed
            }
        }

        AutomationState.log("⚠ Captcha wait timed out (120s)")
        return false
    }
}
