package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import com.real.earn.paypal.service.AutomationState

object StepUtils {

    const val POLL_MS = 60L

    fun shouldStop(): Boolean =
        AutomationState.getStatus() == AutomationState.Status.STOPPING

    fun sleepMs(ms: Long) {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) {
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) return
            while (AutomationState.getStatus() == AutomationState.Status.PAUSED) {
                try { Thread.sleep(150) } catch (_: InterruptedException) { return }
            }
            val rem = end - SystemClock.uptimeMillis()
            if (rem <= 0) break
            try { Thread.sleep(minOf(rem, POLL_MS)) } catch (_: InterruptedException) { return }
        }
    }

    /**
     * Run a step up to 3 times with progressive delay: 1s, 3s, 5s.
     */
    fun retryStep(stepName: String, block: () -> Boolean): Boolean {
        val delays = longArrayOf(1000L, 3000L, 5000L)
        var lastError = ""

        for (attempt in 0 until 3) {
            if (shouldStop()) return false
            if (attempt > 0) {
                AutomationState.log("ℹ Retry #$attempt for $stepName (wait ${delays[attempt]}ms)")
                sleepMs(delays[attempt])
            }
            try {
                if (block()) return true
            } catch (e: Exception) {
                lastError = e.message ?: "unknown"
            }
        }
        AutomationState.log("✘ $stepName failed after 3 attempts ${if (lastError.isNotEmpty()) "($lastError)" else ""}")
        return false
    }

    fun waitForPackage(service: AccessibilityService, keyword: String, timeoutMs: Long): Boolean {
        val kw = keyword.lowercase()
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (shouldStop()) return false
            val pkg = service.rootInActiveWindow?.packageName?.toString()?.lowercase() ?: ""
            if (pkg.contains(kw)) return true
            sleepMs(POLL_MS)
        }
        return false
    }

    fun waitForPackageExit(service: AccessibilityService, keyword: String, timeoutMs: Long): Boolean {
        val kw = keyword.lowercase()
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (shouldStop()) return false
            val pkg = service.rootInActiveWindow?.packageName?.toString()?.lowercase() ?: ""
            if (!pkg.contains(kw)) return true
            sleepMs(POLL_MS)
        }
        return false
    }

    fun isPackageForeground(service: AccessibilityService, keyword: String): Boolean {
        val pkg = service.rootInActiveWindow?.packageName?.toString()?.lowercase() ?: ""
        return pkg.contains(keyword.lowercase())
    }

    fun waitForPackageOrText(
        service: AccessibilityService,
        keyword: String,
        timeoutMs: Long,
        vararg texts: String
    ): Boolean {
        val kw = keyword.lowercase()
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                val pkg = root.packageName?.toString()?.lowercase() ?: ""
                if (pkg.contains(kw)) return true
                if (texts.isNotEmpty() && NodeFinder.containsAny(root, *texts)) return true
            }
            sleepMs(POLL_MS)
        }
        return false
    }
}
