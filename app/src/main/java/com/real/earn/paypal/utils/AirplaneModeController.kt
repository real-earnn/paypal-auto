package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

object AirplaneModeController {

    fun toggleCycle(service: AccessibilityService): Boolean = strictToggle(service)

    fun strictToggle(service: AccessibilityService): Boolean {
        return try {
            AutomationState.log("✈ Flight ON...")

            // ① Open QS — wait for systemui
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            if (!StepUtils.waitForPackage(service, "systemui", 2500)) {
                AutomationState.log("✘ QS not shown")
                return false
            }
            StepUtils.sleepMs(600)

            // ② Find tile
            val tile1 = waitForTile(service, 3000)
            if (tile1 == null) {
                AutomationState.log("✘ Tile not found")
                goHome(service)
                return false
            }

            // ③ Click ON
            if (!NodeFinder.click(tile1)) {
                AutomationState.log("✘ ON click fail")
                goHome(service)
                return false
            }
            if (!waitAirplaneState(service, true, 4000)) {
                AutomationState.log("⚠ ON not confirmed")
            } else {
                AutomationState.log("✔ Flight ON")
            }

            // ④ Wait 1 second (panel same, ON)
            StepUtils.sleepMs(1000)

            // ⑤ Re-find tile and click OFF
            val tile2 = findTile(service)
            if (tile2 == null || !NodeFinder.click(tile2)) {
                AutomationState.log("✘ OFF click fail")
                goHome(service)
                return false
            }
            if (!waitAirplaneState(service, false, 4000)) {
                AutomationState.log("⚠ OFF not confirmed")
            } else {
                AutomationState.log("✔ Flight OFF")
            }

            // ⑥ HOME — close QS
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            // Wait until systemui is gone
            StepUtils.waitForPackageExit(service, "systemui", 3000)
            StepUtils.sleepMs(600)

            // ⑦ Wait for signal to reconnect (~4 seconds)
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

    private fun goHome(service: AccessibilityService) {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(800)
        } catch (_: Exception) {}
    }

    private fun waitForTile(service: AccessibilityService, timeoutMs: Long): AccessibilityNodeInfo? {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return null
            findTile(service)?.let { return it }
            StepUtils.sleepMs(250)
        }
        return null
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

    private fun findTile(service: AccessibilityService): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            val desc = n.contentDescription?.toString()?.lowercase() ?: ""
            val text = n.text?.toString()?.lowercase() ?: ""
            val id = n.viewIdResourceName?.lowercase() ?: ""

            if (desc.contains("airplane") || desc.contains("flight mode") ||
                text.contains("airplane mode") || text.contains("flight mode") ||
                id.contains("airplane")
            ) {
                return if (n.isClickable) n else (findClickableAncestor(n) ?: n)
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }
        return null
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

    fun isAirplaneModeOn(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (e: Exception) { false }
}
