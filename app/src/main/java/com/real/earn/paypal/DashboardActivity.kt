package com.real.earn.paypal

import android.content.Intent
import android.os.Bundle
import android.view.View
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

        // 🟢 Virtual Android button — feature removed, button hidden
        binding.btnVirtualProfile.visibility = View.GONE

        binding.btnRefresh.setOnClickListener { loadMe() }
        binding.btnLogout.setOnClickListener { logout() }

        binding.btnStartAutomation.setOnClickListener {
            startActivity(Intent(this, AppSelectActivity::class.java))
        }

        loadMe()
    }

    private fun loadMe() {
        lifecycleScope.launch {
            try {
                val response = ApiClient.service(authManager).getMe()
                if (response.isSuccessful && response.body()?.success == true) {
                    val user = response.body()?.user
                    if (user != null) {
                        binding.tvName.text = user.firstName.ifEmpty {
                            user.username.ifEmpty { "User #${user.id}" }
                        }
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
