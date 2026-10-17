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
            StepUtils.sleepMs(7000)
            true
        } catch (e: Exception) {
            AutomationState.log("✘ TikTok Launch: ${e.message}")
            false
        }
    }

    fun handleStartupSequence(service: AccessibilityService): Boolean {
        AutomationState.log("ℹ Handling TikTok startup sequence…")
        val startTime = System.currentTimeMillis()
        val timeout = 60000L

        while (System.currentTimeMillis() - startTime < timeout) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root == null) { StepUtils.sleepMs(1000); continue }

            if (findCountryCodeNode(root) != null || NodeFinder.findByTextContains(root, "Phone number") != null) {
                AutomationState.log("✔ Reached Phone Number screen")
                return true
            }

            if (clickNode(service, root, "Use phone / email / username", "Use phone")) { StepUtils.sleepMs(2000); continue }
            if (clickNode(service, root, "Log in", "Add another account", "Add account")) { StepUtils.sleepMs(2000); continue }
            
            val profileTab = findBottomTab(root, "Profile")
            if (profileTab != null) { tapNode(service, profileTab); StepUtils.sleepMs(2000); continue }

            if (NodeFinder.findByTextContains(root, "Swipe up") != null) { swipeUp(service); StepUtils.sleepMs(1500); continue }
            if (clickNode(service, root, "Agree and continue", "Agree", "Accept", "I Agree")) { StepUtils.sleepMs(1500); continue }
            if (clickNode(service, root, "Allow", "While using the app", "OK", "Allow all")) { StepUtils.sleepMs(1500); continue }
            if (clickNode(service, root, "Man", "Woman", "Non-binary")) { StepUtils.sleepMs(1500); continue }
            if (clickNode(service, root, "Skip")) { StepUtils.sleepMs(1500); continue }

            StepUtils.sleepMs(1000)
        }
        AutomationState.log("⚠ Startup sequence timed out")
        return false
    }

    /**
     * 🟢 Country name clean karo: "Norway12" -> "Norway"
     */
    private fun cleanCountryName(raw: String): String {
        return raw.replace(Regex("[0-9]+(-[0-9]+)*$"), "").trim()
    }

    fun selectCountry(service: AccessibilityService, rawCountryName: String): Boolean {
        val countryName = cleanCountryName(rawCountryName) // 🟢 Clean here
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
            
            val resRoot = service.rootInActiveWindow
            val result = NodeFinder.findByTextContains(resRoot, countryName)
            if (result != null) {
                AutomationState.log("✔ Selecting '$countryName' from list")
                tapNode(service, result)
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

        val root = service.rootInActiveWindow
        val inputField = NodeFinder.findByTextContains(root, "Phone number") ?: NodeFinder.findEditText(root)
            
        if (inputField == null) {
            AutomationState.log("✘ Input field not found")
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
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        StepUtils.sleepMs(1500)
    }

    fun hasRedError(service: AccessibilityService): Boolean {
        val root = service.rootInActiveWindow ?: return false
        return NodeFinder.containsAny(root, "Try a different number", "Invalid", "not registered", "Too many attempts", "Try again later")
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
