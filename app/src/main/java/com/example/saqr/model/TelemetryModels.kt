package com.example.saqr.model

enum class LogLevel {
    INFO,
    DEBUG,
    WARN,
    ERROR,
    CRITICAL
}

data class TelemetryLog(
    val id: String = java.util.UUID.randomUUID().toString().take(8),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val latencyMs: Long? = null
)

data class SystemHealth(
    val overallHealthScore: Int = 98,
    val activeWorkers: Int = 0,
    val eventQueueDepth: Int = 0,
    val healedIncidentsCount: Int = 0,
    val totalAstPatchesApplied: Int = 0,
    val uptimeSeconds: Long = 0L
)
