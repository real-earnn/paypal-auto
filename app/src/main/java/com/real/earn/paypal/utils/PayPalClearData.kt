package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import com.real.earn.paypal.service.AutomationState

/**
 * App Info → Storage → Clear Storage → (popup optional) → HOME
 * Agar popup na aaye to samjho already cleared hai.
 */
object PayPalClearData {

    const val PAYPAL_PKG = "com.paypal.android.p2pmobile"

    fun strictClear(service: AccessibilityService): Boolean {
        return try {
            AutomationState.log("🧹 Clear data...")

            // Open App Info
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$PAYPAL_PKG")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            service.startActivity(intent)

            // Wait for "Storage" row
            if (!waitFor(service, 4000, "Storage & cache", "Storage and cache", "Storage", "Memory")) {
                AutomationState.log("✘ Storage row not found")
                exit(service); return false
            }

            // Tap Storage
            tap(service, "Storage & cache", "Storage and cache", "Storage", "Memory")
            sleepMs(1200)

            // Wait for "Clear storage" row
            if (!waitFor(service, 3000, "Clear storage", "Clear data", "Clear all data", "Delete data")) {
                AutomationState.log("✘ Clear row not found")
                exit(service); return false
            }

            // Tap Clear
            tap(service, "Clear storage", "Clear data", "Clear all data", "Delete data")
            sleepMs(700)

            // Popup optional — if appears, tap confirm; else assume already cleared
            if (waitFor(service, 2000, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")) {
                tap(service, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")
                AutomationState.log("ℹ Confirm popup handled")
                sleepMs(1800)
            } else {
                AutomationState.log("ℹ No popup — assuming already cleared")
            }

            exit(service)
            AutomationState.log("✔ Clear data done")
            true
        } catch (e: Exception) {
            AutomationState.log("✘ Clear error: ${e.message}")
            exit(service)
            false
        }
    }

    private fun waitFor(service: AccessibilityService, timeoutMs: Long, vararg texts: String): Boolean {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            val root = service.rootInActiveWindow
            if (root != null) {
                val node = NodeFinder.findByText(root, *texts)
                    ?: NodeFinder.findByTextContains(root, *texts)
                if (node != null) return true
            }
            sleepMs(300)
        }
        return false
    }

    private fun tap(service: AccessibilityService, vararg texts: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = NodeFinder.findByText(root, *texts)
            ?: NodeFinder.findByTextContains(root, *texts)
            ?: return false
        return NodeFinder.click(node)
    }

    private fun exit(service: AccessibilityService) {
        try {
            repeat(3) {
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                sleepMs(400)
            }
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            sleepMs(900)
        } catch (_: Exception) {}
    }

    private fun sleepMs(ms: Long) {
        try { Thread.sleep(ms) } catch (_: InterruptedException) {}
    }
}
