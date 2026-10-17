package com.real.earn.paypal.utils

import android.content.Context
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.os.UserHandle
import androidx.core.content.FileProvider
import com.real.earn.paypal.WorkProfileInstallerActivity
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ApkSharer {

    fun shareApkToWorkProfile(context: Context, packageName: String, workProfileUserHandle: UserHandle) {
        try {
            // 1. APK کا راستہ حاصل کریں
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            val sourceApkPath = appInfo.sourceDir
            val sourceApkFile = File(sourceApkPath)

            // 2. اسے کیش ڈائریکٹری میں کاپی کریں
            val cacheDir = File(context.cacheDir, "shared_apks")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            
            val sharedApkFile = File(cacheDir, "${packageName}.apk")
            
            FileInputStream(sourceApkFile).use { input ->
                FileOutputStream(sharedApkFile).use { output ->
                    input.copyTo(output)
                }
            }

            // 3. FileProvider URI بنائیں
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                sharedApkFile
            )

            // 4. ایکٹیویٹی لانچ کرنے کا انٹینٹ (کیونکہ CrossProfileApps صرف Activities کے لیے ہے)
            val intent = Intent(context, WorkProfileInstallerActivity::class.java).apply {
                putExtra("apk_uri", apkUri.toString())
                putExtra("package_name", packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            // 5. CrossProfileApps کے ذریعے ورک پروفائل میں لانچ کریں
            val crossProfileApps = context.getSystemService(Context.CROSS_PROFILE_APPS_SERVICE) as CrossProfileApps
            
            // 🟢 FIX: Arguments order is (Intent, UserHandle)
            crossProfileApps.startActivity(intent, workProfileUserHandle)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
