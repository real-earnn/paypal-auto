package com.real.earn.paypal.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AutomationState {

    enum class Status { IDLE, RUNNING, PAUSED, STOPPING, STOPPED }

    data class Stats(
        val totalAttempts: Int = 0,
        val successCount: Int = 0,
        val failCount: Int = 0,
        val currentService: String = "",
        val currentCountry: String = "",
        val currentNumber: String = ""
    )

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats.asStateFlow()

    private val _closeRequested = MutableStateFlow(false)
    val closeRequested: StateFlow<Boolean> = _closeRequested.asStateFlow()

    fun setStatus(s: Status) { _status.value = s }
    fun getStatus(): Status = _status.value

    fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val line = "[$time] $msg"
        val cur = _logs.value
        val next = if (cur.size >= 300) cur.drop(50) + line else cur + line
        _logs.value = next
    }

    fun clearLogs() { _logs.value = emptyList() }

    fun setStats(s: Stats) { _stats.value = s }
    fun updateStats(block: (Stats) -> Stats) { _stats.value = block(_stats.value) }

    fun requestClose() { _closeRequested.value = true }
    fun resetClose() { _closeRequested.value = false }

    fun reset() {
        _logs.value = emptyList()
        _stats.value = Stats()
        _status.value = Status.IDLE
        _closeRequested.value = false
    }
}
