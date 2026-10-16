package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Intent
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

                if (!StepUtils.waitForPackage(service, "settings", 3500) &&
                    !StepUtils.waitForPackage(service, "android", 1500)) {
                    AutomationState.log("  ⚠ Settings not foreground")
                    continue
                }

                StepUtils.sleepMs(1300)

                val root = service.rootInActiveWindow
                if (root == null) {
                    AutomationState.log("  ⚠ root null")
                    continue
                }

                val hasAppInfoContent = NodeFinder.containsAny(
                    root,
                    "Storage", "Storage & cache", "Memory",
                    "Force stop", "Uninstall", "App info"
                )
                if (!hasAppInfoContent) {
                    AutomationState.log("  ⚠ App Info content not ready")
                    continue
                }

                AutomationState.log("✔ App Info ready (attempt $attempt)")
                StepUtils.sleepMs(500)

                // ══════════════════════════════════════════════════════
                // 🟢 PATH A: Standard — Storage row → Clear data
                // ══════════════════════════════════════════════════════
                if (tryPathA(service)) {
                    AutomationState.log("✔ Clear data done (Path A)")
                    exitHome(service)
                    return true
                }

                // ══════════════════════════════════════════════════════
                // 🟢 PATH B: Scroll down → "Clear data" button at bottom
                // ══════════════════════════════════════════════════════
                if (tryPathB(service)) {
                    AutomationState.log("✔ Clear data done (Path B)")
                    exitHome(service)
                    return true
                }

                AutomationState.log("  ⚠ Both paths failed on attempt #$attempt")
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
    //  PATH A: Storage row → Clear data button → confirm
    // ══════════════════════════════════════════════════════════════════════
    private fun tryPathA(service: AccessibilityService): Boolean {
        AutomationState.log("  → Path A: Storage row")

        if (!tapWhen(service, 3000,
                "Storage & cache", "Storage and cache", "Storage", "Memory")) {
            AutomationState.log("  ⚠ Storage row not found")
            return false
        }
        StepUtils.sleepMs(1000)

        // Sub-screen: tap "Clear data" / "Clear storage"
        if (!tapWhen(service, 3000,
                "Clear storage", "Clear data", "Clear all data", "Delete data")) {
            AutomationState.log("  ⚠ Clear row not found in storage sub-screen")
            return false
        }
        StepUtils.sleepMs(800)

        // Handle confirm dialog (Delete all data? → OK)
        handleConfirmDialog(service)
        return true
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PATH B: Scroll down → find "Clear data" button at bottom
    // ══════════════════════════════════════════════════════════════════════
    private fun tryPathB(service: AccessibilityService): Boolean {
        AutomationState.log("  → Path B: Scroll to bottom")

        // Check if "Clear data" text already visible on current screen
        val root1 = service.rootInActiveWindow
        if (root1 != null) {
            val existing = NodeFinder.findByText(root1, "Clear data", "Clear storage")
            if (existing != null && existing.isVisibleToUser) {
                AutomationState.log("  ℹ Clear data already visible — tapping")
                if (NodeFinder.click(existing)) {
                    StepUtils.sleepMs(800)
                    // Handle the sub-menu (Clear data / Clear all data / Clear cache)
                    handleSubMenu(service)
                    return true
                }
            }
        }

        // Scroll down multiple times to find it
        for (scrollIdx in 1..5) {
            if (StepUtils.shouldStop()) return false

            performScrollDown(service)
            StepUtils.sleepMs(600)

            val root = service.rootInActiveWindow ?: continue
            val clearBtn = NodeFinder.findByText(root, "Clear data", "Clear storage", "Clear all data")
            if (clearBtn != null && clearBtn.isVisibleToUser) {
                AutomationState.log("  ℹ Clear data found after scroll #$scrollIdx")
                if (NodeFinder.click(clearBtn)) {
                    StepUtils.sleepMs(800)
                    // Handle sub-menu if appears (Clear data / Clear all data / Clear cache / Cancel)
                    handleSubMenu(service)
                    return true
                }
            }
        }

        AutomationState.log("  ⚠ Clear data not found after scrolling")
        return false
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Sub-menu handler: "Clear data / Clear all data / Clear cache / Cancel"
    // ══════════════════════════════════════════════════════════════════════
    private fun handleSubMenu(service: AccessibilityService) {
        StepUtils.sleepMs(500)
        val root = service.rootInActiveWindow ?: return

        // Sub-menu typically has "Clear all data" or "Clear data" option
        val subMenuVisible = NodeFinder.containsAny(root, "Clear cache", "Clear all data", "Cancel")
        if (!subMenuVisible) return

        AutomationState.log("  ℹ Sub-menu detected")

        // Prefer "Clear all data" over "Clear data"
        val options = arrayOf("Clear all data", "Clear data", "Clear app data", "Delete data")
        for (opt in options) {
            val node = NodeFinder.findByText(root, opt)
            if (node != null && node.isVisibleToUser && NodeFinder.click(node)) {
                AutomationState.log("  ✔ Sub-menu: tapped '$opt'")
                StepUtils.sleepMs(900)
                // Now handle final confirm dialog
                handleConfirmDialog(service)
                return
            }
        }
        AutomationState.log("  ⚠ No sub-menu option clickable")
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Confirm dialog handler: "Delete all data?" → "OK" / "Delete" / "Yes"
    // ══════════════════════════════════════════════════════════════════════
    private fun handleConfirmDialog(service: AccessibilityService) {
        // Wait for dialog to appear
        StepUtils.sleepMs(700)

        val end = SystemClock.uptimeMillis() + 2500
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return

            val root = service.rootInActiveWindow
            if (root != null) {
                // Check if dialog visible
                val dialogVisible = NodeFinder.containsAny(
                    root,
                    "Delete all data", "Delete app data",
                    "All app data", "Clear app data", "Clear all data"
                )
                if (dialogVisible) {
                    // Find OK button (positive button in dialog)
                    val okOptions = arrayOf("OK", "Delete", "Clear", "Yes", "Confirm", "Erase", "Continue")
                    for (opt in okOptions) {
                        val node = NodeFinder.findByText(root, opt)
                        if (node != null && node.isVisibleToUser) {
                            if (NodeFinder.click(node)) {
                                AutomationState.log("  ✔ Confirm dialog: tapped '$opt'")
                                StepUtils.sleepMs(1800)
                                return
                            }
                        }
                    }
                }
            }
            StepUtils.sleepMs(200)
        }
        AutomationState.log("  ℹ No confirm dialog (or already dismissed)")
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Scroll down
    // ══════════════════════════════════════════════════════════════════════
    private fun performScrollDown(service: AccessibilityService): Boolean {
        return try {
            val root = service.rootInActiveWindow ?: return false
            // Try scroll on any scrollable node
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)
            while (queue.isNotEmpty()) {
                val n = queue.removeFirst()
                if (n.isScrollable) {
                    val ok = n.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    if (ok) return true
                }
                for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
            }
            false
        } catch (_: Exception) { false }
    }

    private fun tapWhen(
        service: AccessibilityService,
        timeoutMs: Long,
        vararg texts: String
    ): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                val node = NodeFinder.findByText(root, *texts)
                    ?: NodeFinder.findByTextContains(root, *texts)
                if (node != null && NodeFinder.click(node)) return true
            }
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return false
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
