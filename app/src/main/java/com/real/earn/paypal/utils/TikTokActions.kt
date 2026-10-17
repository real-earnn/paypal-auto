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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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
            StepUtils.sleepMs(8000) 
            true
        } catch (e: Exception) {
            AutomationState.log("✘ TikTok Launch: ${e.message}")
            false
        }
    }

    fun handleStartupSequence(service: AccessibilityService): Boolean {
        AutomationState.log("ℹ Handling TikTok startup sequence…")
        val startTime = System.currentTimeMillis()
        val timeout = 90000L 
        var swipeAttempts = 0
        var waitedForVideo = false 

        while (System.currentTimeMillis() - startTime < timeout) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            
            if (root == null) {
                StepUtils.sleepMs(1000)
                continue
            }

            if (findCountryCodeNode(root) != null || NodeFinder.findByTextContains(root, "Phone number") != null) {
                AutomationState.log("✔ Reached Phone Number screen")
                return true
            }

            if (clickNode(service, root, "Use phone / email / username", "Use phone")) { StepUtils.sleepMs(2000); continue }
            if (clickNode(service, root, "Agree and continue", "Agree", "Accept", "I Agree")) { StepUtils.sleepMs(1500); continue }
            if (clickNode(service, root, "Allow", "While using the app", "OK", "Allow all")) { StepUtils.sleepMs(1500); continue }
            if (clickNode(service, root, "Man", "Woman", "Non-binary", "Prefer not to say")) { StepUtils.sleepMs(1500); continue }
            if (clickNode(service, root, "Skip")) { StepUtils.sleepMs(1500); continue }

            if (clickNode(service, root, "Log in", "Add another account", "Add account")) {
                StepUtils.sleepMs(2000)
                continue
            }

            val isVideoFeed = NodeFinder.findByTextContains(root, "Swipe up") != null || 
                              findStrictBottomTab(root, "Home") != null

            if (isVideoFeed) {
                if (!waitedForVideo) {
                    AutomationState.log("  ↳ Video feed detected — waiting 5 seconds...")
                    StepUtils.sleepMs(5000) 
                    waitedForVideo = true
                    // 🟢 DEBUG: Print all screen texts when video loads
                    printScreenTexts(root)
                    continue
                }

                val profileTab = findStrictBottomTab(root, "Profile")

                if (profileTab != null) {
                    val r = Rect(); profileTab.getBoundsInScreen(r)
                    AutomationState.log("  ↳ Clicking Profile Tab at (${r.centerX()}, ${r.centerY()})")
                    tapNode(service, profileTab)
                    StepUtils.sleepMs(3000)
                    waitedForVideo = false 
                    continue
                } else {
                    AutomationState.log("  ↳ Profile tab not found — Swiping up (#${swipeAttempts + 1})")
                    swipeUp(service)
                    StepUtils.sleepMs(3000)
                    swipeAttempts++
                    waitedForVideo = false 
                    
                    if (swipeAttempts >= 6) {
                        AutomationState.log("✘ Too many swipe attempts.")
                        break
                    }
                }
                continue
            }

            StepUtils.sleepMs(1000)
        }
        
        AutomationState.log("⚠ Startup sequence timed out")
        return false
    }

    // 🟢 DEBUG HELPER: Logs all texts and their coordinates on the current screen
    private fun printScreenTexts(root: AccessibilityNodeInfo) {
        val textList = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val t = n.text?.toString()?.trim() ?: ""
            val d = n.contentDescription?.toString()?.trim() ?: ""
            
            if (t.isNotEmpty() || d.isNotEmpty()) {
                val r = Rect(); n.getBoundsInScreen(r)
                // Only log visible items
                if (r.width() > 0 && r.height() > 0 && n.isVisibleToUser) {
                    textList.add("[Y:${r.centerY()}] '${t.ifEmpty { d }}'")
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        // Log in chunks to avoid console overflow
        val chunkSize = 5
        for (i in textList.indices step chunkSize) {
            val chunk = textList.subList(i, minOf(i + chunkSize, textList.size)).joinToString(" | ")
            AutomationState.log("  [SCREEN] $chunk")
        }
    }

    private fun findStrictBottomTab(root: AccessibilityNodeInfo, tabName: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val rootR = Rect(); root.getBoundsInScreen(rootR)
        val strictBottomThreshold = rootR.height() * 0.85 // Thora aur neeche laya hai (85%)

        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val text = n.text?.toString()?.trim() ?: ""
            val desc = n.contentDescription?.toString()?.trim() ?: ""
            
            if ((text.equals(tabName, true) || desc.equals(tabName, true)) && n.isVisibleToUser) {
                val r = Rect(); n.getBoundsInScreen(r)
                // 🟢 DEBUG: Log if we found "Profile" but it was too high
                if (r.centerY() <= strictBottomThreshold) {
                    AutomationState.log("  [DEBUG] Ignored '$tabName' at Y:${r.centerY()} (Too high)")
                }
                if (r.centerY() > strictBottomThreshold) {
                    return n
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
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
                val r = Rect(); node.getBoundsInScreen(r)
                AutomationState.log("  ↳ Clicking '$text' at (${r.centerX()}, ${r.centerY()})")
                tapNode(service, node)
                return true
            }
        }
        return false
    }

    // 🟢 FIXED: Explicitly log which method is clicking and coordinates
    private fun tapNode(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        val textDesc = node.text?.toString() ?: node.contentDescription?.toString() ?: "Unknown"
        
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            AutomationState.log("    ↳ ACTION_CLICK success on '$textDesc'")
            return true
        }
        val parent = NodeFinder.findClickableParent(node)
        if (parent != null && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            AutomationState.log("    ↳ Parent ACTION_CLICK success on '$textDesc'")
            return true
        }
        
        val bounds = Rect(); node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            AutomationState.log("    ↳ Coordinate tap at (${bounds.centerX()}, ${bounds.centerY()}) for '$textDesc'")
            return tapAt(service, bounds.centerX(), bounds.centerY())
        }
        return false
    }

    private fun tapAt(service: AccessibilityService, x: Int, y: Int): Boolean {
        return try {
            val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 60)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val latch = CountDownLatch(1)
            var ok = false
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { ok = true; latch.countDown() }
                override fun onCancelled(d: GestureDescription?) { ok = false; latch.countDown() }
            }, null)
            latch.await(2, TimeUnit.SECONDS)
            ok
        } catch (e: Exception) { false }
    }

    // ══════════════════════════════════════════════════════
    //  🟢 FIXED: Swipe from 60% to 20% (Middle 40% only)
    // ══════════════════════════════════════════════════════
    private fun swipeUp(service: AccessibilityService) {
        try {
            val root = service.rootInActiveWindow ?: return
            val bounds = Rect(); root.getBoundsInScreen(bounds)
            val x = bounds.centerX()
            
            // 🟢 Start at 60% (Middle screen)
            val startY = bounds.height() * 0.60f
            // 🟢 End at 20% (Below top tabs)
            val endY = bounds.height() * 0.20f

            AutomationState.log("  ↳ Swiping from Y:${startY.toInt()} to Y:${endY.toInt()}")

            val path = Path().apply {
                moveTo(x.toFloat(), startY)
                lineTo(x.toFloat(), endY)
            }
            
            val stroke = GestureDescription.StrokeDescription(path, 0, 500) 
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            
            val latch = CountDownLatch(1)
            var ok = false
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { ok = true; latch.countDown() }
                override fun onCancelled(d: GestureDescription?) { ok = false; latch.countDown() }
            }, null)
            latch.await(2, TimeUnit.SECONDS)
            
            if (ok) {
                AutomationState.log("  ↳ Swipe completed successfully")
                StepUtils.sleepMs(1000)
            } else {
                AutomationState.log("  ↳ Swipe gesture cancelled")
            }
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
