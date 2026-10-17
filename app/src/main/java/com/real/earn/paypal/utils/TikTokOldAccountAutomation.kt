package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
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
        AutomationState.log("ℹ Waiting for user to Resume on Input Screen...")

        // Wait until user hits Resume (Status becomes RUNNING)
        while (AutomationState.getStatus() == AutomationState.Status.PAUSED) {
            delay(500)
        }
        if (AutomationState.getStatus() == AutomationState.Status.STOPPING) return Pair(0, 0)

        for (num in numbers) {
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) break
            while (AutomationState.getStatus() == AutomationState.Status.PAUSED) delay(300)

            // Check 5 Consecutive Errors Limit
            if (consecutiveErrors >= 5) {
                AutomationState.log("✘ 5 consecutive Red Errors reached. Stopping.")
                break
            }

            onNumberStart(num)
            AutomationState.log("─────────────")
            AutomationState.log("▶ Typing: $num")

            // 1. Type Number
            if (!typeNumber(service, num)) {
                AutomationState.log("✘ Failed to type number")
                onNumberFail(num); fail++
                continue
            }

            // 2. Click Continue
            if (!clickContinue(service)) {
                AutomationState.log("✘ Failed to click Continue")
                onNumberFail(num); fail++
                continue
            }

            // 3. Wait 3 seconds for TikTok to process
            StepUtils.sleepMs(3500)

            // 4. Check for Red Error
            if (hasRedError(service)) {
                AutomationState.log("✘ Red error detected! (Error #${consecutiveErrors + 1}/5)")
                onNumberFail(num); fail++
                consecutiveErrors++
                
                // Back press to clear error/keyboard, get ready for next number
                pressBackToInput(service)
            } else {
                AutomationState.log("✔ No Red Error detected (Likely OTP Screen)")
                onNumberSuccess(num); success++
                consecutiveErrors = 0 // Reset error count on success
                
                // If no error, we are on OTP screen. Back out.
                pressBackToInput(service)
            }

            StepUtils.sleepMs(1000) // Short breather before next number
        }

        AutomationState.log("⏹ Batch Finished. Success: $success, Fail: $fail")
        return Pair(success, fail)
    }

    // ══════════════════════════════════════════════════════
    //  🟢 SIMPLE LOGIC: Only Type and Continue
    // ══════════════════════════════════════════════════════
    private fun typeNumber(service: AccessibilityService, number: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        
        // Find the Input Field
        val inputField = NodeFinder.findByTextContains(root, "Phone number") 
            ?: NodeFinder.findEditText(root)
            
        if (inputField == null) {
            AutomationState.log("  ↳ Input field not found")
            return false
        }

        // Tap it
        tapNode(service, inputField)
        StepUtils.sleepMs(500)

        // Clear existing text
        val clearArgs = android.os.Bundle()
        clearArgs.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        inputField.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
        StepUtils.sleepMs(300)

        // Set new text
        val setArgs = android.os.Bundle()
        setArgs.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, number)
        val success = inputField.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
        
        if (success) {
            AutomationState.log("  ↳ Number typed successfully")
            StepUtils.sleepMs(800)
        }
        return success
    }

    private fun clickContinue(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val continueBtn = NodeFinder.findByText(root, "Continue")
        
        if (continueBtn != null && continueBtn.isVisibleToUser) {
            tapNode(service, continueBtn)
            AutomationState.log("  ↳ Continue tapped")
            return true
        }
        AutomationState.log("  ↳ Continue button not found")
        return false
    }

    // ══════════════════════════════════════════════════════
    //  🟢 SMART BACK: Clears field and returns to input
    // ══════════════════════════════════════════════════════
    private fun pressBackToInput(service: AccessibilityService) {
        AutomationState.log("  ↳ Pressing Back...")
        
        // First back press (closes keyboard or OTP screen)
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        StepUtils.sleepMs(1000)

        // Check if we are on the input screen
        var root = service.rootInActiveWindow
        var isInputScreen = root != null && (NodeFinder.findByTextContains(root, "Phone number") != null || NodeFinder.findEditText(root) != null)

        if (!isInputScreen) {
            AutomationState.log("  ↳ Not on input screen, pressing Back again...")
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(1000)
            
            root = service.rootInActiveWindow
            isInputScreen = root != null && (NodeFinder.findByTextContains(root, "Phone number") != null || NodeFinder.findEditText(root) != null)
        }

        // Final safety clear
        if (isInputScreen && root != null) {
            val edit = NodeFinder.findEditText(root)
            if (edit != null) {
                val clearArgs = android.os.Bundle()
                clearArgs.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                edit.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, clearArgs)
            }
        }
    }

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

    private fun tapNode(service: AccessibilityService, node: android.view.accessibility.AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true
        val parent = NodeFinder.findClickableParent(node)
        if (parent != null && parent.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)) return true
        
        val bounds = android.graphics.Rect(); node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            val path = android.graphics.Path().apply { moveTo(bounds.centerX().toFloat(), bounds.centerY().toFloat()) }
            val stroke = android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 60)
            val gesture = android.accessibilityservice.GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
            return true
        }
        return false
    }
}
