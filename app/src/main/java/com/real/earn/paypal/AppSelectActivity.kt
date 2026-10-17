package com.real.earn.paypal

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.real.earn.paypal.databinding.ActivityAppSelectBinding

class AppSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAppSelectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAppSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }

        // PayPal Option
        binding.cardPayPal.setOnClickListener {
            val intent = Intent(this, ServiceSelectActivity::class.java)
            startActivity(intent)
        }

        // TikTok Option
        binding.cardTikTok.setOnClickListener {
            val intent = Intent(this, PermissionActivity::class.java).apply {
                putExtra("mode", "tiktok")
                putExtra("service", "TikTok")
            }
            startActivity(intent)
        }
    }
}
