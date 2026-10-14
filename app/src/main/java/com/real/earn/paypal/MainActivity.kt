package com.real.earn.paypal

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.real.earn.paypal.auth.AuthManager
import com.real.earn.paypal.databinding.ActivityMainBinding

/**
 * MainActivity — ab yeh launcher nahi hai.
 * Sirf ek utility/placeholder activity hai. Splash launcher ban gayi hai.
 *
 * Agar kahin se is activity ko open karna ho, to yeh token check kar ke
 * sahi screen pe bhej degi.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Token check — agar logged in to Dashboard, warna Login
        val authManager = AuthManager(this)
        val next = if (authManager.isLoggedIn()) {
            Intent(this, DashboardActivity::class.java)
        } else {
            Intent(this, LoginActivity::class.java)
        }
        startActivity(next)
        finish()
    }
}
