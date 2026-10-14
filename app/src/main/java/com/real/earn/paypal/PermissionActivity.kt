package com.real.earn.paypal

import android.content.Intent
import android.graphics.Color
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

    private val posChips by lazy {
        listOf(
            binding.pos1, binding.pos2, binding.pos3,
            binding.pos4, binding.pos5, binding.pos6
        )
    }

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

        // ── Auto Flight Mode toggle ──────────────────────────
        binding.switchAutoFlight.isChecked = prefs.autoFlightMode
        binding.layoutPosition.visibility =
            if (prefs.autoFlightMode) android.view.View.VISIBLE else android.view.View.GONE

        binding.switchAutoFlight.setOnCheckedChangeListener { _, checked ->
            prefs.autoFlightMode = checked
            binding.layoutPosition.visibility =
                if (checked) android.view.View.VISIBLE else android.view.View.GONE
            refreshStartButton()
        }

        // ── Position chips ──────────────────────────────────
        posChips.forEachIndexed { index, chip ->
            chip.setOnClickListener {
                selectPosition(index + 1)
            }
        }
        // Restore previous selection
        if (prefs.flightPosition in 1..6) {
            selectPosition(prefs.flightPosition)
        }

        // ── Test button ─────────────────────────────────────
        binding.btnTestFlight.setOnClickListener {
            runFlightTest()
        }

        binding.btnStart.setOnClickListener {
            if (!allGranted()) {
                Toast.makeText(this, "Pehle saari permissions enable karo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (prefs.autoFlightMode && prefs.flightPosition !in 1..6) {
                Toast.makeText(this, "Flight position select karo (1-6)", Toast.LENGTH_SHORT).show()
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

            Toast.makeText(this, "Automation start ho rahi hai…", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    // ── UI refreshers ─────────────────────────────────────

    private fun selectPosition(pos: Int) {
        prefs.flightPosition = pos
        posChips.forEachIndexed { idx, chip ->
            val selected = (idx + 1) == pos
            chip.setBackgroundResource(
                if (selected) R.drawable.bg_input else R.drawable.bg_input
            )
            chip.setTextColor(
                if (selected) Color.parseColor("#0070BA") else Color.parseColor("#E6EDF3")
            )
            chip.alpha = if (selected) 1f else 0.6f
        }
        binding.tvSelectedPos.text = "Selected: Position $pos"
        binding.tvTestResult.text = ""
        refreshStartButton()
    }

    private fun refreshUi() {
        val a11y = PermissionHelper.isAccessibilityEnabled(this)
        val overlay = PermissionHelper.isOverlayGranted(this)
        val battery = PermissionHelper.isBatteryOptimizationIgnored(this)

        setRowState(binding.tvAccessibilityState, a11y)
        setRowState(binding.tvOverlayState, overlay)
        setRowState(binding.tvBatteryState, battery)

        refreshStartButton()
    }

    private fun refreshStartButton() {
        val ok = allGranted() &&
                (!prefs.autoFlightMode || prefs.flightPosition in 1..6)
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
        val pos = prefs.flightPosition
        if (pos !in 1..6) {
            Toast.makeText(this, "Pehle position select karo", Toast.LENGTH_SHORT).show()
            return
        }

        val svc = PayPalAccessibilityService.instance
        if (svc == null) {
            Toast.makeText(this, "Accessibility service ON nahi hai", Toast.LENGTH_LONG).show()
            return
        }

        binding.tvTestResult.text = "Testing position $pos…"
        binding.btnTestFlight.isEnabled = false

        Thread {
            val ok = AirplaneModeController.testPosition(svc, pos)
            runOnUiThread {
                binding.btnTestFlight.isEnabled = true
                binding.tvTestResult.text =
                    if (ok) "✔ Test passed — Flight toggled ON/OFF"
                    else "✘ Test fail — position check karo"
                binding.tvTestResult.setTextColor(
                    if (ok) getColor(R.color.success_green) else getColor(R.color.error_red)
                )
            }
        }.start()
    }
}
