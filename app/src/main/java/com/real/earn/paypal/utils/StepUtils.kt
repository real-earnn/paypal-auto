package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import com.real.earn.paypal.service.AutomationState

object StepUtils {

    fun shouldStop(): Boolean =
        AutomationState.getStatus() == AutomationState.Status.STOPPING

    fun sleepMs(ms: Long) {
        val end = SystemClock.uptimeMillis() + ms
        while (SystemClock.uptimeMillis() < end) {
            if (AutomationState.getStatus() == AutomationState.Status.STOPPING) return
            while (AutomationState.getStatus() == AutomationState.Status.PAUSED) {
                try { Thread.sleep(200) } catch (_: InterruptedException) { return }
            }
            val rem = end - SystemClock.uptimeMillis()
            if (rem <= 0) break
            try { Thread.sleep(minOf(rem, 200)) } catch (_: InterruptedException) { return }
        }
    }

    fun waitForPackage(
        service: AccessibilityService,
        packageKeyword: String,
        timeoutMs: Long
    ): Boolean {
        val kw = packageKeyword.lowercase()
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (shouldStop()) return false
            val pkg = service.rootInActiveWindow?.packageName?.toString()?.lowercase() ?: ""
            if (pkg.contains(kw)) return true
            sleepMs(250)
        }
        return false
    }

    fun waitForPackageExit(
        service: AccessibilityService,
        packageKeyword: String,
        timeoutMs: Long
    ): Boolean {
        val kw = packageKeyword.lowercase()
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (shouldStop()) return false
            val pkg = service.rootInActiveWindow?.packageName?.toString()?.lowercase() ?: ""
            if (!pkg.contains(kw)) return true
            sleepMs(200)
        }
        return false
    }

    fun isPackageForeground(service: AccessibilityService, packageKeyword: String): Boolean {
        val pkg = service.rootInActiveWindow?.packageName?.toString()?.lowercase() ?: ""
        return pkg.contains(packageKeyword.lowercase())
    }

    fun waitForPackageOrText(
        service: AccessibilityService,
        packageKeyword: String,
        timeoutMs: Long,
        vararg texts: String
    ): Boolean {
        val kw = packageKeyword.lowercase()
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                val pkg = root.packageName?.toString()?.lowercase() ?: ""
                if (pkg.contains(kw)) return true
                if (texts.isNotEmpty() && NodeFinder.containsAny(root, *texts)) return true
            }
            sleepMs(300)
        }
        return false
    }
}
