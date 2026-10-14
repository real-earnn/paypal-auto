package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import com.real.earn.paypal.service.AutomationState

/**
 * Shared helpers for automation steps:
 * - Interruptible sleep (respects Pause / Stop)
 * - Wait for a specific package to be foreground
 * - Check abort flag
 */
object StepUtils {

    /** Returns true if we should abort (stopping). */
    fun shouldStop(): Boolean =
        AutomationState.getStatus() == AutomationState.Status.STOPPING

    /** Sleeps but: pauses while PAUSED, aborts if STOPPING. */
    fun sleepMs(ms: Long) {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) {
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) return
            // Wait for resume
            while (AutomationState.getStatus() == AutomationState.Status.PAUSED) {
                try { Thread.sleep(200) } catch (_: InterruptedException) { return }
            }
            val rem = end - SystemClock.uptimeMillis()
            if (rem <= 0) break
            try { Thread.sleep(minOf(rem, 200)) } catch (_: InterruptedException) { return }
        }
    }

    /**
     * Wait until foreground package matches (contains keyword).
     * Returns true if matched, false on timeout or abort.
     */
    fun waitForPackage(
        service: AccessibilityService,
        packageKeyword: String,
        timeoutMs: Long
    ): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (shouldStop()) return false
            val pkg = service.rootInActiveWindow?.packageName?.toString() ?: ""
            if (pkg.contains(packageKeyword)) return true
            sleepMs(250)
        }
        return false
    }

    /**
     * Wait until foreground package does NOT contain keyword.
     */
    fun waitForPackageExit(
        service: AccessibilityService,
        packageKeyword: String,
        timeoutMs: Long
    ): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (shouldStop()) return false
            val pkg = service.rootInActiveWindow?.packageName?.toString() ?: ""
            if (!pkg.contains(packageKeyword)) return true
            sleepMs(200)
        }
        return false
    }
}
