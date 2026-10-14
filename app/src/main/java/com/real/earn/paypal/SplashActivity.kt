package com.real.earn.paypal

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.real.earn.paypal.auth.AuthManager
import com.real.earn.paypal.databinding.ActivitySplashBinding

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val authManager = AuthManager(this)

        Handler(Looper.getMainLooper()).postDelayed({
            val next = if (authManager.isLoggedIn()) {
                Intent(this, DashboardActivity::class.java)
            } else {
                Intent(this, LoginActivity::class.java)
            }
            startActivity(next)
            finish()
        }, 1800L)
    }
}
