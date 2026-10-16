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

                // ══════════════════════════════════════════════════════════
                //  ① Fire App Info intent
                // ══════════════════════════════════════════════════════════
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

                // ══════════════════════════════════════════════════════════
                //  ② 🟢 CONTENT-BASED detection (NO package-name check)
                //     - Fire intent → wait 500ms → read root
                //     - Root mein App Info markers (Storage, Force stop, etc.)
                //     - Mil gaya → proceed, warna next check (max ~6s)
                // ══════════════════════════════════════════════════════════
                val appInfoReady = waitForAppInfoContent(service, 6000)
                if (!appInfoReady) {
                    AutomationState.log("  ⚠ App Info content not detected")
                    continue
                }
                AutomationState.log("✔ App Info ready (attempt $attempt)")
                StepUtils.sleepMs(500)

                // ══════════════════════════════════════════════════════════
                //  PATH A: Standard — Storage row → Clear data
                // ══════════════════════════════════════════════════════════
                if (tryPathA(service)) {
                    AutomationState.log("✔ Clear data done (Path A)")
                    exitHome(service)
                    return true
                }

                // ══════════════════════════════════════════════════════════
                //  PATH B: Scroll down → "Clear data" button at bottom
                // ══════════════════════════════════════════════════════════
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
    //  🟢 Content-based App Info detection
    //  Package name ki fikr nahi — sirf screen ke content pe focus
    // ══════════════════════════════════════════════════════════════════════
    private fun waitForAppInfoContent(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        var checkCount = 0

        // Pehle ek initial delay — transition ke liye
        StepUtils.sleepMs(700)

        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false

            checkCount++
            val root = service.rootInActiveWindow

            if (root == null) {
                AutomationState.log("    ↳ check#$checkCount: root null")
                StepUtils.sleepMs(500)
                continue
            }

            val pkg = root.packageName?.toString() ?: "?"
            val hasContent = NodeFinder.containsAny(
                root,
                // App Info common markers (kisi bhi OEM pe yeh texts hote hain)
                "Storage",
                "Storage & cache",
                "Force stop",
                "Uninstall",
                "App info",
                "App permissions",
                "Notifications",
                "Permissions",
                "Battery",
                "Mobile data",
                "Data usage"
            )

            if (hasContent) {
                AutomationState.log("    ↳ check#$checkCount: App Info content found (pkg=$pkg)")
                return true
            }

            AutomationState.log("    ↳ check#$checkCount: not ready (pkg=$pkg)")
            StepUtils.sleepMs(500)
        }
        return false
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PATH A: Storage row → Clear data button
    // ══════════════════════════════════════════════════════════════════════
    private fun tryPathA(service: AccessibilityService): Boolean {
        AutomationState.log("  → Path A: Storage row")

        if (!tapWhen(service, 3000,
                "Storage & cache", "Storage and cache", "Storage", "Memory")) {
            AutomationState.log("  ⚠ Storage row not found")
            return false
        }
        StepUtils.sleepMs(1200)

        // Sub-screen — wait for it to load
        val subScreenReady = waitForClearDataButton(service, 3000)
        if (!subScreenReady) {
            AutomationState.log("  ⚠ Clear data button not found in storage sub-screen")
            return false
        }

        // Now tap Clear data / Clear storage / Clear all data
        if (!tapWhen(service, 2000,
                "Clear storage", "Clear data", "Clear all data", "Delete data")) {
            AutomationState.log("  ⚠ Clear tap failed")
            return false
        }
        StepUtils.sleepMs(1000)

        // If it's a 2-step menu (Clear data / Clear all data / Clear cache), pick the strongest
        handleSubMenu(service)

        // Confirm dialog (Delete all data? → OK)
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
            val existing = NodeFinder.findByText(root1, "Clear data", "Clear storage", "Clear all data")
            if (existing != null && existing.isVisibleToUser) {
                AutomationState.log("  ℹ Clear data already visible — tapping")
                if (NodeFinder.click(existing)) {
                    StepUtils.sleepMs(1000)
                    handleSubMenu(service)
                    handleConfirmDialog(service)
                    return true
                }
            }
        }

        // Scroll down multiple times to find it
        for (scrollIdx in 1..6) {
            if (StepUtils.shouldStop()) return false

            performScrollDown(service)
            StepUtils.sleepMs(700)

            val root = service.rootInActiveWindow ?: continue
            val clearBtn = NodeFinder.findByText(root, "Clear data", "Clear storage", "Clear all data")
            if (clearBtn != null && clearBtn.isVisibleToUser) {
                AutomationState.log("  ℹ Clear data found after scroll #$scrollIdx")
                if (NodeFinder.click(clearBtn)) {
                    StepUtils.sleepMs(1000)
                    handleSubMenu(service)
                    handleConfirmDialog(service)
                    return true
                }
            }
        }

        AutomationState.log("  ⚠ Clear data not found after scrolling")
        return false
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Wait specifically for "Clear data" button (in storage sub-screen)
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

    // ══════════════════════════════════════════════════════════════════════
    //  Sub-menu: "Clear data / Clear all data / Clear cache / Cancel"
    // ══════════════════════════════════════════════════════════════════════
    private fun handleSubMenu(service: AccessibilityService) {
        StepUtils.sleepMs(600)
        val root = service.rootInActiveWindow ?: return

        // Sub-menu check — do option ya zyada
        val subMenuVisible = NodeFinder.containsAny(root, "Clear cache", "Clear all data", "Cancel")
        if (!subMenuVisible) return

        AutomationState.log("  ℹ Sub-menu detected")

        // Prefer "Clear all data" over "Clear data"
        val options = arrayOf("Clear all data", "Clear app data", "Clear data", "Delete data")
        for (opt in options) {
            val node = NodeFinder.findByText(root, opt)
            if (node != null && node.isVisibleToUser && NodeFinder.click(node)) {
                AutomationState.log("  ✔ Sub-menu: tapped '$opt'")
                StepUtils.sleepMs(1200)
                // Confirm dialog bhi aa sakta hai is ke baad
                handleConfirmDialog(service)
                return
            }
        }
        AutomationState.log("  ⚠ No sub-menu option clickable")
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Confirm dialog: "Delete all data?" → OK / Delete / Yes
    // ══════════════════════════════════════════════════════════════════════
    private fun handleConfirmDialog(service: AccessibilityService) {
        StepUtils.sleepMs(800)

        val end = SystemClock.uptimeMillis() + 3000
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
                            if (NodeFinder.click(node)) {
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

    // ══════════════════════════════════════════════════════════════════════
    //  Scroll down
    // ══════════════════════════════════════════════════════════════════════
    private fun performScrollDown(service: AccessibilityService): Boolean {
        return try {
            val root = service.rootInActiveWindow ?: return false
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
