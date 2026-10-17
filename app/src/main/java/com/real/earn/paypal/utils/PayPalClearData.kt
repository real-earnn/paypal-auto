package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

object PayPalClearData {

    // 🟢 Ab targetPackage parameter accept karega
    fun strictClear(service: AccessibilityService, targetPackage: String): Boolean {
        try {
            AutomationState.log("🧹 Clear data for $targetPackage…")

            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(900)

            for (attempt in 1..3) {
                if (StepUtils.shouldStop()) return false

                if (attempt > 1) {
                    AutomationState.log("ℹ App Info retry #$attempt")
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    StepUtils.sleepMs(1000)
                }

                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$targetPackage")
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                        )
                    }
                    service.startActivity(intent)
                } catch (_: Exception) {}

                var appInfoReady = waitForAppInfoContent(service, 6000)

                if (!appInfoReady) {
                    AutomationState.log("  ⚠ Intent didn't open foreground — trying Recents fallback")
                    appInfoReady = tryRecentsFallback(service)
                }

                if (!appInfoReady) {
                    AutomationState.log("  ⚠ App Info not detected — retrying")
                    continue
                }

                AutomationState.log("✔ App Info ready (attempt $attempt)")
                StepUtils.sleepMs(700)

                if (tryClearViaStorage(service)) {
                    AutomationState.log("✔ Clear data done")
                    exitHome(service)
                    return true
                }

