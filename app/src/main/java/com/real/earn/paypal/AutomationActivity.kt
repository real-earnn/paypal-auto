package com.real.earn.paypal

import android.os.Bundle
import android.view.View
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

        val service = intent.getStringExtra("service") ?: ""
        val country = intent.getStringExtra("country") ?: ""
        binding.tvSubHeader.text = "$service · $country"

        binding.btnBack.setOnClickListener {
            // Do NOT stop — just hide activity, service keeps running
            finish()
        }

        binding.btnPause.setOnClickListener {
            when (AutomationState.getStatus()) {
                AutomationState.Status.PAUSED -> AutomationService.resume(this)
                AutomationState.Status.RUNNING -> AutomationService.pause(this)
                else -> {}
            }
        }

        binding.btnStop.setOnClickListener {
            // "Stop" → stop + close both
            AutomationService.stopAndClose(this)
        }

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
}
