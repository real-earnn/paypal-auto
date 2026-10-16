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

    const val PAYPAL_PKG = "com.paypal.android.p2pmobile"

    fun strictClear(service: AccessibilityService): Boolean {
        try {
            AutomationState.log("🧹 Clear data…")

            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(900)

            for (attempt in 1..3) {
                if (StepUtils.shouldStop()) return false

                if (attempt > 1) {
                    AutomationState.log("ℹ App Info retry #$attempt")
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                    StepUtils.sleepMs(1000)
                }

                // ① Fire App Info intent
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$PAYPAL_PKG")
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TASK
                        )
                    }
                    service.startActivity(intent)
                } catch (_: Exception) {}

                // ② Content-based detection
                val appInfoReady = waitForAppInfoContent(service, 6000)
                if (!appInfoReady) {
                    AutomationState.log("  ⚠ App Info content not detected")
                    continue
                }
                AutomationState.log("✔ App Info ready (attempt $attempt)")
                StepUtils.sleepMs(700)

                // ③ Try to click Storage & cache row → then Clear data
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

    // ══════════════════════════════════════════════════════════════════════
    //  App Info content-based detection
    // ══════════════════════════════════════════════════════════════════════
    private fun waitForAppInfoContent(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        StepUtils.sleepMs(700)

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                val hasContent = NodeFinder.containsAny(
                    root,
                    "Storage", "Storage & cache", "Force stop", "Uninstall",
                    "App info", "App permissions", "Notifications",
                    "Permissions", "Battery", "Mobile data", "Data usage"
                )
                if (hasContent) return true
            }
            StepUtils.sleepMs(500)
        }
        return false
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Main flow: Storage & cache → Clear data → Confirm
    // ══════════════════════════════════════════════════════════════════════
    private fun tryClearViaStorage(service: AccessibilityService): Boolean {
        AutomationState.log("  → Looking for Storage & cache row")

        // ① Find the row
        val storageNode = findStorageRow(service, 5000)
        if (storageNode == null) {
            AutomationState.log("  ✘ Storage row not found")
            return false
        }

        // ② Scroll it into safe tap zone if needed (avoid footer overlap)
        scrollIntoViewIfNeeded(service, storageNode)

        // ③ Click robustly
        if (!clickNodeRobust(service, storageNode)) {
            AutomationState.log("  ✘ Storage row click failed")
            return false
        }
        AutomationState.log("  ✔ Storage row clicked")
        StepUtils.sleepMs(1500)

        // ④ Wait for sub-screen: "Clear storage" / "Clear data"
        if (!waitForClearDataButton(service, 5000)) {
            AutomationState.log("  ✘ Clear button not found in sub-screen")
            return false
        }

        // ⑤ Click "Clear storage" / "Clear data" / "Clear all data"
        if (!clickClearDataButton(service, 3000)) {
            AutomationState.log("  ✘ Clear button click failed")
            return false
        }
        StepUtils.sleepMs(1000)

        // ⑥ Sub-menu (Clear data / Clear all data / Clear cache)? Pick strongest.
        handleSubMenu(service)

        // ⑦ Confirm dialog → OK / Delete / Yes
        handleConfirmDialog(service)

        return true
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Find Storage & cache row — MULTIPLE strategies
    // ══════════════════════════════════════════════════════════════════════
    private fun findStorageRow(service: AccessibilityService, timeoutMs: Long): AccessibilityNodeInfo? {
        val end = SystemClock.uptimeMillis() + timeoutMs

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return null
            val root = service.rootInActiveWindow
            if (root == null) {
                StepUtils.sleepMs(300)
                continue
            }

            // ── Strategy 1: Exact text match (multiple variants) ──
            val variants = arrayOf(
                "Storage & cache", "Storage and cache",
                "Storage & Cache", "Storage and Cache",
                "Storage &amp; cache", "Storage &amp; Cache",
                "Storage", "Memory", "Storage & data"
            )
            for (v in variants) {
                val n = NodeFinder.findByText(root, v)
                if (n != null && n.isVisibleToUser) {
                    val r = Rect(); n.getBoundsInScreen(r)
                    if (r.width() > 0 && r.height() > 0 && r.top > 100) {
                        AutomationState.log("  ℹ Found row via exact: '$v' @ ${r.centerX()},${r.centerY()}")
                        return n
                    }
                }
            }

            // ── Strategy 2: Contains match ──
            for (v in arrayOf("Storage & cache", "Storage & Cache", "storage & cache")) {
                val n = NodeFinder.findByTextContains(root, v)
                if (n != null && n.isVisibleToUser) {
                    val r = Rect(); n.getBoundsInScreen(r)
                    if (r.width() > 0 && r.height() > 0 && r.top > 100) {
                        AutomationState.log("  ℹ Found row via contains: '$v'")
                        return n
                    }
                }
            }

            // ── Strategy 3: Any CLICKABLE row whose subtree contains "storage" ──
            val row = findClickableRowBySubtree(root, "storage")
            if (row != null) {
                val r = Rect(); row.getBoundsInScreen(r)
                if (r.width() > 0 && r.height() > 0 && r.top > 100) {
                    AutomationState.log("  ℹ Found row via subtree scan @ ${r.centerX()},${r.centerY()}")
                    return row
                }
            }

            StepUtils.sleepMs(350)
        }
        return null
    }

    /** BFS: find clickable node whose subtree contains needle (text or contentDesc) */
    private fun findClickableRowBySubtree(root: AccessibilityNodeInfo, needle: String): AccessibilityNodeInfo? {
        val lower = needle.lowercase()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (n.isClickable && n.isVisibleToUser && subtreeContains(n, lower)) {
                return n
            }
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

    // ══════════════════════════════════════════════════════════════════════
    //  Click node — 3-tier fallback (direct → parent → coordinate tap)
    // ══════════════════════════════════════════════════════════════════════
    private fun clickNodeRobust(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        // Method 1: direct click
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            AutomationState.log("    ↳ click: direct")
            return true
        }

        // Method 2: clickable parent
        val parent = NodeFinder.findClickableParent(node)
        if (parent != null && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            AutomationState.log("    ↳ click: parent")
            return true
        }

        // Method 3: coordinate tap
        val r = Rect(); node.getBoundsInScreen(r)
        if (r.width() > 0 && r.height() > 0) {
            // Tap slightly left of center to avoid right-side arrow hit area issues
            val tx = r.centerX()
            val ty = r.centerY()
            AutomationState.log("    ↳ tap at ($tx, $ty) bounds=${r.width()}x${r.height()}")
            return tapAt(service, tx, ty)
        }

        AutomationState.log("    ↳ click: bounds invalid")
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
        } catch (e: Exception) {
            false
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Scroll into safe zone (avoid bottom navbar overlap)
    // ══════════════════════════════════════════════════════════════════════
    private fun scrollIntoViewIfNeeded(service: AccessibilityService, node: AccessibilityNodeInfo) {
        val r = Rect(); node.getBoundsInScreen(r)
        val root = service.rootInActiveWindow ?: return
        val rootR = Rect(); root.getBoundsInScreen(rootR)
        val bottomSafeZone = rootR.bottom - 250  // avoid navbar
        val topSafeZone = rootR.top + 150

        if (r.centerY() > bottomSafeZone) {
            AutomationState.log("    ℹ Row too low — scrolling up")
            // Try scroll on some scrollable node
            val scrollable = findScrollable(root)
            scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
            StepUtils.sleepMs(500)
        } else if (r.centerY() < topSafeZone) {
            AutomationState.log("    ℹ Row too high — scrolling down")
            val scrollable = findScrollable(root)
            scrollable?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
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

    // ══════════════════════════════════════════════════════════════════════
    //  Wait for Clear data button (sub-screen)
    // ══════════════════════════════════════════════════════════════════════
    private fun waitForClearDataButton(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                val node = NodeFinder.findByText(root,
                    "Clear storage", "Clear data", "Clear all data", "Delete data")
                    ?: NodeFinder.findByTextContains(root,
                    "Clear storage", "Clear data", "Clear all data")
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
                val node = NodeFinder.findByText(root,
                    "Clear storage", "Clear data", "Clear all data", "Delete data")
                    ?: NodeFinder.findByTextContains(root,
                    "Clear storage", "Clear data", "Clear all data")
                if (node != null && node.isVisibleToUser) {
                    if (clickNodeRobust(service, node)) return true
                }
            }
            StepUtils.sleepMs(300)
        }
        return false
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Sub-menu: "Clear data / Clear all data / Clear cache / Cancel"
    // ══════════════════════════════════════════════════════════════════════
    private fun handleSubMenu(service: AccessibilityService) {
        StepUtils.sleepMs(600)
        val root = service.rootInActiveWindow ?: return

        val subMenuVisible = NodeFinder.containsAny(root, "Clear cache", "Clear all data", "Cancel")
        if (!subMenuVisible) return

        AutomationState.log("  ℹ Sub-menu detected")

        val options = arrayOf("Clear all data", "Clear app data", "Clear data", "Delete data")
        for (opt in options) {
            val node = NodeFinder.findByText(root, opt)
            if (node != null && node.isVisibleToUser && clickNodeRobust(service, node)) {
                AutomationState.log("  ✔ Sub-menu: tapped '$opt'")
                StepUtils.sleepMs(1200)
                handleConfirmDialog(service)
                return
            }
        }
        AutomationState.log("  ⚠ No sub-menu option clickable")
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Confirm dialog: "Delete all data?" → OK
    // ══════════════════════════════════════════════════════════════════════
    private fun handleConfirmDialog(service: AccessibilityService) {
        StepUtils.sleepMs(800)

        val end = SystemClock.uptimeMillis() + 3500
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return

            val root = service.rootInActiveWindow
            if (root != null) {
                val dialogVisible = NodeFinder.containsAny(
                    root,
                    "Delete all data", "Delete app data",
                    "All app data", "Clear app data", "Clear all data",
                    "Delete all app data"
                )
                if (dialogVisible) {
                    val okOptions = arrayOf("OK", "Delete", "Clear", "Yes", "Confirm", "Erase", "Continue")
                    for (opt in okOptions) {
                        val node = NodeFinder.findByText(root, opt)
                        if (node != null && node.isVisibleToUser) {
                            if (clickNodeRobust(service, node)) {
                                AutomationState.log("  ✔ Confirm dialog: tapped '$opt'")
                                StepUtils.sleepMs(2000)
                                return
                            }
                        }
                    }
                }
            }
            StepUtils.sleepMs(250)
        }
        AutomationState.log("  ℹ No confirm dialog (or already dismissed)")
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
