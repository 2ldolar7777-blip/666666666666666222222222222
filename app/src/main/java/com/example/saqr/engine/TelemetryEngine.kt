package com.example.saqr.engine

import com.example.saqr.model.LogLevel
import com.example.saqr.model.SystemHealth
import com.example.saqr.model.TelemetryLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentLinkedDeque

class TelemetryEngine {
    private val maxLogCapacity = 150
    private val logQueue = ConcurrentLinkedDeque<TelemetryLog>()

    private val _logsState = MutableStateFlow<List<TelemetryLog>>(emptyList())
    val logsState: StateFlow<List<TelemetryLog>> = _logsState.asStateFlow()

    private val _healthState = MutableStateFlow(SystemHealth())
    val healthState: StateFlow<SystemHealth> = _healthState.asStateFlow()

    private val startTimeMs = System.currentTimeMillis()

    init {
        log(LogLevel.INFO, "TelemetryEngine", "SAQR OS Telemetry Subsystem online. Zero-lock lockless buffer allocated.")
    }

    fun log(level: LogLevel, tag: String, message: String, latencyMs: Long? = null) {
        val entry = TelemetryLog(
            level = level,
            tag = tag,
            message = message,
            latencyMs = latencyMs
        )
        logQueue.addFirst(entry)
        while (logQueue.size > maxLogCapacity) {
            logQueue.pollLast()
        }
        _logsState.value = logQueue.toList()
        updateHealthMetrics()
    }

    fun recordHealedIncident() {
        _healthState.value = _healthState.value.copy(
            healedIncidentsCount = _healthState.value.healedIncidentsCount + 1,
            overallHealthScore = (_healthState.value.overallHealthScore + 1).coerceAtMost(100)
        )
    }

    fun recordAstPatchApplied() {
        _healthState.value = _healthState.value.copy(
            totalAstPatchesApplied = _healthState.value.totalAstPatchesApplied + 1
        )
    }

    fun updateActiveWorkers(count: Int) {
        _healthState.value = _healthState.value.copy(activeWorkers = count)
    }

    fun clearLogs() {
        logQueue.clear()
        _logsState.value = emptyList()
        log(LogLevel.INFO, "TelemetryEngine", "Telemetry circular buffer reset.")
    }

    private fun updateHealthMetrics() {
        val uptime = (System.currentTimeMillis() - startTimeMs) / 1000
        val errorCount = logQueue.count { it.level == LogLevel.ERROR || it.level == LogLevel.CRITICAL }
        val calculatedScore = (100 - (errorCount * 3)).coerceIn(60, 100)

        _healthState.value = _healthState.value.copy(
            overallHealthScore = calculatedScore,
            eventQueueDepth = logQueue.size,
            uptimeSeconds = uptime
        )
    }
}
