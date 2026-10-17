package com.real.earn.paypal

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.real.earn.paypal.api.ApiClient
import com.real.earn.paypal.auth.AuthManager
import com.real.earn.paypal.databinding.ActivityDashboardBinding
import kotlinx.coroutines.launch

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)

        binding.btnRefresh.setOnClickListener { loadMe() }
        binding.btnLogout.setOnClickListener { logout() }
        
        binding.btnStartAutomation.setOnClickListener {
            startActivity(Intent(this, AppSelectActivity::class.java))
        }

        binding.btnVirtualProfile.setOnClickListener {
            startActivity(Intent(this, VirtualProfileActivity::class.java))
        }

        loadMe()
    }

    override fun onResume() {
        super.onResume()
        updateVirtualProfileButtonText()
    }

private fun updateVirtualProfileButtonText() {
    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val isOwner = dpm.isProfileOwnerApp(packageName)
    
    val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val myHandle = Process.myUserHandle()
    val hasWorkProfile = launcherApps.profiles.any { it != myHandle } || isOwner
    
    val prefs = getSharedPreferences("virtual_prefs", Context.MODE_PRIVATE)
    val prefCreated = prefs.getBoolean("profile_created", false)

    if (hasWorkProfile || prefCreated) {
        binding.btnVirtualProfile.text = "Show Virtual Android Setup"
        binding.btnVirtualProfile.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4CAF50"))
    } else {
        binding.btnVirtualProfile.text = "Create Virtual Android"
        binding.btnVirtualProfile.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#673AB7"))
    }
}

    private fun loadMe() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.service(authManager).getMe()
                if (response.isSuccessful && response.body()?.success == true) {
                    val user = response.body()?.user
                    if (user != null) {
                        binding.tvName.text = user.firstName.ifEmpty { user.username.ifEmpty { "User #${user.id}" } }
                        binding.tvBalance.text = "%.2f %s".format(user.balance, user.currency)
                        binding.tvTodayOtps.text = user.todayOtps.toString()
                        binding.tvTotalOtps.text = user.totalOtps.toString()
                        binding.tvTotalEarned.text = "%.2f %s".format(user.totalEarned, user.currency)
                    }
                } else if (response.code() == 401) {
                    authManager.clear()
                    goToLogin()
                }
            } catch (_: Exception) {}
        }
    }

    private fun logout() {
        lifecycleScope.launch {
            try { ApiClient.service(authManager).logout() } catch (_: Exception) {}
            authManager.clear()
            goToLogin()
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
