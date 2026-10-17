package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.delay

object TikTokOldAccountAutomation {

    suspend fun runBatch(
        service: AccessibilityService,
        numbers: List<String>,
        onNumberStart: suspend (String) -> Unit,
        onNumberSuccess: suspend (String) -> Unit,
        onNumberFail: suspend (String) -> Unit
    ): Pair<Int, Int> {

        var success = 0
        var fail = 0
        var consecutiveErrors = 0

        AutomationState.log("🎵 TikTok Old Account Method Started")
        AutomationState.log("ℹ Waiting for user to Resume on Phone Input Screen...")

        // Wait until user hits Resume
        while (AutomationState.getStatus() == AutomationState.Status.PAUSED) {
            delay(500)
        }
        if (AutomationState.getStatus() == AutomationState.Status.STOPPING) return Pair(0, 0)

        var i = 0
        while (i < numbers.size) {
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
            while (AutomationState.getStatus() == AutomationState.Status.PAUSED) delay(300)
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break

            if (consecutiveErrors >= 5) {
                AutomationState.log("✘ 5 consecutive Red Errors reached. Stopping.")
                break
            }

            val num = numbers[i]
            onNumberStart(num)
            AutomationState.log("─────────────")
            AutomationState.log("▶ Typing: $num")

            // ═══ STEP 1: Verify we are on phone input screen ═══
            val root = service.rootInActiveWindow
            if (root == null || !isOnPhoneInputScreen(root)) {
                AutomationState.log("⚠ Not on Phone Number screen!")
                showToast(service, "No number field visible!\nPlease go to Phone Number screen & tap Resume")
                AutomationState.setStatus(AutomationState.Status.PAUSED)
                // Do NOT increment i, do NOT count as fail — retry same number
                continue
            }

            // ═══ STEP 2: Type Number ═══
            if (!typeNumber(service, num)) {
                if (AutomationState.getStatus() == AutomationState.Status.PAUSED) {
                    // It got auto-paused, retry
                    continue
                }
                AutomationState.log("✘ Failed to type number")
                onNumberFail(num); fail++
                i++
                continue
            }

            // ═══ STEP 3: Click Continue ═══
            if (!clickContinue(service)) {
                AutomationState.log("✘ Failed to click Continue")
                onNumberFail(num); fail++
                i++
                continue
            }

            // ═══ STEP 4: Wait for TikTok response ═══
            StepUtils.sleepMs(3500)

            // ═══ STEP 5: Check result ═══
            if (hasRedError(service)) {
                AutomationState.log("✘ Red error detected! (#${consecutiveErrors + 1}/5)")
                onNumberFail(num); fail++
                consecutiveErrors++
            } else {
                AutomationState.log("✔ No Red Error (Likely OTP Screen)")
                onNumberSuccess(num); success++
                consecutiveErrors = 0
            }

            // ═══ STEP 6: Smart Back to input screen ═══
            pressBackToInput(service)
            StepUtils.sleepMs(800)

            i++
        }

        AutomationState.log("⏹ Batch Finished. Success: $success, Fail: $fail")
        return Pair(success, fail)
    }

    // ══════════════════════════════════════════════════════
    //  🟢 SCREEN DETECTORS
    // ══════════════════════════════════════════════════════
    private fun isOnOtpScreen(root: AccessibilityNodeInfo): Boolean {
        return NodeFinder.containsAny(
            root,
            "6-digit code",
            "6 digit code",
            "Enter 6-digit",
            "Enter the 6",
            "verification code",
            "Verification code",
            "Resend code",
            "Resend"
        )
    }

    private fun isOnPhoneInputScreen(root: AccessibilityNodeInfo): Boolean {
        // If we're on OTP screen, definitely not on phone input
        if (isOnOtpScreen(root)) return false

        // Look for phone number label or editable field
        return NodeFinder.findByTextContains(root, "Phone number") != null ||
               NodeFinder.findByTextContains(root, "phone number") != null ||
               NodeFinder.findByTextContains(root, "Enter phone") != null ||
               NodeFinder.findByTextContains(root, "Phone") != null ||
               NodeFinder.findEditText(root) != null
    }

    // ══════════════════════════════════════════════════════
    //  🟢 TYPE NUMBER (with safe check)
    // ══════════════════════════════════════════════════════
    private fun typeNumber(service: AccessibilityService, number: String): Boolean {
        val root = service.rootInActiveWindow ?: return false

        // Double-check: OTP screen pe ho to yahan number mat type karo
        if (isOnOtpScreen(root)) {
            AutomationState.log("  ↳ Detected OTP screen — cannot type here")
            return false
        }

        // Find the phone input field
        val inputField = NodeFinder.findByTextContains(root, "Phone number")
            ?: NodeFinder.findByTextContains(root, "phone number")
            ?: NodeFinder.findEditText(root)

        if (inputField == null) {
            AutomationState.log("  ↳ Input field not found")
            return false
        }

        // Tap to focus
        tapNode(service, inputField)
        StepUtils.sleepMs(500)

        // Clear existing text
        val clearArgs = android.os.Bundle()
        clearArgs.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, ""
        )
        inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
        StepUtils.sleepMs(300)

        // Set new text
        val setArgs = android.os.Bundle()
        setArgs.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, number
        )
        val ok = inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)

        if (ok) {
            AutomationState.log("  ↳ Number typed successfully")
            StepUtils.sleepMs(800)
        }
        return ok
    }

    private fun clickContinue(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val continueBtn = NodeFinder.findByText(root, "Continue", "Next")

        if (continueBtn != null && continueBtn.isVisibleToUser) {
            tapNode(service, continueBtn)
            AutomationState.log("  ↳ Continue tapped")
            return true
        }
        AutomationState.log("  ↳ Continue button not found")
        return false
    }

    // ══════════════════════════════════════════════════════
    //  🟢 SMART BACK: Loops until Phone Input Screen reached
    // ══════════════════════════════════════════════════════
    private fun pressBackToInput(service: AccessibilityService) {
        AutomationState.log("  ↳ Smart Back to Phone Input screen...")

        for (attempt in 0 until 5) {
            val root = service.rootInActiveWindow
            if (root == null) {
                // Screen unreadable, back press anyway
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                StepUtils.sleepMs(1200)
                continue
            }

            val onOtp = isOnOtpScreen(root)
            val onInput = isOnPhoneInputScreen(root)

            AutomationState.log("    attempt #${attempt + 1}: OTP=$onOtp, Input=$onInput")

            if (onInput && !onOtp) {
                // Reached phone input, clear the field
                val edit = NodeFinder.findEditText(root)
                if (edit != null) {
                    val clearArgs = android.os.Bundle()
                    clearArgs.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, ""
                    )
                    edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
                    AutomationState.log("    ↳ Phone input reached, field cleared")
                }
                return
            }

            // Need to press Back
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(1200)
        }

        AutomationState.log("  ⚠ Could not reach Phone Input after 5 Backs")
    }

    // ══════════════════════════════════════════════════════
    //  🟢 HELPERS
    // ══════════════════════════════════════════════════════
    private fun hasRedError(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return NodeFinder.containsAny(
            root,
            "Try a different number",
            "Invalid",
            "not registered",
            "Too many attempts",
            "Try again later"
        )
    }

    private fun showToast(service: AccessibilityService, msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(service.applicationContext, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun tapNode(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val parent = NodeFinder.findClickableParent(node)
        if (parent != null && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true

        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            val path = android.graphics.Path().apply {
                moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat())
            }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 60)
            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
            return true
        }
        return false
    }
}
