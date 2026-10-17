package com.real.earn.paypal.admin

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.util.Log

class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DevicePolicyManager.ACTION_MANAGED_PROFILE_PROVISIONED -> {
                Log.d("DPC", "Received explicit ACTION_MANAGED_PROFILE_PROVISIONED on Personal Profile")
                WorkProfileProvisioningManager.markSuccess()
            }
        }
        super.onReceive(context, intent)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        super.onProfileProvisioningComplete(context, intent)
        Log.d("DPC", "Running post-setup tasks inside Work Profile")
        
        val prefs = context.getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("profile_created", true).apply()
    }
}
