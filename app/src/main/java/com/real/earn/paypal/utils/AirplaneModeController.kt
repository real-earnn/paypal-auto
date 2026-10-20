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
import kotlinx.coroutines.runBlocking

object AirplaneModeController {

    private val AIRPLANE_KEYWORDS = arrayOf(
        "airplane", "aeroplane", "air plane", "air-plane",
        "flight mode", "flight-mode", "in-flight", "inflight", "flight",
        "ایروپلین", "ایرو پلین", "فلائٹ", "ہوائی جہاز", "ہوائی", "طیارہ",
        "एयरप्लेन", "एयरोप्लेन", "फ्लाइट", "हवाई जहाज", "हवाई जहाज़", "विमान",
        "طيران", "الطيران", "وضع الطيران",
        "هواپیما", "پرواز", "حالت هواپیما",
        "uçak", "ucak", "uçuş", "ucus",
        "বিমান", "ফ্লাইট",
        "mode pesawat", "pesawat",
        "самолет", "самолёт", "авиарежим", "полет", "полёт",
        "mode avion", "avion",
        "modo avión", "modo avion", "avión", "avion",
        "flugmodus", "flug",
        "modo avião", "modo aviao",
        "modalità aereo", "modalita aereo", "aereo",
    )

    fun toggleCycle(service: AccessibilityService): Boolean = strictToggle(service)

    fun strictToggle(service: AccessibilityService): Boolean {
        // ══════════════════════════════════════════════════════════════
        // 🟢 ADB-FIRST: fast, device-agnostic, no tile hunting
        // ══════════════════════════════════════════════════════════════
        val adbResult = runBlocking {
            SystemActionsViaAdb.cycleAirplaneMode(service.applicationContext, 5)
        }
        if (adbResult != null) {
            if (adbResult) {
                AutomationState.log("✔ Flight cycle via ADB")
                return true
            } else {
                AutomationState.log("⚠ ADB flight failed — falling back to accessibility")
            }
        }

        // ══════════════════════════════════════════════════════════════
        // FALLBACK: old Quick Settings tile method
        // ══════════════════════════════════════════════════════════════
        return try {
            if (isAirplaneModeOn(service)) {
                AutomationState.log("ℹ Flight was ON — normalizing")
                toggleTo(service, targetOn = false)
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                StepUtils.waitForPackageExit(service, "systemui", 1500)
            }

            AutomationState.log("✈ Flight ON")
            if (!toggleTo(service, targetOn = true)) {
                AutomationState.log("✘ ON failed")
                goHome(service); return false
            }
            AutomationState.log("✔ Flight ON")

            StepUtils.sleepMs(900)

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

    private fun toggleTo(service: AccessibilityService, targetOn: Boolean): Boolean {
        val prefs = AppPrefs(service)

        if (!openQs(service)) {
            AutomationState.log("  ✘ QS not open")
            return false
        }
        StepUtils.sleepMs(700)

        var targetX = prefs.flightTapX
        var targetY = prefs.flightTapY

        if (targetX > 0 && targetY > 0) {
            AutomationState.log("  ℹ Using saved coords ($targetX, $targetY)")
        } else {
            val pick = findTile(service) ?: run {
                AutomationState.log("  ✘ No tile found")
                return false
            }
            val r = Rect(); pick.getBoundsInScreen(r)
            targetX = r.centerX()
            targetY = r.centerY()
            AutomationState.log("  ℹ Tile found at ($targetX, $targetY) size=${r.width()}x${r.height()}")
        }

        for (attempt in 1..4) {
            if (StepUtils.shouldStop()) return false

            AutomationState.log("  ↳ Tap attempt #$attempt at ($targetX, $targetY)")
            tapAt(service, targetX, targetY)

            if (waitAirplaneState(service, targetOn, 3200)) {
                prefs.flightTapX = targetX
                prefs.flightTapY = targetY
                AutomationState.log("  ✔ Success on attempt #$attempt")
                return true
            }

            if (!isQsOpen(service)) {
                AutomationState.log("  ⚠ QS closed after tap #$attempt — reopening")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                if (!StepUtils.waitForPackage(service, "systemui", 2000)) {
                    AutomationState.log("  ✘ QS reopen failed")
                    return false
                }
                StepUtils.sleepMs(600)
            } else {
                StepUtils.sleepMs(400)
            }
        }

        AutomationState.log("  ✘ All 4 tap attempts failed — clearing saved coords")
        prefs.flightTapX = 0
        prefs.flightTapY = 0
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

            val saysAirplane = matchesAirplaneKeyword(dl) ||
                    matchesAirplaneKeyword(tl) ||
                    matchesAirplaneKeyword(il)

            if (saysAirplane && n.isVisibleToUser) {
                val r = Rect(); n.getBoundsInScreen(r)

                var score = 0
                val nodeArea = r.width() * r.height()
                if (nodeArea > (screenArea * 0.6).toInt()) score += 30
                if (r.top < 150) score += 20

                val dlLower = dl.lowercase().trim()
                val tlLower = tl.lowercase().trim()

                val isExactDesc = isExactAirplaneMatch(dlLower)
                val isExactText = isExactAirplaneMatch(tlLower)
                val startsDesc = startsWithAirplane(dlLower)
                val startsText = startsWithAirplane(tlLower)

                when {
                    isExactDesc || isExactText -> score += 0
                    startsDesc -> score += 3
                    startsText -> score += 5
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

    private fun matchesAirplaneKeyword(input: String): Boolean {
        if (input.isEmpty()) return false
        for (kw in AIRPLANE_KEYWORDS) {
            if (input.contains(kw)) return true
        }
        return false
    }

    private fun isExactAirplaneMatch(input: String): Boolean {
        val t = input.trim()
        val exactSet = arrayOf(
            "airplane mode", "aeroplane mode", "air plane mode",
            "flight mode", "in-flight mode", "inflight mode",
            "airplane", "aeroplane", "flight",
            "ایروپلین موڈ", "فلائٹ موڈ", "ہوائی جہاز کا موڈ",
            "एयरप्लेन मोड", "एयरोप्लेन मोड", "फ्लाइट मोड",
            "وضع الطيران", "الطيران",
            "uçak modu", "ucak modu",
        )
        for (e in exactSet) {
            if (t == e) return true
        }
        if ((t.startsWith("airplane") || t.startsWith("aeroplane")) &&
            (t.endsWith("mode") || t == "airplane" || t == "aeroplane")) return true
        return false
    }

    private fun startsWithAirplane(input: String): Boolean {
        val t = input.trim()
        val starts = arrayOf(
            "airplane", "aeroplane", "air plane", "air-plane",
            "flight", "in-flight", "inflight",
            "ایروپلین", "فلائٹ", "ہوائی", "طیارہ",
            "एयरप्लेन", "एयरोप्लेन", "फ्लाइट", "हवाई", "विमान",
            "طيران", "الطيران",
            "هواپیما", "پرواز",
            "uçak", "ucak", "uçuş", "ucus",
            "বিমান", "ফ্লাইট",
            "mode pesawat", "pesawat",
            "самолет", "самолёт", "авиарежим", "полет", "полёт",
            "mode avion", "avion",
            "modo avión", "modo avion", "avión",
            "flugmodus", "flug",
            "modo avião", "modo aviao",
            "modalità aereo", "modalita aereo", "aereo",
        )
        for (s in starts) {
            if (t.startsWith(s)) return true
        }
        return false
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
