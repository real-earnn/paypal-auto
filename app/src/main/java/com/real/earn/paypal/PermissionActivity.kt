package com.real.earn.paypal

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.real.earn.paypal.databinding.ActivityPermissionBinding
import com.real.earn.paypal.service.AutomationService
import com.real.earn.paypal.utils.AppPrefs
import com.real.earn.paypal.utils.PermissionHelper

class PermissionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPermissionBinding
    private lateinit var prefs: AppPrefs

    private var mode: String = "service"
    private var serviceName: String = "PayPal"
    private var countryName: String = ""
    private var countryCode: String = ""
    private var dialingCode: String = ""
    private var price: Double = 0.0
    private var prefix: String = ""
    private var file: String = ""
    private var engineType: String = "paypal"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPermissionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = AppPrefs(this)

        mode = intent.getStringExtra("mode") ?: "service"
        serviceName = intent.getStringExtra("service") ?: "PayPal"
        countryName = intent.getStringExtra("country") ?: ""
        countryCode = intent.getStringExtra("country_code") ?: ""
        dialingCode = intent.getStringExtra("dialing_code") ?: ""
        price = intent.getDoubleExtra("price", 0.0)
        prefix = intent.getStringExtra("prefix") ?: ""
        file = intent.getStringExtra("file") ?: ""
        engineType = intent.getStringExtra("engine_type") ?: "paypal"

        binding.tvTitle.text = when (mode) {
            "auto" -> "$serviceName — Auto Prefix"
            "prefix" -> "$serviceName — $prefix ($file)"
            else -> "$serviceName — $countryName"
        }

        // 🟢 Agar TikTok hai to Old Account Method ka option dikhayein
        if (engineType == "tiktok") {
            binding.rowOldAccountMethod.visibility = android.view.View.VISIBLE
        } else {
            binding.rowOldAccountMethod.visibility = android.view.View.GONE
        }

        binding.btnBack.setOnClickListener { finish() }
        binding.rowAccessibility.setOnClickListener { PermissionHelper.openAccessibilitySettings(this) }
        binding.rowOverlay.setOnClickListener { PermissionHelper.openOverlaySettings(this) }
        binding.rowBattery.setOnClickListener { PermissionHelper.openBatterySettings(this) }

        binding.switchAutoFlight.isChecked = prefs.autoFlightMode
        binding.switchAutoFlight.setOnCheckedChangeListener { _, checked -> prefs.autoFlightMode = checked }

        binding.switchCaptchaAlarm.isChecked = prefs.captchaAlarmEnabled
        binding.switchCaptchaAlarm.setOnCheckedChangeListener { _, checked ->
            prefs.captchaAlarmEnabled = checked
            updateSoundLabel()
        }

        binding.btnChooseSound.setOnClickListener { openRingtonePicker() }
        updateSoundLabel()

        binding.btnStart.setOnClickListener {
            if (!allGranted()) {
                Toast.makeText(this, "Pehle saari permissions enable karo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startAutomation()
        }
    }

    private fun startAutomation() {
        val actIntent = Intent(this, AutomationActivity::class.java).apply {
            putExtra("mode", mode)
            putExtra("service", serviceName)
            putExtra("country", countryName)
            putExtra("prefix", prefix)
            putExtra("file", file)
        }
        startActivity(actIntent)

        val isOldAccount = binding.switchOldAccountMethod.isChecked

        AutomationService.start(
            context = this,
            mode = mode,
            service = serviceName,
            country = countryName,
            countryCode = countryCode,
            dialingCode = dialingCode,
            prefix = prefix,
            file = file,
            engineType = engineType,
            oldAccountMethod = isOldAccount // 🟢 Pass the flag
        )
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

    private fun updateSoundLabel() {
        if (!prefs.captchaAlarmEnabled) {
            binding.tvSelectedSound.text = "Alarm disabled"
            binding.btnChooseSound.isEnabled = false
            binding.btnChooseSound.alpha = 0.5f
            return
        }
        binding.btnChooseSound.isEnabled = true
        binding.btnChooseSound.alpha = 1f

        val uri = prefs.captchaAlarmUri
        if (uri.isNullOrEmpty()) {
            binding.tvSelectedSound.text = "Default system alarm"
        } else {
            val name = try {
                val r = RingtoneManager.getRingtone(this, Uri.parse(uri))
                r?.getTitle(this) ?: "Custom sound"
            } catch (_: Exception) { "Custom sound" }
            binding.tvSelectedSound.text = "Selected: $name"
        }
    }

    private fun openRingtonePicker() {
        try {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_RINGTONE)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Captcha Alarm Sound")

                val existing = prefs.captchaAlarmUri
                if (!existing.isNullOrEmpty()) {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existing))
                } else {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                }
            }
            startActivityForResult(intent, 5001)
        } catch (e: Exception) {
            Toast.makeText(this, "Ringtone picker not available: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 5001 && resultCode == Activity.RESULT_OK) {
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri
            }

            if (uri != null) {
                prefs.captchaAlarmUri = uri.toString()
                Toast.makeText(this, "Alarm sound saved", Toast.LENGTH_SHORT).show()
            } else {
                prefs.captchaAlarmUri = null
                Toast.makeText(this, "Default alarm set", Toast.LENGTH_SHORT).show()
            }
            updateSoundLabel()
        }
    }
}
