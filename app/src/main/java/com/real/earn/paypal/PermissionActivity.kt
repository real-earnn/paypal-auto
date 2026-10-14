package com.real.earn.paypal

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.real.earn.paypal.databinding.ActivityPermissionBinding
import com.real.earn.paypal.service.AutomationService
import com.real.earn.paypal.service.PayPalAccessibilityService
import com.real.earn.paypal.utils.AirplaneModeController
import com.real.earn.paypal.utils.AppPrefs
import com.real.earn.paypal.utils.PermissionHelper

class PermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionBinding
    private lateinit var prefs: AppPrefs

    private var serviceName: String = ""
    private var countryName: String = ""
    private var countryCode: String = ""
    private var dialingCode: String = ""
    private var price: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPrefs(this)

        serviceName = intent.getStringExtra("service") ?: ""
        countryName = intent.getStringExtra("country") ?: ""
        countryCode = intent.getStringExtra("country_code") ?: ""
        dialingCode = intent.getStringExtra("dialing_code") ?: ""
        price = intent.getDoubleExtra("price", 0.0)

        binding.tvTitle.text = "$serviceName — $countryName"

        binding.btnBack.setOnClickListener { finish() }
        binding.rowAccessibility.setOnClickListener { PermissionHelper.openAccessibilitySettings(this) }
        binding.rowOverlay.setOnClickListener { PermissionHelper.openOverlaySettings(this) }
        binding.rowBattery.setOnClickListener { PermissionHelper.openBatterySettings(this) }

        // ── Auto Flight toggle ──
        binding.switchAutoFlight.isChecked = prefs.autoFlightMode
        binding.switchAutoFlight.setOnCheckedChangeListener { _, checked ->
            prefs.autoFlightMode = checked
        }

        binding.btnTestFlight.setOnClickListener { runFlightTest() }

        binding.btnStart.setOnClickListener {
            if (!allGranted()) {
                Toast.makeText(this, "Pehle saari permissions enable karo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, AutomationActivity::class.java).apply {
                putExtra("service", serviceName)
                putExtra("country", countryName)
            })
            AutomationService.start(
                this,
                service = serviceName,
                country = countryName,
                countryCode = countryCode,
                dialingCode = dialingCode,
                price = price
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    private fun refreshUi() {
        setRowState(binding.tvAccessibilityState, PermissionHelper.isAccessibilityEnabled(this))
        setRowState(binding.tvOverlayState, PermissionHelper.isOverlayGranted(this))
        setRowState(binding.tvBatteryState, PermissionHelper.isBatteryOptimizationIgnored(this))
        refreshStartButton()
    }

    private fun refreshStartButton() {
        val ok = allGranted()
        binding.btnStart.isEnabled = ok
        binding.btnStart.alpha = if (ok) 1f else 0.5f
    }

    private fun setRowState(view: TextView, granted: Boolean) {
        if (granted) {
            view.text = "✔ Enabled"
            view.setTextColor(getColor(R.color.success_green))
        } else {
            view.text = "✘ Tap to enable"
            view.setTextColor(getColor(R.color.error_red))
        }
    }

    private fun allGranted(): Boolean {
        return PermissionHelper.isAccessibilityEnabled(this) &&
                PermissionHelper.isOverlayGranted(this) &&
                PermissionHelper.isBatteryOptimizationIgnored(this)
    }

    private fun runFlightTest() {
        val svc = PayPalAccessibilityService.instance
        if (svc == null) {
            Toast.makeText(this, "Accessibility service ON nahi hai", Toast.LENGTH_LONG).show()
            return
        }

        binding.tvTestResult.text = "Testing…"
        binding.btnTestFlight.isEnabled = false

        Thread {
            val ok = AirplaneModeController.toggleCycle(svc)
            runOnUiThread {
                binding.btnTestFlight.isEnabled = true
                binding.tvTestResult.text =
                    if (ok) "✔ Test passed — Flight toggled ON/OFF"
                    else "✘ Test fail — logs check karo"
                binding.tvTestResult.setTextColor(
                    if (ok) getColor(R.color.success_green) else getColor(R.color.error_red)
                )
            }
        }.start()
    }
}
