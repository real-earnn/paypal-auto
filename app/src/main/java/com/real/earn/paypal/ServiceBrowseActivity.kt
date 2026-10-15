package com.real.earn.paypal

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.real.earn.paypal.adapter.ServiceAdapter
import com.real.earn.paypal.api.ApiClient
import com.real.earn.paypal.auth.AuthManager
import com.real.earn.paypal.databinding.ActivityServiceBrowseBinding
import kotlinx.coroutines.launch

/**
 * Service Browse — purana ServiceSelect tha, rename kiya.
 * Isme service list dikhti hai (PayPal, WhatsApp, etc.)
 */
class ServiceBrowseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityServiceBrowseBinding
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityServiceBrowseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        authManager = AuthManager(this)
        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.btnBack.setOnClickListener { finish() }

        loadServices()
    }

    private fun loadServices() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val res = ApiClient.service(authManager).getServices()
                if (res.isSuccessful && res.body()?.success == true) {
                    val services = res.body()?.services ?: emptyList()
                    if (services.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.tvEmpty.text = "Abhi koi service available nahi hai"
                    } else {
                        binding.recycler.adapter = ServiceAdapter(services) { s ->
                            if (s.countriesSafe.isEmpty()) {
                                Toast.makeText(
                                    this@ServiceBrowseActivity,
                                    "${s.name} mein abhi koi country available nahi",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                val i = Intent(this@ServiceBrowseActivity, CountrySelectActivity::class.java)
                                i.putExtra("service", s.name)
                                i.putExtra("is_unpaid", s.isUnpaid)
                                startActivity(i)
                            }
                        }
                    }
                } else if (res.code() == 401) {
                    Toast.makeText(this@ServiceBrowseActivity, "Session khatam. Login karo.", Toast.LENGTH_LONG).show()
                    authManager.clear()
                    startActivity(
                        Intent(this@ServiceBrowseActivity, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    finish()
                } else {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.tvEmpty.text = "Services load nahi hue (code ${res.code()})"
                }
            } catch (e: Exception) {
                binding.tvEmpty.visibility = View.VISIBLE
                binding.tvEmpty.text = "Network error: ${e.message}"
            } finally {
                binding.progressBar.visibility = View.GONE
            }
        }
    }
}
