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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServiceSelectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)

        binding.btnBack.setOnClickListener { finish() }

        // ═══ TOP: Select Services ═══
        binding.cardServices.setOnClickListener {
            // Purana flow — ab alag activity nahi, isi activity ka hissa nahi.
            // Chalo simple: ek naya Intent → ServiceListActivity? Nahi.
            // Direct: is activity ka "old" version reload? Nahi.
            // Simplest: dedicated button → ab ServiceSelect replaced hai, toh use
            // cardServices ka kaam yahan hi krte hain — matlab Services list yahin
            // dikhani hai. Lekin hum alag screen preserve karenge:
            startActivity(Intent(this, ServiceBrowseActivity::class.java))
        }

        // ═══ AUTO MODE ═══
        binding.cardAuto.setOnClickListener {
            checkAutoPrefixesAndGo()
        }

        // ═══ SEARCH PREFIX MODE ═══
        binding.cardPrefix.setOnClickListener {
            startActivity(Intent(this, PrefixInputActivity::class.java))
        }
    }

    /**
     * Auto mode click → pehle check karo admin ne prefix config kiya hai ya nahi.
     * Agar list empty ho → Toast message.
     * Warna → PermissionActivity with mode=auto
     */
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
                        Toast.makeText(
                            this@ServiceSelectActivity,
                            "Admin ne abhi koi prefix configure nahi kiya hai",
                            Toast.LENGTH_LONG
                        ).show()
                        return@launch
                    }

                    // Go to PermissionActivity with mode=auto
                    val intent = Intent(this@ServiceSelectActivity, PermissionActivity::class.java).apply {
                        putExtra("mode", "auto")
                        putExtra("service", "PayPal")
                    }
                    startActivity(intent)

                } else if (res.code() == 401) {
                    Toast.makeText(this@ServiceSelectActivity, "Session khatam. Login karo.", Toast.LENGTH_LONG).show()
                    authManager.clear()
                    startActivity(
                        Intent(this@ServiceSelectActivity, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                } else {
                    Toast.makeText(
                        this@ServiceSelectActivity,
                        "Prefixes load nahi ho sake (code ${res.code()})",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Toast.makeText(
                    this@ServiceSelectActivity,
                    "Network error: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}
