package com.example.saqr.model

/**
 * Hardware Performance Tiers determining UI complexity, coroutine concurrency,
 * and AI/AST workload dispatching.
 */
enum class PerformanceTier(
    val displayName: String,
    val maxConcurrentWorkers: Int,
    val blurRadiusDp: Float,
    val enableAmbientParticles: Boolean,
    val refreshRateTargetFps: Int
) {
    ULTRA("Ultra Compute", maxConcurrentWorkers = 8, blurRadiusDp = 20f, enableAmbientParticles = true, refreshRateTargetFps = 120),
    HIGH("High Performance", maxConcurrentWorkers = 4, blurRadiusDp = 14f, enableAmbientParticles = true, refreshRateTargetFps = 60),
    MEDIUM("Balanced", maxConcurrentWorkers = 2, blurRadiusDp = 8f, enableAmbientParticles = false, refreshRateTargetFps = 60),
    LOW("Eco / Throttled", maxConcurrentWorkers = 1, blurRadiusDp = 0f, enableAmbientParticles = false, refreshRateTargetFps = 30)
}

enum class ThermalState(val label: String, val isThrottling: Boolean) {
    NORMAL("Normal Thermal", false),
    LIGHT("Light Warming", false),
    MODERATE("Moderate Heat", true),
    SEVERE("Severe Throttle", true),
    CRITICAL("Critical Thermal", true)
}

data class CpuTopology(
    val coreCount: Int,
    val activeCores: Int,
    val architecture: String,
    val estimatedLoadPercent: Int,
    val maxFrequencyMhz: Long
)

data class MemoryMetrics(
    val totalRamMb: Long,
    val availableRamMb: Long,
    val usedRamMb: Long,
    val ramUsagePercent: Int,
    val isLowMemory: Boolean,
    val thresholdMb: Long
)

data class BatteryMetrics(
    val levelPercent: Int,
    val isCharging: Boolean,
    val temperatureCelsius: Float
)

data class HardwareProfile(
    val tier: PerformanceTier,
    val cpu: CpuTopology,
    val memory: MemoryMetrics,
    val thermal: ThermalState,
    val battery: BatteryMetrics,
    val timestamp: Long = System.currentTimeMillis()
)
