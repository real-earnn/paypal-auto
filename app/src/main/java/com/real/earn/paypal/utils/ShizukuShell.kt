package com.real.earn.paypal.utils

import android.content.pm.PackageManager
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.lang.reflect.Method

/**
 * 🟢 Shizuku Shell — ADB pairing کی جگہ
 *
 * User صرف Shizuku app install کرتا ہے اور wireless debugging سے ایک بار activate کرتا ہے،
 * پھر ہم اس کے ذریعے shell commands چلا سکتے ہیں (Android 11+).
 */
object ShizukuShell {

    private const val REQUEST_CODE = 1001

    /** کیا Shizuku app فون پر انسٹال ہے اور service چل رہی ہے؟ */
    fun isAvailable(): Boolean = try {
        Shizuku.pingBinder()
    } catch (e: Exception) {
        false
    }

    /**
     * کیا ہمیں ADB shell کی اجازت مل چکی ہے؟
     * Block body use کیا تاکہ `return` expression body میں نہ ہو.
     */
    fun hasPermission(): Boolean {
        return try {
            if (!isAvailable()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Exception) {
            false
        }
    }

    /** Shizuku سے permission مانگیں (یہ system dialog دکھاتا ہے) */
    fun requestPermission() {
        try {
            if (!isAvailable()) return
            if (hasPermission()) return
            Shizuku.requestPermission(REQUEST_CODE)
        } catch (e: Exception) {
            AutomationState.log("⚠ Shizuku permission request failed: ${e.message}")
        }
    }

    /**
     * 🟢 Reflection-based process creation.
     *
     * Shizuku 13.1.5 میں `newProcess()` private ہے، لہٰذا reflection سے access کرتے ہیں.
     * Signature: static ShizukuRemoteProcess newProcess(String[] cmd, String[] env, String dir)
     */
    private fun createShizukuProcess(command: Array<String>): Process? {
        return try {
            val stringArrayClass: Class<*> = emptyArray<String>()::class.java

            val method: Method = try {
                // Primary: method directly in rikka.shizuku.Shizuku
                Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    stringArrayClass,
                    stringArrayClass,
                    String::class.java
                )
            } catch (e: NoSuchMethodException) {
                // Fallback: companion class (اگر مستقبل میں Shizuku Companion میں move کرے)
                Class.forName("rikka.shizuku.Shizuku\$Companion").getDeclaredMethod(
                    "newProcess",
                    stringArrayClass,
                    stringArrayClass,
                    String::class.java
                )
            }

            method.isAccessible = true
            method.invoke(null, command, null, null) as? Process
        } catch (e: Exception) {
            AutomationState.log("✘ Shizuku process error: ${e.message}")
            null
        }
    }

    /**
     * Shell command چلائیں.
     * @return ShellResult — null اگر Shizuku available/permitted نہیں
     */
    suspend fun executeShell(command: String): ShellResult? {
        return withContext(Dispatchers.IO) {
            if (!isAvailable() || !hasPermission()) {
                return@withContext null
            }
            try {
                val process = createShizukuProcess(arrayOf("sh", "-c", command))
                    ?: return@withContext null

                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                val exitCode = process.waitFor()

                if (exitCode != 0) {
                    AutomationState.log("⚠ Shell exit $exitCode: $command → $stderr")
                }
                ShellResult(exitCode, stdout.trim())
            } catch (e: Exception) {
                AutomationState.log("✘ Shell error: ${e.message}")
                null
            }
        }
    }

    /**
     * ایک ساتھ multiple commands (fast — single shell session)
     */
    suspend fun executeShellMulti(commands: List<String>): List<ShellResult> {
        return commands.map { cmd -> executeShell(cmd) ?: ShellResult(-1, "") }
    }

    // ── Convenience wrappers ────────────────────────────────

    /** Flight mode on/off */
    suspend fun setAirplaneMode(enable: Boolean): Boolean {
        val cmd = if (enable)
            "cmd connectivity airplane-mode enable"
        else
            "cmd connectivity airplane-mode disable"
        val primary = executeShell(cmd)
        if (primary != null && primary.exitCode == 0) return true

        // Fallback (older APIs)
        val fallback = if (enable)
            "settings put global airplane_mode_on 1; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state true"
        else
            "settings put global airplane_mode_on 0; am broadcast -a android.intent.action.AIRPLANE_MODE --ez state false"
        val fb = executeShell(fallback)
        return fb?.exitCode == 0
    }

    /** Clear app data */
    suspend fun clearAppData(packageName: String): Boolean {
        val r = executeShell("pm clear $packageName")
        return r?.exitCode == 0
    }

    /** Force stop app */
    suspend fun forceStopApp(packageName: String): Boolean {
        val r = executeShell("am force-stop $packageName")
        return r?.exitCode == 0
    }

    /** Launch app via monkey */
    suspend fun launchApp(packageName: String): Boolean {
        val r = executeShell("monkey -p $packageName -c android.intent.category.LAUNCHER 1")
        return r?.exitCode == 0
    }

    data class ShellResult(val exitCode: Int, val output: String)
}
