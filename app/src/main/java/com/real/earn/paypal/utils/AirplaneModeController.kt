package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.real.earn.paypal.service.AutomationState

object AirplaneModeController {

    fun toggleCycle(service: AccessibilityService): Boolean = strictToggle(service)

    fun strictToggle(service: AccessibilityService): Boolean {
        return try {
            // Normalize: if ON, turn OFF first
            if (isAirplaneModeOn(service)) {
                AutomationState.log("ℹ Flight was ON — normalizing")
                toggleTo(service, targetOn = false)
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                StepUtils.waitForPackageExit(service, "systemui", 1500)
            }

            // Turn ON
            AutomationState.log("✈ Flight ON")
            if (!toggleTo(service, targetOn = true)) {
                AutomationState.log("✘ ON failed")
                goHome(service); return false
            }
            AutomationState.log("✔ Flight ON")

            // Gap
            StepUtils.sleepMs(900)

            // Turn OFF
            AutomationState.log("✈ Flight OFF")
            if (!toggleTo(service, targetOn = false)) {
                AutomationState.log("✘ OFF failed")
                goHome(service); return false
            }
            AutomationState.log("✔ Flight OFF")

            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.waitForPackageExit(service, "systemui", 1500)
            AutomationState.log("✔ Flight cycle done")
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Flight error: ${e.message}")
            goHome(service)
            false
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  toggleTo — Saved coords first, find-tile fallback
    // ══════════════════════════════════════════════════════════════════════
    private fun toggleTo(service: AccessibilityService, targetOn: Boolean): Boolean {
        val prefs = AppPrefs(service)

        // ═══ ① Open Quick Settings ═══
        if (!openQs(service)) {
            AutomationState.log("  ✘ QS not open")
            return false
        }
        StepUtils.sleepMs(700)

        // ═══ ② Try saved coordinates first ═══
        val savedX = prefs.flightTapX
        val savedY = prefs.flightTapY

        if (savedX > 0 && savedY > 0) {
            AutomationState.log("  ℹ Using saved coords ($savedX, $savedY)")
            if (tryTap(service, savedX, savedY, targetOn)) {
                return true
            }
            // Saved coords failed → clear them
            AutomationState.log("  ⚠ Saved coords failed — refinding tile")
            prefs.flightTapX = 0
            prefs.flightTapY = 0
        }

        // ═══ ③ Find tile by accessibility scan ═══
        val pick = findTile(service) ?: run {
            AutomationState.log("  ✘ No tile found")
            return false
        }
        val r = Rect(); pick.getBoundsInScreen(r)
        val x = r.centerX()
        val y = r.centerY()
        AutomationState.log("  ℹ Tile found at ($x, $y) size=${r.width()}x${r.height()}")

        // ═══ ④ Tap & verify ═══
        if (tryTap(service, x, y, targetOn)) {
            prefs.flightTapX = x
            prefs.flightTapY = y
            AutomationState.log("  ✔ Saved tap coords ($x, $y)")
            return true
        }

        // ═══ ⑤ Retry: re-open QS if closed, retry once ═══
        AutomationState.log("  ⚠ First tap failed — retry")
        if (!isQsOpen(service)) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            StepUtils.waitForPackage(service, "systemui", 1500)
            StepUtils.sleepMs(700)
        }

        if (tryTap(service, x, y, targetOn)) {
            prefs.flightTapX = x
            prefs.flightTapY = y
            AutomationState.log("  ✔ Saved tap coords (retry) ($x, $y)")
            return true
        }

        return false
    }

    private fun tryTap(service: AccessibilityService, x: Int, y: Int, targetOn: Boolean): Boolean {
        tapAt(service, x, y)
        if (waitAirplaneState(service, targetOn, 3200)) return true
        return false
    }

    private fun openQs(service: AccessibilityService): Boolean {
        if (isQsOpen(service)) return true
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        return StepUtils.waitForPackage(service, "systemui", 2500)
    }

    private fun isQsOpen(service: AccessibilityService): Boolean =
        StepUtils.isPackageForeground(service, "systemui")

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

    private fun waitAirplaneState(service: AccessibilityService, desired: Boolean, timeoutMs: Long): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            if (isAirplaneModeOn(service) == desired) return true
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return false
    }

