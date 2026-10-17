package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

object TikTokActions {

    // TikTok Global & Asia packages
    private val TIKTOK_PKGS = listOf(
        "com.zhiliaoapp.musically", // Global
        "com.ss.android.ugc.trill"  // Asia
    )

    fun launch(service: AccessibilityService): Boolean {
        return try {
            var intent: Intent? = null
            for (pkg in TIKTOK_PKGS) {
                intent = service.packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) break
            }
            if (intent == null) return false
            
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            service.startActivity(intent)
            StepUtils.sleepMs(6000) // TikTok takes time to load
            true
        } catch (e: Exception) {
            AutomationState.log("✘ TikTok Launch: ${e.message}")
            false
        }
    }

    fun tapUsePhoneOrEmail(service: AccessibilityService, timeoutMs: Long = 15000): Boolean {
        AutomationState.log("ℹ Searching 'Use phone / email / username'…")
        val end = SystemClock.uptimeMillis() + timeoutMs
        
        while (SystemClock.uptimeMillis() < end) {
            val root = service.rootInActiveWindow
            if (root != null) {
                val node = NodeFinder.findByTextContains(root, "Use phone")
                    ?: NodeFinder.findByTextContains(root, "Log in")
                    ?: NodeFinder.findByTextContains(root, "Sign up")
                
                if (node != null) {
                    AutomationState.log("✔ Clicking Login option")
                    tapNode(service, node)
                    StepUtils.sleepMs(2000)
                    return true
                }
            }
            StepUtils.sleepMs(500)
        }
        return false
    }

    fun selectCountry(service: AccessibilityService, countryName: String): Boolean {
        AutomationState.log("ℹ Selecting country: $countryName")
        
        // 1. Click the country code button (e.g., "US +1")
        val root = service.rootInActiveWindow ?: return false
        val usButton = NodeFinder.findByTextContains(root, "US") 
            ?: NodeFinder.findByTextContains(root, "+1")
        
        if (usButton == null) {
            AutomationState.log("✘ Country button not found")
            return false
        }
        
        AutomationState.log("✔ Clicking Country selector")
        tapNode(service, usButton)
        StepUtils.sleepMs(1500)
        
        // 2. Search Country
        val searchRoot = service.rootInActiveWindow
        val searchField = NodeFinder.findByTextContains(searchRoot, "Search countries")
            ?: NodeFinder.findEditText(searchRoot)
            
        if (searchField != null) {
            AutomationState.log("ℹ Typing country in search field")
            tapNode(service, searchField)
            StepUtils.sleepMs(500)
            pasteText(service, countryName, "country_search")
            StepUtils.sleepMs(2000)
            
            // 3. Click the result
            val resRoot = service.rootInActiveWindow
            val result = NodeFinder.findByTextContains(resRoot, countryName)
            if (result != null) {
                AutomationState.log("✔ Selecting '$countryName' from list")
                tapNode(service, result)
                StepUtils.sleepMs(1500)
                return true
            }
        }
        
        AutomationState.log("✘ Country search failed")
        return false
    }

    fun typeNumberAndContinue(service: AccessibilityService, number: String, dialingCode: String): Boolean {
        var national = number.trim().removePrefix("+")
        if (national.startsWith(dialingCode)) {
            national = national.substring(dialingCode.length)
        }
        AutomationState.log("ℹ Entering number: $national")

        // 1. Type Number
        val root = service.rootInActiveWindow
        val inputField = NodeFinder.findByTextContains(root, "Phone number") 
            ?: NodeFinder.findEditText(root)
            
        if (inputField == null) {
            AutomationState.log("✘ Input field not found")
            return false
        }

        tapNode(service, inputField)
        StepUtils.sleepMs(500)
        
        // Clear existing
        val setArgs = Bundle()
        setArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, national)
        inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
        StepUtils.sleepMs(1000)

        // 2. Click Continue
        val continueBtn = NodeFinder.findByText(service.rootInActiveWindow, "Continue")
        if (continueBtn != null) {
            AutomationState.log("ℹ Clicking Continue")
            tapNode(service, continueBtn)
            StepUtils.sleepMs(3000) // Wait for response
            return true
        }
        
        AutomationState.log("✘ Continue button not found")
        return false
    }

    fun pressBackToLogin(service: AccessibilityService) {
        // Used to go back to number input screen for the next number
        AutomationState.log("ℹ Pressing Back to return to input")
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        StepUtils.sleepMs(1500)
    }

    fun hasRedError(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        // TikTok shows red text below the field on invalid number
        return NodeFinder.containsAny(
            root,
            "Try a different number",
            "Invalid",
            "not registered",
            "Too many attempts",
            "Try again later"
        )
    }

    private fun tapNode(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val parent = NodeFinder.findClickableParent(node)
        if (parent != null && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val bounds = Rect(); node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) return tapAt(service, bounds.centerX(), bounds.centerY())
        return false
    }

    private fun tapAt(service: AccessibilityService, x: Int, y: Int): Boolean {
        return try {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 60)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val latch = java.util.concurrent.CountDownLatch(1)
            var ok = false
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { ok = true; latch.countDown() }
                override fun onCancelled(d: GestureDescription?) { ok = false; latch.countDown() }
            }, null)
            latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
            ok
        } catch (e: Exception) { false }
    }

    private fun pasteText(service: AccessibilityService, text: String, label: String) {
        try {
            val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText(label, text))
        } catch (_: Exception) {}
        StepUtils.sleepMs(200)
        val root = service.rootInActiveWindow
        val edit = NodeFinder.findEditText(root)
        if (edit != null) {
            edit.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            if (edit.performAction(AccessibilityNodeInfo.ACTION_PASTE)) return
            val args = Bundle()
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        }
    }
}