                AutomationState.log("  ⚠ Clear attempt failed — retrying")
            }

            AutomationState.log("✘ Clear data failed after 3 attempts")
            exitHome(service)
            return false
        } catch (e: Exception) {
            AutomationState.log("✘ Clear error: ${e.message}")
            exitHome(service)
            return false
        }
    }

    private fun tryRecentsFallback(service: AccessibilityService): Boolean {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        StepUtils.sleepMs(1800)

        val root = service.rootInActiveWindow
        if (root != null) {
            val settingsNode = NodeFinder.findByText(root, "Settings", "سیٹنگز")
                ?: NodeFinder.findByTextContains(root, "Settings")
                ?: findByContentDescContains(root, "Settings")

            if (settingsNode != null) {
                AutomationState.log("  ✔ Found Settings in Recents — clicking")
                val r = Rect(); settingsNode.getBoundsInScreen(r)
                if (r.width() > 0 && r.height() > 0) {
                    tapAt(service, r.centerX(), r.centerY())
                    StepUtils.sleepMs(1800)
                    if (waitForAppInfoContent(service, 6000)) {
                        return true
                    }
                }
            }
        }

        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        StepUtils.sleepMs(1000)
        return false
    }

    private fun findByContentDescContains(root: AccessibilityNodeInfo?, needle: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val lower = needle.lowercase()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val d = n.contentDescription?.toString()?.lowercase() ?: ""
            if (d.contains(lower) && n.isVisibleToUser) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun waitForAppInfoContent(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var launcherStreak = 0
        var lastLog = 0L

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            val now = SystemClock.uptimeMillis()

            if (root == null) {
                StepUtils.sleepMs(400); continue
            }

            val pkg = root.packageName?.toString()?.lowercase() ?: ""

            if (pkg.contains("launcher") || pkg.contains("home") ||
                pkg.contains("com.miui.home") || pkg.contains("com.oppo.launcher") ||
                pkg.contains("com.vivo.launcher") || pkg.contains("tw.launcher") ||
                pkg.contains("touchwiz") || pkg.contains("sec.android.app.launcher") ||
                pkg.contains("com.google.android.apps.nexuslauncher")) {
                launcherStreak++
                if (launcherStreak >= 3) return false
                StepUtils.sleepMs(500); continue
            }
            launcherStreak = 0

            val hasContent = NodeFinder.containsAny(
                root,
                "Storage", "Storage & cache", "Force stop", "Uninstall",
                "App info", "App permissions", "Notifications",
                "Permissions", "Battery", "Mobile data", "Data usage",
                "Version", "Size"
            )
            if (hasContent) return true

            if (isScreenLoading(root)) {
                if (now - lastLog > 1500) {
                    AutomationState.log("    ↳ Loading screen — waiting")
                    lastLog = now
                }
                StepUtils.sleepMs(500); continue
            }
            StepUtils.sleepMs(400)
        }
        return false
    }

    private fun isScreenLoading(root: AccessibilityNodeInfo): Boolean {
        var textCount = 0
        var clickableCount = 0
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var depth = 0
        while (queue.isNotEmpty() && depth < 300) {
            val n = queue.removeFirst()
            depth++
            if ((n.text?.toString()?.trim()?.length ?: 0) > 1) textCount++
            if (n.isClickable && n.isVisibleToUser) clickableCount++
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return textCount < 4 && clickableCount < 3
    }

    private fun tryClearViaStorage(service: AccessibilityService): Boolean {
        AutomationState.log("  → Looking for Storage & cache row")
        val storageNode = findStorageRow(service, 5000)
        if (storageNode == null) {
            AutomationState.log("  ✘ Storage row not found")
            return false
        }

        scrollIntoViewIfNeeded(service, storageNode)

        if (!clickNodeRobust(service, storageNode)) {
            AutomationState.log("  ✘ Storage row click failed")
            return false
        }
        AutomationState.log("  ✔ Storage row clicked")
        StepUtils.sleepMs(1500)

        if (!waitForClearDataButton(service, 5000)) {
            AutomationState.log("  ✘ Clear button not found in sub-screen")
            return false
        }

        if (!clickClearDataButton(service, 3000)) {
            AutomationState.log("  ✘ Clear button click failed")
            return false
        }
        StepUtils.sleepMs(1000)

        handleSubMenu(service)
        handleConfirmDialog(service)
        return true
    }

    private fun findStorageRow(service: AccessibilityService, timeoutMs: Long): AccessibilityNodeInfo? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return null
            val root = service.rootInActiveWindow
            if (root == null) { StepUtils.sleepMs(300); continue }

            val variants = arrayOf(
                "Storage & cache", "Storage and cache",
                "Storage & Cache", "Storage and Cache",
                "Storage", "Memory", "Storage & data"
            )
            for (v in variants) {
                val n = NodeFinder.findByText(root, v)
                if (n != null && n.isVisibleToUser) {
                    val r = Rect(); n.getBoundsInScreen(r)
                    if (r.width() > 0 && r.height() > 0 && r.top > 100) {
                        AutomationState.log("  ℹ Row found: '$v'")
                        return n
                    }
                }
            }

            for (v in arrayOf("Storage & cache", "Storage & Cache", "storage & cache")) {
                val n = NodeFinder.findByTextContains(root, v)
                if (n != null && n.isVisibleToUser) {
                    val r = Rect(); n.getBoundsInScreen(r)
                    if (r.width() > 0 && r.height() > 0 && r.top > 100) {
                        AutomationState.log("  ℹ Row found (contains): '$v'")
                        return n
                    }
                }
            }

            val row = findClickableRowBySubtree(root, "storage")
            if (row != null) {
                val r = Rect(); row.getBoundsInScreen(r)
                if (r.width() > 0 && r.height() > 0 && r.top > 100) {
                    AutomationState.log("  ℹ Row found (subtree)")
                    return row
                }
            }
            StepUtils.sleepMs(350)
        }
        return null
    }

    private fun findClickableRowBySubtree(root: AccessibilityNodeInfo, needle: String): AccessibilityNodeInfo? {
        val lower = needle.lowercase()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isClickable && n.isVisibleToUser && subtreeContains(n, lower)) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun subtreeContains(node: AccessibilityNodeInfo, lowerNeedle: String): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)
        var depth = 0
        while (queue.isNotEmpty() && depth < 50) {
            val n = queue.removeFirst()
            depth++
            val t = n.text?.toString()?.lowercase() ?: ""
            val d = n.contentDescription?.toString()?.lowercase() ?: ""
            if (t.contains(lowerNeedle) || d.contains(lowerNeedle)) return true
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return false
    }

    private fun clickNodeRobust(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val parent = NodeFinder.findClickableParent(node)
        if (parent != null && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        val r = Rect(); node.getBoundsInScreen(r)
        if (r.width() > 0 && r.height() > 0) return tapAt(service, r.centerX(), r.centerY())
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

    private fun scrollIntoViewIfNeeded(service: AccessibilityService, node: AccessibilityNodeInfo) {
        val r = Rect(); node.getBoundsInScreen(r)
        val root = service.rootInActiveWindow ?: return
        val rootR = Rect(); root.getBoundsInScreen(rootR)
        val bottomSafeZone = rootR.bottom - 250
        val topSafeZone = rootR.top + 150

        if (r.centerY() > bottomSafeZone) {
            findScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            StepUtils.sleepMs(500)
        } else if (r.centerY() < topSafeZone) {
            findScrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            StepUtils.sleepMs(500)
        }
    }

    private fun findScrollable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isScrollable) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private fun waitForClearDataButton(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                val node = NodeFinder.findByText(root, "Clear storage", "Clear data", "Clear all data", "Delete data")
                    ?: NodeFinder.findByTextContains(root, "Clear storage", "Clear data", "Clear all data")
                if (node != null && node.isVisibleToUser) return true
            }
            StepUtils.sleepMs(400)
        }
        return false
    }

    private fun clickClearDataButton(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                val node = NodeFinder.findByText(root, "Clear storage", "Clear data", "Clear all data", "Delete data")
                    ?: NodeFinder.findByTextContains(root, "Clear storage", "Clear data", "Clear all data")
                if (node != null && node.isVisibleToUser) {
                    if (clickNodeRobust(service, node)) return true
                }
            }
            StepUtils.sleepMs(300)
        }
        return false
    }

    private fun handleSubMenu(service: AccessibilityService) {
        StepUtils.sleepMs(600)
        val root = service.rootInActiveWindow ?: return
        if (!NodeFinder.containsAny(root, "Clear cache", "Clear all data", "Cancel")) return

        AutomationState.log("  ℹ Sub-menu detected")
        val options = arrayOf("Clear all data", "Clear app data", "Clear data", "Delete data")
        for (opt in options) {
            val node = NodeFinder.findByText(root, opt)
            if (node != null && node.isVisibleToUser && clickNodeRobust(service, node)) {
                AutomationState.log("  ✔ Sub-menu: '$opt'")
                StepUtils.sleepMs(1200)
                handleConfirmDialog(service)
                return
            }
        }
    }

    private fun handleConfirmDialog(service: AccessibilityService) {
        StepUtils.sleepMs(800)
        val end = SystemClock.uptimeMillis() + 3500
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return
            val root = service.rootInActiveWindow
            if (root != null) {
                if (NodeFinder.containsAny(root, "Delete all data", "Delete app data", "All app data", "Clear app data", "Clear all data", "Delete all app data")) {
                    val okOptions = arrayOf("OK", "Delete", "Clear", "Yes", "Confirm", "Erase", "Continue")
                    for (opt in okOptions) {
                        val node = NodeFinder.findByText(root, opt)
                        if (node != null && node.isVisibleToUser && clickNodeRobust(service, node)) {
                            AutomationState.log("  ✔ Confirm: '$opt'")
                            StepUtils.sleepMs(2000)
                            return
                        }
                    }
                }
            }
            StepUtils.sleepMs(250)
        }
    }

    private fun exitHome(service: AccessibilityService) {
        try {
            repeat(2) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                StepUtils.sleepMs(300)
            }
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(700)
        } catch (_: Exception) {}
    }
}
