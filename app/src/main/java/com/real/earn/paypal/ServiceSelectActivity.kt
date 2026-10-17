package com.real.earn.paypal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.real.earn.paypal.api.ApiClient
import com.real.earn.paypal.auth.AuthManager
import com.real.earn.paypal.databinding.ActivityServiceSelectBinding
import kotlinx.coroutines.launch

class ServiceSelectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServiceSelectBinding
    private lateinit var authManager: AuthManager
    private var engineType: String = "paypal"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServiceSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)
        engineType = intent.getStringExtra("engine_type") ?: "paypal"

        binding.btnBack.setOnClickListener { finish() }

        binding.cardServices.setOnClickListener {
            val intent = Intent(this, ServiceBrowseActivity::class.java)
            intent.putExtra("engine_type", engineType)
            startActivity(intent)
        }

        binding.cardAuto.setOnClickListener {
            checkAutoPrefixesAndGo()
        }

        binding.cardPrefix.setOnClickListener {
            val intent = Intent(this, PrefixInputActivity::class.java)
            intent.putExtra("engine_type", engineType)
            startActivity(intent)
        }
    }

    private fun checkAutoPrefixesAndGo() {
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val res = ApiClient.service(authManager).getPrefixesList()
                binding.progressBar.visibility = View.GONE

                if (res.isSuccessful && res.body()?.success == true) {
                    val prefixes = res.body()?.prefixes ?: emptyList()
                    val hasValid = prefixes.any { it.filesSafe.isNotEmpty() }

                    if (!hasValid) {
                        Toast.makeText(this@ServiceSelectActivity, "Admin ne abhi koi prefix configure nahi kiya hai", Toast.LENGTH_LONG).show()
                        return@launch
                    }

                    val intent = Intent(this@ServiceSelectActivity, PermissionActivity::class.java).apply {
                        putExtra("mode", "auto")
                        putExtra("service", if (engineType == "tiktok") "TikTok" else "PayPal")
                        putExtra("engine_type", engineType)
                    }
                    startActivity(intent)

                } else if (res.code() == 401) {
                    Toast.makeText(this@ServiceSelectActivity, "Session khatam. Login karo.", Toast.LENGTH_LONG).show()
                    authManager.clear()
                    startActivity(Intent(this@ServiceSelectActivity, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    finish()
                } else {
                    Toast.makeText(this@ServiceSelectActivity, "Prefixes load nahi ho sake (code ${res.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(this@ServiceSelectActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
