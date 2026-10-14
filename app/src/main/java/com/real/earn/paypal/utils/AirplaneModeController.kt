package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

object AirplaneModeController {

    fun toggleCycle(service: AccessibilityService): Boolean {
        return try {
            AutomationState.log("✈ Flight cycle start")

            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)

            if (!waitForAirplaneTile(service, 3000)) {
                AutomationState.log("✘ Airplane tile not found")
                closeQS(service)
                return false
            }
            AutomationState.log("ℹ Tile found")

            val tile = findAirplaneTile(service)
            if (tile == null || !NodeFinder.click(tile)) {
                AutomationState.log("✘ Click ON fail")
                closeQS(service)
                return false
            }

            if (!waitForAirplaneState(service, true, 4000)) {
                AutomationState.log("⚠ ON state not confirmed")
            } else {
                AutomationState.log("✔ Flight ON")
            }

            sleepMs(3000)

            var tile2 = findAirplaneTile(service)
            if (tile2 == null) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                sleepMs(1200)
                tile2 = findAirplaneTile(service)
            }
            if (tile2 == null) {
                AutomationState.log("✘ Tile lost for OFF")
                closeQS(service)
                return false
            }

            if (!NodeFinder.click(tile2)) {
                AutomationState.log("✘ Click OFF fail")
                closeQS(service)
                return false
            }

            if (!waitForAirplaneState(service, false, 4000)) {
                AutomationState.log("⚠ OFF state not confirmed")
            } else {
                AutomationState.log("✔ Flight OFF")
            }

            closeQS(service)
            sleepMs(500)
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Flight error: ${e.message}")
            try { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) } catch (_: Exception) {}
            false
        }
    }

    private fun waitForAirplaneTile(service: AccessibilityService, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (findAirplaneTile(service) != null) return true
            sleepMs(300)
        }
        return false
    }

    private fun waitForAirplaneState(
        service: AccessibilityService,
        desired: Boolean,
        timeoutMs: Long
    ): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (isAirplaneModeOn(service) == desired) return true
            sleepMs(250)
        }
        return false
    }

    private fun findAirplaneTile(service: AccessibilityService): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()

            val desc = n.contentDescription?.toString()?.lowercase() ?: ""
            val text = n.text?.toString()?.lowercase() ?: ""
            val id = n.viewIdResourceName?.lowercase() ?: ""

            if (desc.contains("airplane") ||
                desc.contains("flight mode") ||
                desc.contains("aeroplane") ||
                text.contains("airplane mode") ||
                text.contains("flight mode") ||
                id.contains("airplane")
            ) {
                val clickable = if (n.isClickable) n else findClickableAncestor(n)
                return clickable ?: n
            }

            for (i in 0 until n.childCount) {
                n.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findClickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur = node.parent
        var depth = 0
        while (cur != null && depth < 6) {
            if (cur.isClickable) return cur
            cur = cur.parent
            depth++
        }
        return null
    }

    private fun closeQS(service: AccessibilityService) {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            sleepMs(400)
        } catch (_: Exception) {}
    }

    fun isAirplaneModeOn(context: Context): Boolean {
        return try {
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.AIRPLANE_MODE_ON, 0
            ) == 1
        } catch (e: Exception) { false }
    }

    private fun sleepMs(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }
}
