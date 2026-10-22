package com.real.earn.paypal.utils

import android.content.Context

/**
 * 🟢 DEPRECATED — Kadb dependency hata di gayi hai.
 * Ye class sirf backward compatibility ke liye hai.
 * Nayi code me ShizukuShell use karo.
 */
@Deprecated(
    "Use ShizukuShell instead",
    ReplaceWith("ShizukuShell", "com.real.earn.paypal.utils.ShizukuShell")
)
object SystemActionsViaAdb {

    suspend fun cycleAirplaneMode(context: Context, cycles: Int = 5): Boolean? = null

    suspend fun clearAppData(context: Context, packageName: String): Boolean? = null

    suspend fun forceStopApp(context: Context, packageName: String): Boolean? = null

    suspend fun launchApp(context: Context, packageName: String): Boolean? = null
}
