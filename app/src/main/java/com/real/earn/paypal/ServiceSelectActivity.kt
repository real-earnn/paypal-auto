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

                    val allServices = res.body()?.services ?: emptyList()

                    // 🟢 Filter: sirf woh services jinki countries non-empty hain
                    val services = allServices.filter { it.countriesSafe.isNotEmpty() }

                    if (services.isEmpty()) {
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.tvEmpty.text = "Abhi koi service available nahi hai"
                    } else {
                        binding.recycler.adapter = ServiceAdapter(services) { s ->
                            val i = Intent(this@ServiceSelectActivity, CountrySelectActivity::class.java)
                            i.putExtra("service", s.name)
                            i.putExtra("is_unpaid", s.isUnpaid)
                            startActivity(i)
                        }
                    }

                } else if (res.code() == 401) {
                    Toast.makeText(
                        this@ServiceSelectActivity,
                        "Session khatam. Login karo.",
                        Toast.LENGTH_LONG
                    ).show()
                    authManager.clear()
                    startActivity(
                        Intent(this@ServiceSelectActivity, LoginActivity::class.java)
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
