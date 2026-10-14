package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import com.real.earn.paypal.service.AutomationState

object PayPalClearData {

    const val PAYPAL_PKG = "com.paypal.android.p2pmobile"

    fun strictClear(service: AccessibilityService): Boolean {
        return try {
            AutomationState.log("🧹 Clear data...")

            // ① Open App Info
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$PAYPAL_PKG")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            service.startActivity(intent)

            // ② Wait for Settings package foreground
            if (!StepUtils.waitForPackage(service, "settings", 4000) &&
                !StepUtils.waitForPackage(service, "android", 1500)) {
                AutomationState.log("✘ App Info not opened")
                exit(service); return false
            }
            StepUtils.sleepMs(900)

            // ③ Wait for "Storage" row
            if (!waitFor(service, 4000, "Storage & cache", "Storage and cache", "Storage", "Memory")) {
                AutomationState.log("✘ Storage row not found")
                exit(service); return false
            }

            // ④ Tap Storage
            if (!tap(service, "Storage & cache", "Storage and cache", "Storage", "Memory")) {
                AutomationState.log("✘ Storage tap fail")
                exit(service); return false
            }
            StepUtils.sleepMs(1200)

            // ⑤ Wait for "Clear storage" row
            if (!waitFor(service, 3500, "Clear storage", "Clear data", "Clear all data", "Delete data")) {
                AutomationState.log("✘ Clear row not found")
                exit(service); return false
            }

            // ⑥ Tap Clear
            if (!tap(service, "Clear storage", "Clear data", "Clear all data", "Delete data")) {
                AutomationState.log("✘ Clear tap fail")
                exit(service); return false
            }
            StepUtils.sleepMs(700)

            // ⑦ Optional confirm dialog
            if (waitFor(service, 2000, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")) {
                tap(service, "Delete", "Clear", "OK", "Yes", "Confirm", "Erase")
                AutomationState.log("ℹ Confirm popup handled")
                StepUtils.sleepMs(1800)
            } else {
                AutomationState.log("ℹ No popup — already cleared or auto-confirmed")
            }

            // ⑧ Exit and Home
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
            if (StepUtils.shouldStop()) return false
            val root = service.rootInActiveWindow
            if (root != null) {
                val node = NodeFinder.findByText(root, *texts)
                    ?: NodeFinder.findByTextContains(root, *texts)
                if (node != null) return true
            }
            StepUtils.sleepMs(300)
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
                StepUtils.sleepMs(450)
            }
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            StepUtils.sleepMs(900)
        } catch (_: Exception) {}
    }
}
