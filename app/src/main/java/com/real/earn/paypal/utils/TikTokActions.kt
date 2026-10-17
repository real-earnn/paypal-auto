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
    //  🟢 SMART: Handle Startup Popups & Onboarding
    // ══════════════════════════════════════════════════════
    fun handleStartupSequence(service: AccessibilityService): Boolean {
        AutomationState.log("ℹ Handling TikTok startup sequence…")
        
        for (i in 1..15) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            
            if (root != null) {
                // 1. Terms & Conditions
                if (clickIfFound(service, root, "Agree and continue", "Agree", "Accept")) continue
                
                // 2. Permissions
                if (clickIfFound(service, root, "Allow", "While using the app", "OK")) continue
                
                // 3. Gender
                if (clickIfFound(service, root, "Man", "Woman", "Non-binary")) continue
                
                // 4. Interests (Skip)
                if (clickIfFound(service, root, "Skip")) continue
                
                // 5. Home Feed -> Swipe up to dismiss overlay
                if (findByTextContains(root, "Swipe up for more") != null) {
                    swipeUp(service)
                    continue
                }

                // 6. Already logged in / Home feed -> Go to Profile
                if (findByTextContains(root, "Home") != null && findByTextContains(root, "Profile") != null) {
                    AutomationState.log("✔ On Home feed — opening Profile")
                    val profileTab = findByText(root, "Profile")
                    if (profileTab != null) {
                        tapNode(service, profileTab)
                        StepUtils.sleepMs(2000)
                        continue
                    }
                }

                // 7. Profile Page -> Log in / Add account
                if (clickIfFound(service, root, "Log in", "Add another account")) {
                    StepUtils.sleepMs(1500)
                    continue
                }

                // 8. Use phone / email / username
                if (clickIfFound(service, root, "Use phone / email / username", "Use phone")) {
                    StepUtils.sleepMs(2000)
                    return true // Successfully reached login screen
                }

                // 9. If we are already on the "Log in" screen with country code, we are done
                if (findByTextContains(root, "Phone number") != null || findCountryCodeNode(root) != null) {
                    AutomationState.log("✔ Reached Phone Number screen")
                    return true
                }
            }
            StepUtils.sleepMs(1000)
        }
        
        AutomationState.log("⚠ Startup sequence timed out")
        return false
    }

    private fun clickIfFound(service: AccessibilityService, root: AccessibilityNodeInfo, vararg texts: String): Boolean {
        val node = NodeFinder.findByText(root, *texts)
        if (node != null && node.isVisibleToUser) {
            AutomationState.log("  ↳ Clicking: ${node.text}")
            tapNode(service, node)
            StepUtils.sleepMs(1500)
            return true
        }
        return false
    }

    // ══════════════════════════════════════════════════════
    //  🟢 SMART: Country Code Detection (e.g., "US +1")
    // ══════════════════════════════════════════════════════
    fun selectCountry(service: AccessibilityService, countryName: String): Boolean {
        AutomationState.log("ℹ Selecting country: $countryName")
        
        val root = service.rootInActiveWindow ?: return false
        
        // Find the country code button (e.g., "PK +92", "US +1", "NO +47")
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
     * 🟢 Jugaad Logic: Find text matching "ABC +123" or containing "+"
     */
    private fun findCountryCodeNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val text = n.text?.toString()?.trim() ?: ""
            
            // Check if text contains "+" and a number
            if (text.contains("+") && text.any { it.isDigit() }) {
                // Ensure it's not a long phone number
                if (text.length < 15 && n.isVisibleToUser) {
                    return n
                }
            }
            
            // Also check for content description (sometimes used by TikTok)
            val desc = n.contentDescription?.toString()?.trim() ?: ""
            if (desc.contains("+") && desc.any { it.isDigit() } && desc.length < 15 && n.isVisibleToUser) {
                return n
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
    
    // FindByText helper for exact match
    private fun findByText(root: AccessibilityNodeInfo?, target: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            if (t.equals(target, ignoreCase = true) && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun findByTextContains(root: AccessibilityNodeInfo?, needle: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val lower = needle.lowercase()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.lowercase() ?: ""
            if (t.contains(lower) && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }
}
