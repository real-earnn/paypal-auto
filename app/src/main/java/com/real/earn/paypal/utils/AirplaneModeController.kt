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
            if (isAirplaneModeOn(service)) {
                AutomationState.log("ℹ Flight was ON — normalizing")
                if (openQsAndWaitTile(service, 2000)) {
                    clickTile(service)
                    waitAirplaneState(service, false, 2500)
                }
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                StepUtils.waitForPackageExit(service, "systemui", 1500)
            }

            AutomationState.log("✈ Flight ON")
            if (!openQsAndWaitTile(service, 2500)) {
                AutomationState.log("✘ QS tile not found")
                goHome(service); return false
            }

            if (!clickTile(service)) {
                AutomationState.log("✘ ON click fail")
                goHome(service); return false
            }
            if (!waitAirplaneState(service, true, 3000)) {
                AutomationState.log("✘ ON not confirmed (first try) — re-tapping")
                // 🟢 Second attempt: re-open QS if closed, tap again
                if (!isQsOpen(service)) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                    StepUtils.waitForPackage(service, "systemui", 1500)
                    StepUtils.sleepMs(600)
                }
                clickTile(service)
                if (!waitAirplaneState(service, true, 3000)) {
                    AutomationState.log("✘ ON not confirmed (after retry)")
                    goHome(service); return false
                }
            }
            AutomationState.log("✔ Flight ON")

            StepUtils.sleepMs(900)

            if (!isQsOpen(service)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                StepUtils.waitForPackage(service, "systemui", 1500)
                StepUtils.sleepMs(600)
            }

            if (!clickTile(service)) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                StepUtils.waitForPackage(service, "systemui", 1500)
                StepUtils.sleepMs(600)
                if (!clickTile(service)) {
                    AutomationState.log("✘ OFF click fail")
                    goHome(service); return false
                }
            }
            if (!waitAirplaneState(service, false, 3000)) {
                AutomationState.log("✘ OFF not confirmed (first try) — re-tapping")
                if (!isQsOpen(service)) {
                    service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                    StepUtils.waitForPackage(service, "systemui", 1500)
                    StepUtils.sleepMs(600)
                }
                clickTile(service)
                if (!waitAirplaneState(service, false, 3000)) {
                    AutomationState.log("✘ OFF not confirmed (after retry)")
                    goHome(service); return false
                }
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

    private fun openQsAndWaitTile(service: AccessibilityService, timeoutMs: Long): Boolean {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (StepUtils.shouldStop()) return false
            if (isQsOpen(service) && findTile(service) != null) return true
            StepUtils.sleepMs(StepUtils.POLL_MS)
        }
        return false
    }

    private fun isQsOpen(service: AccessibilityService): Boolean =
        StepUtils.isPackageForeground(service, "systemui")

    /**
     * 🟢 FIXED: Uses GESTURE TAP on the tile's bounds (not performAction)
     * This works even when clickable flag is on a wrong parent container.
     */
    private fun clickTile(service: AccessibilityService): Boolean {
        val pick = findTile(service) ?: return false

        // Primary: gesture tap at tile's bounds center
        val r = Rect()
        pick.boundsNode.getBoundsInScreen(r)
        if (r.width() > 0 && r.height() > 0) {
            val x = r.centerX()
            val y = r.centerY()
            AutomationState.log("  ↳ TAP at ($x, $y) size=${r.width()}x${r.height()}")
            if (tapAt(service, x, y)) return true
        }

        // Fallback: direct clickable action
        if (pick.clickableNode != null &&
            pick.clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            AutomationState.log("  ↳ performAction CLICK (fallback)")
            return true
        }
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
            AutomationState.log("  ↳ tapAt err: ${e.message}")
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
    //  TILE PICKER — Score based, uses both original node and clickable
    // ══════════════════════════════════════════════════════════════════════
    data class TilePick(
        val boundsNode: AccessibilityNodeInfo,   // for gesture tap bounds
        val clickableNode: AccessibilityNodeInfo?,  // for performAction fallback
        val score: Int,
        val desc: String
    )

    private fun findTile(service: AccessibilityService): TilePick? {
        val root = service.rootInActiveWindow ?: return null

        val screenR = Rect(); root.getBoundsInScreen(screenR)
        val screenArea = screenR.width() * screenR.height()

        data class Candidate(
            val originalNode: AccessibilityNodeInfo,
            val clickableNode: AccessibilityNodeInfo?,
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

            val descL = desc.lowercase()
            val textL = text.lowercase()
            val idL = id.lowercase()

            val saysAirplane =
                descL.contains("airplane") || descL.contains("flight mode") ||
                textL.contains("airplane") || textL.contains("flight mode") ||
                idL.contains("airplane")

            if (saysAirplane && n.isVisibleToUser) {
                val r = Rect(); n.getBoundsInScreen(r)

                var score = 0

                // Big node (whole screen)?
                val nodeArea = r.width() * r.height()
                if (nodeArea > (screenArea * 0.6).toInt()) score += 30

                // Status bar?
                if (r.top < 150) score += 20

                // Match quality
                when {
                    descL == "airplane mode" || descL == "flight mode" -> score += 0
                    textL == "airplane mode" || textL == "flight mode" -> score += 2
                    descL.startsWith("airplane mode") || descL.startsWith("flight mode") -> score += 3
                    textL.startsWith("airplane mode") || textL.startsWith("flight mode") -> score += 5
                    else -> score += 8
                }

                // Only ADJACENT siblings (not the whole panel)
                val adjacent = getAdjacentSiblingTexts(n)
                val combined = (descL + " " + textL + " " + adjacent).lowercase()

                if (combined.contains("mobile data")) score += 15
                if (combined.contains("wi-fi") || combined.contains("wifi")) score += 15
                if (combined.contains("cellular")) score += 10
                if (combined.contains("bluetooth")) score += 10
                if (combined.contains("hotspot")) score += 10

                // Find smallest reasonable clickable ancestor (tile-sized)
                val clickable = findSmallestClickableTile(n)

                candidates.add(Candidate(n, clickable, score, desc, text, id, r))

                // Don't descend
                continue
            }

            for (i in 0 until n.childCount) n.getChild(i)?.let { queue.add(it) }
        }

        if (candidates.isEmpty()) {
            AutomationState.log("✘ No airplane candidates found")
            return null
        }

        val sorted = candidates.sortedBy { it.score }

        AutomationState.log("═══ TILE CANDIDATES (${sorted.size}) ═══")
        sorted.forEachIndexed { i, c ->
            val d = c.desc.take(35).ifEmpty { "—" }
            val t = c.text.take(20).ifEmpty { "—" }
            val idShort = c.id.substringAfterLast("/").take(20).ifEmpty { "—" }
            AutomationState.log("  [$i] sc=${c.score} y=${c.rect.top} ${c.rect.width()}x${c.rect.height()} id='$idShort' T='$t' D='$d'")
        }

        val picked = sorted.first()

        // Bounds node: prefer clickable if it has reasonable size, else original
        val boundsNode = picked.clickableNode?.let { cn ->
            val cr = Rect(); cn.getBoundsInScreen(cr)
            if (cr.width() in 60..700 && cr.height() in 60..700) cn else picked.originalNode
        } ?: picked.originalNode

        AutomationState.log("✔ PICKED: sc=${picked.score} onNode=${boundsNode === picked.clickableNode} D='${picked.desc.take(30)}'")

        return TilePick(boundsNode, picked.clickableNode, picked.score, picked.desc)
    }

    /** Walk up ancestors, pick the smallest clickable with tile-sized bounds */
    private fun findSmallestClickableTile(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = node
        var best: AccessibilityNodeInfo? = null
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur.isClickable) {
                val r = Rect(); cur.getBoundsInScreen(r)
                val w = r.width(); val h = r.height()
                // Tile-sized: 60..700
                if (w in 60..700 && h in 60..700) {
                    best = cur   // keep the smallest
                }
            }
            cur = cur.parent
            depth++
        }
        return best
    }

    /** Only adjacent siblings (within ~250px) — avoids pulling in whole QS panel */
    private fun getAdjacentSiblingTexts(node: AccessibilityNodeInfo): String {
        val sb = StringBuilder()
        try {
            val parent = node.parent ?: return ""
            val nr = Rect(); node.getBoundsInScreen(nr)

            for (i in 0 until parent.childCount) {
                val sibling = parent.getChild(i) ?: continue
                if (sibling === node) continue

                val sr = Rect(); sibling.getBoundsInScreen(sr)
                val dx = Math.abs(sr.centerX() - nr.centerX())
                val dy = Math.abs(sr.centerY() - nr.centerY())

                // Only nearby siblings
                if (dx > 300 && dy > 300) continue

                sibling.text?.toString()?.let { if (it.isNotEmpty()) sb.append(" ").append(it) }
                sibling.contentDescription?.toString()?.let { if (it.isNotEmpty()) sb.append(" ").append(it) }
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
