package com.real.earn.paypal.utils

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log

/**
 * Clear PayPal app data via App Info screen using Accessibility.
 * Non-rooted, no ADB needed.
 */
object PayPalClearData {

    private const val TAG = "ClearData"
    const val PAYPAL_PKG = "com.paypal.android.p2pmobile"

    fun clear(service: AccessibilityService): Boolean {
        return try {
            AutomationLog("🧹 Clearing PayPal data...")

            // 1. Open app details
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$PAYPAL_PKG")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            service.startActivity(intent)
            Thread.sleep(1800)

            // 2. Tap "Storage & cache" / "Storage"
            tapByTexts(
                service,
                "Storage & cache", "Storage and cache",
                "Storage", "Memory", "Storage usage"
            )
            Thread.sleep(1400)

            // 3. Tap "Clear storage" / "Clear data"
            tapByTexts(
                service,
                "Clear storage", "Clear data", "Clear all data",
                "Clear app data", "Delete data"
            )
            Thread.sleep(1400)

            // 4. Confirm dialog
            tapByTexts(
                service,
                "Delete", "Clear", "OK", "Yes", "Confirm", "Erase"
            )
            Thread.sleep(1600)

            // 5. Go home
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
            Thread.sleep(1000)

            AutomationLog("✔ Data cleared")
            true
        } catch (e: Exception) {
            Log.e(TAG, "clear failed: ${e.message}")
            AutomationLog("✘ Clear data error: ${e.message}")
            false
        }
    }

    private fun tapByTexts(service: AccessibilityService, vararg texts: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        // Exact text first
        NodeFinder.findByText(root, *texts)?.let {
            if (NodeFinder.click(it)) return true
        }
        // Contains
        NodeFinder.findByTextContains(root, *texts)?.let {
            if (NodeFinder.click(it)) return true
        }
        return false
    }

    private fun AutomationLog(msg: String) {
        try {
            val cls = Class.forName("com.real.earn.paypal.service.AutomationState")
            val logMethod = cls.getMethod("log", String::class.java)
            logMethod.invoke(cls.getField("INSTANCE").get(null), msg)
        } catch (_: Exception) { }
    }
}
