package com.real.earn.paypal.admin

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    // 🟢 پروفائل مکمل ہونے کا سگنل وصول کریں
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.d("WorkProfile", "Provisioning Complete Broadcast Received!")
        
        // SharedPreferences میں محفوظ کریں تاکہ مین ایپ کو پتہ چلے
        val prefs = context.getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("profile_created", true).apply()
    }
}
