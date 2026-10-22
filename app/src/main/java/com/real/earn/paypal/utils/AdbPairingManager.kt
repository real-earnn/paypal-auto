package com.real.earn.paypal.utils

import android.content.Context

/**
 * 🟢 DEPRECATED — Kadb dependency hata di gayi hai.
 * Ye class sirf backward compatibility ke liye hai. Koi functionality nahi.
 * Nayi code me ShizukuShell use karo.
 */
@Deprecated(
    "Use ShizukuShell instead",
    ReplaceWith("ShizukuShell", "com.real.earn.paypal.utils.ShizukuShell")
)
object AdbPairingManager {

    fun initialize(context: Context) { /* no-op */ }

    fun isPairingSupported(): Boolean = false

    suspend fun pair(
        context: Context,
        host: String,
        pairingPort: Int,
        code: String
    ): Pair<Boolean, String> = false to "Deprecated — use Shizuku"

    suspend fun discoverConnectPort(
        context: Context,
        host: String,
        timeoutMs: Long = 0L
    ): Int? = null

    fun savePairing(context: Context, host: String, pairingPort: Int, connectPort: Int) { /* no-op */ }

    fun clearPairing(context: Context) { /* no-op */ }

    suspend fun isConnected(context: Context): Boolean = false

    suspend fun executeShell(context: Context, command: String): ShellOutcome? = null

    data class ShellOutcome(val exitCode: Int, val output: String)
}
