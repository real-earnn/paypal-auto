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
import java.util.regex.Pattern

object TikTokActions {

    val TIKTOK_PKGS = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    fun getInstalledPackage(service: AccessibilityService): String {
        for (pkg in TIKTOK_PKGS) {
            if (service.packageManager.getLaunchIntentForPackage(pkg) != null) return pkg
        }
        return TIKTOK_PKGS[0]
    }

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
            StepUtils.sleepMs(7000) // Wait for app to load
            true
        } catch (e: Exception) {
            AutomationState.log("✘ TikTok Launch: ${e.message}")
            false
        }
    }

    // ══════════════════════════════════════════════════════
    //  🟢 SMART BRAIN: Handles Random Startup Screens
    // ══════════════════════════════════════════════════════
    fun handleStartupSequence(service: AccessibilityService): Boolean {
        AutomationState.log("ℹ Handling TikTok startup sequence…")
        val startTime = System.currentTimeMillis()
        val timeout = 60000L // Max 60 seconds to reach login screen

        while (System.currentTimeMillis() - startTime < timeout) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            
            if (root == null) {
                StepUtils.sleepMs(1000)
                continue
            }

            // ═══ 1. SUCCESS: Phone Number Screen? ═══
            if (findCountryCodeNode(root) != null || NodeFinder.findByTextContains(root, "Phone number") != null) {
                AutomationState.log("✔ Reached Phone Number screen")
                return true
            }

            // ═══ 2. "Use phone / email / username" Button? ═══
            if (clickNode(service, root, "Use phone / email / username", "Use phone")) {
                StepUtils.sleepMs(2000)
                continue
            }

            // ═══ 3. Profile Page (Log in / Add account)? ═══
            if (clickNode(service, root, "Log in", "Add another account", "Add account")) {
                StepUtils.sleepMs(2000)
                continue
            }

            // ═══ 4. Home Feed? (Click "Profile" tab) ═══
            val profileTab = findBottomTab(root, "Profile")
            if (profileTab != null) {
                AutomationState.log("  ↳ Clicking Profile tab")
                tapNode(service, profileTab)
                StepUtils.sleepMs(2000)
                continue
            }

            // ═══ 5. Tutorial Overlay? ("Swipe up") ═══
            if (NodeFinder.findByTextContains(root, "Swipe up") != null) {
                AutomationState.log("  ↳ Swiping up to dismiss overlay")
                swipeUp(service)
                StepUtils.sleepMs(1500)
                continue
            }

            // ═══ 6. Terms & Conditions ═══
            if (clickNode(service, root, "Agree and continue", "Agree", "Accept", "I Agree", "Continue")) {
                StepUtils.sleepMs(1500)
                continue
            }

            // ═══ 7. Permissions ═══
            if (clickNode(service, root, "Allow", "While using the app", "OK", "Allow all")) {
                StepUtils.sleepMs(1500)
                continue
            }

            // ═══ 8. Gender Selection ═══
            if (clickNode(service, root, "Man", "Woman", "Non-binary", "Prefer not to say")) {
                StepUtils.sleepMs(1500)
                continue
            }

            // ═══ 9. Interests (Skip) ═══
            if (clickNode(service, root, "Skip")) {
                StepUtils.sleepMs(1500)
                continue
            }

            // ═══ 10. Next Button (in onboarding) ═══
            if (clickNode(service, root, "Next")) {
                StepUtils.sleepMs(1500)
                continue
            }

            // Agar upar ka kuch bhi nahi mila, toh 1 second wait karke dobara dekho
            StepUtils.sleepMs(1000)
        }
        
        AutomationState.log("⚠ Startup sequence timed out")
        return false
    }

    // ══════════════════════════════════════════════════════
    //  🟢 SMART: Country Code Detection (e.g., "US +1", "PK +92")
    // ══════════════════════════════════════════════════════
    fun selectCountry(service: AccessibilityService, countryName: String): Boolean {
        AutomationState.log("ℹ Selecting country: $countryName")
        
        val root = service.rootInActiveWindow ?: return false
        
        // Find the country code button
        val countryBtn = findCountryCodeNode(root)
        
        if (countryBtn == null) {
            AutomationState.log("✘ Country button not found")
            return false
        }
        
        AutomationState.log("✔ Clicking Country selector")
        tapNode(service, countryBtn)
        StepUtils.sleepMs(1500)
        
        // Search Country
        val searchRoot = service.rootInActiveWindow
        val searchField = NodeFinder.findByTextContains(searchRoot, "Search countries")
            ?: NodeFinder.findEditText(searchRoot)
            
        if (searchField != null) {
            AutomationState.log("ℹ Typing country in search field")
            tapNode(service, searchField)
            StepUtils.sleepMs(500)
            pasteText(service, countryName, "country_search")
            StepUtils.sleepMs(2000)
            
            // Click the result
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

    /**
     * 🟢 Jugaad Logic: Find any node containing "+" and digits (e.g., "PK +92")
     */
    private fun findCountryCodeNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val text = n.text?.toString()?.trim() ?: ""
            val desc = n.contentDescription?.toString()?.trim() ?: ""
            
            // Text must contain "+" and at least one digit. Length < 15 to avoid phone numbers.
            if ((text.contains("+") || desc.contains("+")) && 
                (text.any { it.isDigit() } || desc.any { it.isDigit() }) &&
                text.length < 15 && desc.length < 15 && n.isVisibleToUser) {
                return n
            }

            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    // Find the bottom tab exactly (Home, Friends, Profile)
    private fun findBottomTab(root: AccessibilityNodeInfo, tabName: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val text = n.text?.toString()?.trim() ?: ""
            val desc = n.contentDescription?.toString()?.trim() ?: ""
            
            if ((text.equals(tabName, true) || desc.equals(tabName, true)) && n.isVisibleToUser) {
                // Ensure it's at the bottom of the screen
                val r = Rect(); n.getBoundsInScreen(r)
                val rootR = Rect(); root.getBoundsInScreen(rootR)
                if (r.centerY() > rootR.height() * 0.8) {
                    return n
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    fun typeNumberAndContinue(service: AccessibilityService, number: String, dialingCode: String): Boolean {
        var national = number.trim().removePrefix("+")
        if (national.startsWith(dialingCode)) {
            national = national.substring(dialingCode.length)
        }
        AutomationState.log("ℹ Entering number: $national")

        val root = service.rootInActiveWindow
        val inputField = NodeFinder.findByTextContains(root, "Phone number") 
            ?: NodeFinder.findEditText(root)
            
        if (inputField == null) {
            AutomationState.log("✘ Input field not found")
            return false
        }

        tapNode(service, inputField)
        StepUtils.sleepMs(500)
        
        // Clear existing & Set new
        val setArgs = Bundle()
        setArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, national)
        inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
        StepUtils.sleepMs(1000)

        // Click Continue
        val continueBtn = NodeFinder.findByText(service.rootInActiveWindow, "Continue")
        if (continueBtn != null) {
            AutomationState.log("ℹ Clicking Continue")
            tapNode(service, continueBtn)
            StepUtils.sleepMs(3000)
            return true
        }
        
        AutomationState.log("✘ Continue button not found")
        return false
    }

    fun pressBackToLogin(service: AccessibilityService) {
        AutomationState.log("ℹ Pressing Back to return to input")
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        StepUtils.sleepMs(1500)
    }

    fun hasRedError(service: AccessibilityService): Boolean {
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

    // ══════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════
    
    // Helper: Checks multiple text options and clicks the first one found
    private fun clickNode(service: AccessibilityService, root: AccessibilityNodeInfo, vararg texts: String): Boolean {
        for (text in texts) {
            val node = NodeFinder.findByText(root, text) ?: NodeFinder.findByTextContains(root, text)
            if (node != null && node.isVisibleToUser) {
                AutomationState.log("  ↳ Clicking: '$text'")
                tapNode(service, node)
                return true
            }
        }
        return false
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

    private fun swipeUp(service: AccessibilityService) {
        try {
            val root = service.rootInActiveWindow ?: return
            val bounds = Rect(); root.getBoundsInScreen(bounds)
            val x = bounds.centerX()
            val startY = bounds.height() * 3 / 4
            val endY = bounds.height() / 4

            val path = Path().apply {
                moveTo(x.toFloat(), startY.toFloat())
                lineTo(x.toFloat(), endY.toFloat())
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, 300)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            service.dispatchGesture(gesture, null, null)
            StepUtils.sleepMs(1000)
        } catch (_: Exception) {}
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