    // ══════════════════════════════════════════════════════════════════════
    //  FIND TILE — returns ORIGINAL matched node (for gesture tap bounds)
    // ══════════════════════════════════════════════════════════════════════
    private fun findTile(service: AccessibilityService): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        val screenR = Rect(); root.getBoundsInScreen(screenR)
        val screenArea = screenR.width() * screenR.height()

        data class Candidate(
            val node: AccessibilityNodeInfo,
            val score: Int,
            val desc: String,
            val text: String,
            val id: String,
            val rect: Rect
        )

        val candidates = mutableListOf<Candidate>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()

            val desc = n.contentDescription?.toString()?.trim() ?: ""
            val text = n.text?.toString()?.trim() ?: ""
            val id = n.viewIdResourceName ?: ""

            val dl = desc.lowercase()
            val tl = text.lowercase()
            val il = id.lowercase()

            val saysAirplane =
                dl.contains("airplane") || dl.contains("flight mode") ||
                tl.contains("airplane") || tl.contains("flight mode") ||
                il.contains("airplane")

            if (saysAirplane && n.isVisibleToUser) {
                val r = Rect(); n.getBoundsInScreen(r)

                var score = 0
                val nodeArea = r.width() * r.height()
                if (nodeArea > (screenArea * 0.6).toInt()) score += 30
                if (r.top < 150) score += 20

                when {
                    dl == "airplane mode" || dl == "flight mode" -> score += 0
                    tl == "airplane mode" || tl == "flight mode" -> score += 2
                    dl.startsWith("airplane") || dl.startsWith("flight") -> score += 3
                    tl.startsWith("airplane") || tl.startsWith("flight") -> score += 5
                    else -> score += 8
                }

                val adjacent = getAdjacentSiblingTexts(n)
                val combined = (dl + " " + tl + " " + adjacent).lowercase()
                if (combined.contains("mobile data")) score += 15
                if (combined.contains("wi-fi") || combined.contains("wifi")) score += 15
                if (combined.contains("cellular")) score += 10
                if (combined.contains("bluetooth")) score += 10

                candidates.add(Candidate(n, score, desc, text, id, r))
                continue
            }

            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }

        if (candidates.isEmpty()) return null

        val sorted = candidates.sortedBy { it.score }
        AutomationState.log("  ═══ TILE CANDIDATES (${sorted.size}) ═══")
        sorted.forEachIndexed { i, c ->
            val d = c.desc.take(30).ifEmpty { "—" }
            val t = c.text.take(15).ifEmpty { "—" }
            AutomationState.log("    [$i] sc=${c.score} y=${c.rect.top} ${c.rect.width()}x${c.rect.height()} T='$t' D='$d'")
        }

        val picked = sorted.first()
        AutomationState.log("  ✔ Picked: sc=${picked.score} D='${picked.desc.take(30)}'")
        return picked.node
    }

    private fun getAdjacentSiblingTexts(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        try {
            val parent = node.parent ?: return ""
            val nr = Rect(); node.getBoundsInScreen(nr)
            for (i in 0 until parent.childCount) {
                val sib = parent.getChild(i) ?: continue
                if (sib === node) continue
                val sr = Rect(); sib.getBoundsInScreen(sr)
                if (Math.abs(sr.centerX() - nr.centerX()) > 300 &&
                    Math.abs(sr.centerY() - nr.centerY()) > 300) continue
                sib.text?.toString()?.let { if (it.isNotEmpty()) sb.append(" ").append(it) }
                sib.contentDescription?.toString()?.let { if (it.isNotEmpty()) sb.append(" ").append(it) }
            }
        } catch (_: Exception) {}
        return sb.toString()
    }

    private fun goHome(service: AccessibilityService) {
        try {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(500)
        } catch (_: Exception) {}
    }

    fun isAirplaneModeOn(context: Context): Boolean = try {
        Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    } catch (e: Exception) { false }
}
