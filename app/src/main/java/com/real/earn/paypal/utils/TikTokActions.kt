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
            StepUtils.sleepMs(8000) // App load hone ke liye zyada time diya
            true
        } catch (e: Exception) {
            AutomationState.log("✘ TikTok Launch: ${e.message}")
            false
        }
    }

    // ══════════════════════════════════════════════════════
    //  🟢 SMART BRAIN: Handles Startup + Swipe Logic
    // ══════════════════════════════════════════════════════
    fun handleStartupSequence(service: AccessibilityService): Boolean {
        AutomationState.log("ℹ Handling TikTok startup sequence…")
        val startTime = System.currentTimeMillis()
        val timeout = 60000L
        var swipeAttempts = 0

        while (System.currentTimeMillis() - startTime < timeout) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            
            if (root == null) {
                StepUtils.sleepMs(1000)
                continue
            }

            // 1. SUCCESS: Phone Number Screen?
            if (findCountryCodeNode(root) != null || NodeFinder.findByTextContains(root, "Phone number") != null) {
                AutomationState.log("✔ Reached Phone Number screen")
                return true
            }

            // 2. Terms, Permissions, Gender, Interests
            if (clickNode(service, root, "Use phone / email / username", "Use phone")) { StepUtils.sleepMs(2000); continue }
            if (clickNode(service, root, "Agree and continue", "Agree", "Accept", "I Agree")) { StepUtils.sleepMs(1500); continue }
            if (clickNode(service, root, "Allow", "While using the app", "OK", "Allow all")) { StepUtils.sleepMs(1500); continue }
            if (clickNode(service, root, "Man", "Woman", "Non-binary", "Prefer not to say")) { StepUtils.sleepMs(1500); continue }
            if (clickNode(service, root, "Skip")) { StepUtils.sleepMs(1500); continue }

            // 3. Profile Page (Log in / Add account)
            if (clickNode(service, root, "Log in", "Add another account", "Add account")) {
                StepUtils.sleepMs(2000)
                continue
            }

            // 4. 🟢 SWIPE LOGIC: "Swipe up for more"
            if (NodeFinder.findByTextContains(root, "Swipe up") != null) {
                if (swipeAttempts < 3) {
                    AutomationState.log("  ↳ 'Swipe up' visible — Swiping (#${swipeAttempts + 1})")
                    swipeUp(service)
                    swipeAttempts++
                    // 🟢 Wait 5 seconds for video to load and overlay to disappear
                    StepUtils.sleepMs(5000)
                    continue
                } else {
                    AutomationState.log("  ⚠ 'Swipe up' still visible after 3 attempts")
                }
            }

            // 5. 🟢 PROFILE TAB: Bottom tab pe click karo
            val profileTab = findBottomTab(root, "Profile")
            if (profileTab != null) {
                AutomationState.log("  ↳ Profile tab visible — Clicking")
                tapNode(service, profileTab)
                StepUtils.sleepMs(3000)
                continue
            }

            StepUtils.sleepMs(1000)
        }
        
        AutomationState.log("⚠ Startup sequence timed out")
        return false
    }

    private fun cleanCountryName(raw: String): String {
        return raw.replace(Regex("[0-9]+(-[0-9]+)*$"), "").trim()
    }

    fun selectCountry(service: AccessibilityService, rawCountryName: String): Boolean {
        val countryName = cleanCountryName(rawCountryName)
        AutomationState.log("ℹ Selecting country: $countryName")
        
        val root = service.rootInActiveWindow ?: return false
        val countryBtn = findCountryCodeNode(root)
        
        if (countryBtn == null) {
            AutomationState.log("✘ Country button not found")
            return false
        }
        
        tapNode(service, countryBtn)
        StepUtils.sleepMs(1500)
        
        val searchRoot = service.rootInActiveWindow
        val searchField = NodeFinder.findByTextContains(searchRoot, "Search countries") ?: NodeFinder.findEditText(searchRoot)
            
        if (searchField != null) {
            AutomationState.log("ℹ Typing country in search field: $countryName")
            tapNode(service, searchField)
            StepUtils.sleepMs(500)
            pasteText(service, countryName, "country_search")
            StepUtils.sleepMs(2000)
            
            if (clickCountryResult(service, countryName)) {
                AutomationState.log("✔ Selected '$countryName' from list")
                StepUtils.sleepMs(2000)
                return true
            } else {
                AutomationState.log("✘ Search result for '$countryName' not found")
            }
        } else {
            AutomationState.log("✘ Search field not found")
        }
        return false
    }

    private fun clickCountryResult(service: AccessibilityService, countryName: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isEditable) continue
            
            val text = n.text?.toString() ?: ""
            if (text.contains(countryName, ignoreCase = true)) {
                val clickable = NodeFinder.findClickableParent(n) ?: n
                if (clickable.isClickable) {
                    return tapNode(service, clickable)
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    private fun findCountryCodeNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val text = n.text?.toString()?.trim() ?: ""
            val desc = n.contentDescription?.toString()?.trim() ?: ""
            if ((text.contains("+") || desc.contains("+")) && (text.any { it.isDigit() } || desc.any { it.isDigit() }) && text.length < 15 && desc.length < 15 && n.isVisibleToUser) {
                return n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun findBottomTab(root: AccessibilityNodeInfo, tabName: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val text = n.text?.toString()?.trim() ?: ""
            val desc = n.contentDescription?.toString()?.trim() ?: ""
            if ((text.equals(tabName, true) || desc.equals(tabName, true)) && n.isVisibleToUser) {
                val r = Rect(); n.getBoundsInScreen(r)
                val rootR = Rect(); root.getBoundsInScreen(rootR)
                if (r.centerY() > rootR.height() * 0.8) return n
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    fun typeNumberAndContinue(service: AccessibilityService, number: String, dialingCode: String): Boolean {
        var national = number.trim().removePrefix("+")
        if (national.startsWith(dialingCode)) national = national.substring(dialingCode.length)
        AutomationState.log("ℹ Entering number: $national")

        var inputField: AccessibilityNodeInfo? = null
        val end = SystemClock.uptimeMillis() + 8000
        
        while (SystemClock.uptimeMillis() < end) {
            val root = service.rootInActiveWindow
            inputField = NodeFinder.findByTextContains(root, "Phone number")
            
            if (inputField == null) {
                val codeNode = findCountryCodeNode(root ?: break)
                if (codeNode != null) {
                    inputField = NodeFinder.findEditText(root)
                }
            }
            if (inputField != null) break
            StepUtils.sleepMs(500)
        }

        if (inputField == null) {
            AutomationState.log("✘ Phone number input field not found")
            return false
        }

        tapNode(service, inputField)
        StepUtils.sleepMs(500)
        
        val setArgs = Bundle()
        setArgs.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, national)
        inputField.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setArgs)
        StepUtils.sleepMs(1000)

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
        
        for (i in 0 until 3) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            StepUtils.sleepMs(1000)
            
            val root = service.rootInActiveWindow
            if (root == null) continue

            if (findCountryCodeNode(root) != null || NodeFinder.findByTextContains(root, "Phone number") != null) {
                AutomationState.log("✔ Reached Phone Number screen")
                return
            }
            
            if (NodeFinder.findByTextContains(root, "6-digit code") != null || NodeFinder.findByTextContains(root, "Enter 6-digit") != null) {
                AutomationState.log("  ↳ OTP screen detected — pressing Back again")
                continue
            }
        }
    }

    fun hasRedError(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return NodeFinder.containsAny(root, "Try a different number", "Invalid", "not registered", "Too many attempts", "Try again later")
    }

    fun hasFatalError(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return NodeFinder.containsAny(root, "Too many attempts", "Maximum attempts", "Try again later", "blocked")
    }

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
            StepUtils.sleepMs(1000) // Thoda internal wait
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
