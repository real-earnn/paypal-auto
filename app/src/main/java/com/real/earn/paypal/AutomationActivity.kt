package com.real.earn.paypal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.real.earn.paypal.databinding.ActivityAutomationBinding
import com.real.earn.paypal.service.AutomationService
import com.real.earn.paypal.service.AutomationState
import kotlinx.coroutines.launch

class AutomationActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAutomationBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAutomationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mode = intent.getStringExtra("mode") ?: "service"
        val service = intent.getStringExtra("service") ?: "PayPal"
        val country = intent.getStringExtra("country") ?: ""
        val prefix = intent.getStringExtra("prefix") ?: ""
        val file = intent.getStringExtra("file") ?: ""

        binding.tvSubHeader.text = when (mode) {
            "auto" -> "$service · Auto Prefix"
            "prefix" -> {
                val p = if (prefix.isNotEmpty()) prefix else "?"
                val f = if (file.isNotEmpty()) file else "?"
                "$service · $p / $f"
            }
            else -> "$service · $country"
        }

        binding.btnBack.setOnClickListener { finish() }

        binding.btnPause.setOnClickListener {
            when (AutomationState.getStatus()) {
                AutomationState.Status.PAUSED -> AutomationService.resume(this)
                AutomationState.Status.RUNNING -> AutomationService.pause(this)
                else -> {}
            }
        }

        binding.btnStop.setOnClickListener {
            AutomationService.stopAndClose(this)
        }

        // 🟢 Copy log button (safe lookup)
        setupCopyLogButton()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    AutomationState.logs.collect { list ->
                        binding.tvLog.text = list.joinToString("\n")
                        binding.scrollLog.post {
                            binding.scrollLog.fullScroll(View.FOCUS_DOWN)
                        }
                    }
                }

                launch {
                    AutomationState.stats.collect { s ->
                        binding.tvAttempts.text = s.totalAttempts.toString()
                        binding.tvSuccess.text = s.successCount.toString()
                        binding.tvFailed.text = s.failCount.toString()
                        binding.tvCurrentNumber.text = "Current: ${s.currentNumber.ifEmpty { "—" }}"
                    }
                }

                launch {
                    AutomationState.status.collect { st ->
                        binding.tvHeader.text = when (st) {
                            AutomationState.Status.RUNNING -> "Automation Running"
                            AutomationState.Status.PAUSED -> "Automation Paused"
                            AutomationState.Status.STOPPING -> "Stopping…"
                            AutomationState.Status.STOPPED -> "Stopped"
                            AutomationState.Status.IDLE -> "Idle"
                        }
                        binding.btnPause.text = if (st == AutomationState.Status.PAUSED) "▶ Resume" else "⏸ Pause"
                        binding.btnPause.isEnabled = st == AutomationState.Status.RUNNING || st == AutomationState.Status.PAUSED
                        binding.btnStop.isEnabled = st == AutomationState.Status.RUNNING || st == AutomationState.Status.PAUSED
                    }
                }

                launch {
                    AutomationState.closeRequested.collect { close ->
                        if (close) {
                            AutomationState.resetClose()
                            finish()
                        }
                    }
                }
            }
        }
    }

    private fun setupCopyLogButton() {
        val copyId = resources.getIdentifier("btnCopyLog", "id", packageName)
        if (copyId == 0) return
        findViewById<View>(copyId)?.setOnClickListener {
            val logText = binding.tvLog.text.toString()
            if (logText.isEmpty()) {
                Toast.makeText(this, "No logs yet", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("logs", logText))
            Toast.makeText(this, "Logs copied ✓", Toast.LENGTH_SHORT).show()
        }
    }
}
