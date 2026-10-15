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

            // ══════════════════════════════════════════════════════
            // ⑨ CAPTCHA HANDLING (after Continue)
            //    - Wait to see if hCaptcha appears
            //    - Let CaptchaWatcher solve "I am human"
            //    - If picture puzzle appears → SKIP this number
            //    - If solved → continue to result check
            // ══════════════════════════════════════════════════════
            val captchaResult = handleCaptchaIfAny(service)

            when (captchaResult) {
                CaptchaResult.PICTURE_PUZZLE -> {
                    AutomationState.log("✘ [SKIP] Picture puzzle captcha — releasing number")
                    onNumberFail(num); fail++
                    PayPalActions.closePayPal(service)
                    delay(800)
                    continue
                }
                CaptchaResult.SOLVED -> {
                    AutomationState.log("✔ Captcha solved — continuing")
                    // Fall through to result check
                }
                CaptchaResult.NOT_PRESENT -> {
                    // No captcha, proceed directly
                }
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
    //  CAPTCHA DETECTION + WAIT
    // ══════════════════════════════════════════════════════
    private enum class CaptchaResult { NOT_PRESENT, SOLVED, PICTURE_PUZZLE }

    private suspend fun handleCaptchaIfAny(service: AccessibilityService): CaptchaResult {
        // 1. Give PayPal a moment to render the captcha screen
        StepUtils.sleepMs(1500)

        // 2. Check if captcha appeared
        val root = service.rootInActiveWindow
        if (root == null) return CaptchaResult.NOT_PRESENT

        val hasCaptcha = NodeFinder.containsAny(
            root,
            "I am human",
            "Security Challenge",
            "hCaptcha",
            "Verify you are human",
            "Verification"
        )
        if (!hasCaptcha) return CaptchaResult.NOT_PRESENT

        AutomationState.log("🛡️ Captcha detected after Continue — waiting for solve…")

        // 3. Wait up to 15 seconds for CaptchaWatcher to solve
        val end = System.currentTimeMillis() + 15000
        var firstDetectTime = System.currentTimeMillis()

        while (System.currentTimeMillis() < end) {
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) {
                return CaptchaResult.NOT_PRESENT
            }

            StepUtils.sleepMs(700)

            val currentRoot = service.rootInActiveWindow
            if (currentRoot == null) continue

            // ── Check: Picture puzzle appeared? ──
            if (isPicturePuzzle(currentRoot)) {
                AutomationState.log("🛡️ Picture puzzle detected")
                // Give 2 seconds in case checkbox auto-pass
                StepUtils.sleepMs(2000)
                val recheck = service.rootInActiveWindow
                if (recheck != null && !isPicturePuzzle(recheck)) {
                    // Auto-cleared — treat as solved
                    AutomationState.log("✔ Puzzle auto-cleared")
                    return CaptchaResult.SOLVED
                }
                return CaptchaResult.PICTURE_PUZZLE
            }

            // ── Check: Captcha still present? ──
            val stillCaptcha = NodeFinder.containsAny(
                currentRoot,
                "I am human",
                "Security Challenge",
                "hCaptcha"
            )
            if (!stillCaptcha) {
                // Captcha gone — we're on the next screen
                AutomationState.log("✔ Captcha gone — moving on")
                // Extra settle for next screen to load
                StepUtils.sleepMs(2500)
                return CaptchaResult.SOLVED
            }

            // Log progress every 3s
            val elapsed = System.currentTimeMillis() - firstDetectTime
            if (elapsed > 5000 && elapsed < 5300) {
                AutomationState.log("🛡️ Still waiting for captcha solve…")
            }
        }

        // Timeout — couldn't solve
        AutomationState.log("⚠ Captcha timeout (15s)")
        return CaptchaResult.SOLVED   // give it a chance, let result check decide
    }

    /** Detect picture-puzzle captcha (drag-drop, image grid, rotate) */
    private fun isPicturePuzzle(root: android.view.accessibility.AccessibilityNodeInfo): Boolean {
        // Strong indicators of picture-based hCaptcha
        val pictureMarkers = arrayOf(
            "Drag the", "Select all", "Choose the",
            "Please click", "Please drag", "Please select",
            "Skip", "Refresh", "Audio",
            "Challenge", "Click each"
        )
        return NodeFinder.containsAny(root, *pictureMarkers)
    }
}
