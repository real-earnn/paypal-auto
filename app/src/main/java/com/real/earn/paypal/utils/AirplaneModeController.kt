package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

object AirplaneModeController {

    fun toggleCycle(service: AccessibilityService): Boolean = strictToggle(service)

    fun strictToggle(service: AccessibilityService): Boolean {
        return try {
            // Normalize: if already ON, turn OFF first
            if (isAirplaneModeOn(service)) {
                AutomationState.log("ℹ Flight was ON — normalizing")
                if (!openQsAndWait(service)) return false
                if (!clickTileOnce(service)) {
                    AutomationState.log("✘ Normalize click fail")
                    goHome(service); return false
                }
                waitAirplaneState(service, false, 4000)
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                StepUtils.waitForPackageExit(service, "systemui", 3000)
                StepUtils.sleepMs(700)
            }

            AutomationState.log("✈ Flight cycle start")

            // ON
            if (!openQsAndWait(service)) {
                AutomationState.log("✘ QS not shown")
                return false
            }
            if (!clickTileOnce(service)) {
                AutomationState.log("✘ ON click fail")
                goHome(service); return false
            }
            if (!waitAirplaneState(service, true, 4500)) {
                AutomationState.log("⚠ ON not confirmed")
            } else {
                AutomationState.log("✔ Flight ON")
            }

            // 1 second ON
            StepUtils.sleepMs(1000)

            // Some OEMs auto-close QS
            if (!isQsOpen(service)) {
                AutomationState.log("ℹ QS closed — reopening for OFF")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                StepUtils.waitForPackage(service, "systemui", 2500)
                StepUtils.sleepMs(800)
            }

            // OFF
            if (!clickTileOnce(service)) {
                AutomationState.log("ℹ OFF retry — reopening QS")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                StepUtils.waitForPackage(service, "systemui", 2500)
                StepUtils.sleepMs(800)
                if (!clickTileOnce(service)) {
                    AutomationState.log("✘ OFF click fail")
                    goHome(service); return false
                }
            }
            if (!waitAirplaneState(service, false, 4500)) {
                AutomationState.log("⚠ OFF not confirmed")
            } else {
                AutomationState.log("✔ Flight OFF")
            }

            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.waitForPackageExit(service, "systemui", 3000)
            AutomationState.log("ℹ Waiting for signal…")
            StepUtils.sleepMs(4000)

            AutomationState.log("✔ Flight cycle done")
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Flight error: ${e.message}")
            goHome(service)
            false
        }
    }

    // ══ helpers ══

    private fun openQsAndWait(service: AccessibilityService): Boolean {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        if (!StepUtils.waitForPackage(service, "systemui", 2500)) return false
        StepUtils.sleepMs(800)
        val end = SystemClock.uptimeMillis() + 3000
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            if (findTile(service) != null) return true
            StepUtils.sleepMs(250)
        }
        return false
    }

    private fun isQsOpen(service: AccessibilityService): Boolean =
        StepUtils.isPackageForeground(service, "systemui")

    private fun clickTileOnce(service: AccessibilityService): Boolean {
        val tile = findTile(service) ?: return false
        return NodeFinder.click(tile)
    }

    private fun waitAirplaneState(service: AccessibilityService, desired: Boolean, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            if (isAirplaneModeOn(service) == desired) return true
            StepUtils.sleepMs(250)
        }
        return false
    }

    /**
     * 🎯 SMART findTile:
     *  1. Collect ALL "airplane mode" matches (text/desc/id)
     *  2. Filter out status bar (top 150px) — wo carrier name wala hota hai
     *  3. Remaining:
     *     - 1  → wahi click
     *     - 2+ → sort by Y, take middle
     */
    private fun findTile(service: AccessibilityService): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null

        val all = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val desc = n.contentDescription?.toString()?.lowercase() ?: ""
            val text = n.text?.toString()?.lowercase() ?: ""
            val id = n.viewIdResourceName?.lowercase() ?: ""

            val isMatch =
                desc.contains("airplane") || desc.contains("flight mode") ||
                text.contains("airplane mode") || text.contains("flight mode") ||
                id.contains("airplane")

            if (isMatch) {
                val clickable = if (n.isClickable) n else (findClickableAncestor(n) ?: n)
                all.add(clickable)
            }

            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }

        if (all.isEmpty()) return null

        // Dedupe by bounds
        val seen = mutableSetOf<String>()
        val unique = all.filter { node ->
            val r = Rect(); node.getBoundsInScreen(r)
            val key = "${r.left},${r.top},${r.right},${r.bottom}"
            if (seen.contains(key)) false else { seen.add(key); true }
        }

        // Filter out status bar (top 150px)
        val nonStatusBar = unique.filter { node ->
            val r = Rect(); node.getBoundsInScreen(r)
            r.top > 150
        }

        val pool = if (nonStatusBar.isNotEmpty()) nonStatusBar else unique

        // Sort by Y
        val sorted = pool.sortedBy { node ->
            val r = Rect(); node.getBoundsInScreen(r); r.top
        }

        val picked = when {
            sorted.size == 1 -> sorted[0]
            sorted.size == 2 -> sorted[1]          // 2nd one = tile (1st is carrier/status)
            else -> sorted[sorted.size / 2]        // middle
        }

        val pr = Rect(); picked.getBoundsInScreen(pr)
        AutomationState.log("ℹ Tile pick: size=${sorted.size} at y=${pr.top}")
        return picked
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur = node.parent
        var d = 0
        while (cur != null && d < 6) {
            if (cur.isClickable) return cur
            cur = cur.parent; d++
        }
        return null
    }

    private fun goHome(service: AccessibilityService) {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(800)
        } catch (_: Exception) {}
    }

    fun isAirplaneModeOn(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (e: Exception) { false }
}
