package com.example.saqr.engine

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.example.saqr.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.roundToInt

class HardwareProfilerEngine(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private var forcedThermalState: ThermalState? = null

    private val _hardwareProfile = MutableStateFlow(computeInitialProfile())
    val hardwareProfile: StateFlow<HardwareProfile> = _hardwareProfile.asStateFlow()

    private var monitorJob: Job? = null

    init {
        startProfiling()
    }

    fun startProfiling() {
        if (monitorJob?.isActive == true) return
        monitorJob = coroutineScope.launch(Dispatchers.Default) {
            while (isActive) {
                val updatedProfile = sampleHardware()
                _hardwareProfile.value = updatedProfile
                delay(1500)
            }
        }
    }

    fun stopProfiling() {
        monitorJob?.cancel()
        monitorJob = null
    }

    fun simulateThermalState(state: ThermalState?) {
        forcedThermalState = state
        _hardwareProfile.value = sampleHardware()
    }

    private fun computeInitialProfile(): HardwareProfile {
        return sampleHardware()
    }

    private fun sampleHardware(): HardwareProfile {
        val cpu = probeCpuTopology()
        val memory = probeMemory()
        val thermal = forcedThermalState ?: probeThermalState()
        val battery = probeBattery()

        val tier = determineTier(cpu, memory, thermal)

        return HardwareProfile(
            tier = tier,
            cpu = cpu,
            memory = memory,
            thermal = thermal,
            battery = battery
        )
    }

    private fun probeCpuTopology(): CpuTopology {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val arch = Build.SUPPORTED_ABIS.firstOrNull() ?: System.getProperty("os.arch") ?: "arm64-v8a"
        
        // Estimate load based on active threads & available processors
        val activeThreads = Thread.activeCount()
        val estimatedLoad = ((activeThreads * 3) + (cores * 4)).coerceIn(12, 94)
        
        // Approx max frequency
        val freqMhz = try {
            val maxFreqFile = File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
            if (maxFreqFile.exists()) {
                maxFreqFile.readText().trim().toLongOrNull()?.div(1000) ?: 2400L
            } else 2400L
        } catch (_: Exception) {
            2400L
        }

        return CpuTopology(
            coreCount = cores,
            activeCores = cores,
            architecture = arch,
            estimatedLoadPercent = estimatedLoad,
            maxFrequencyMhz = freqMhz
        )
    }

    private fun probeMemory(): MemoryMetrics {
        val memoryInfo = ActivityManager.MemoryInfo()
        if (activityManager != null) {
            activityManager.getMemoryInfo(memoryInfo)
            val totalMb = memoryInfo.totalMem / (1024 * 1024)
            val availMb = memoryInfo.availMem / (1024 * 1024)
            val usedMb = (totalMb - availMb).coerceAtLeast(0)
            val pct = if (totalMb > 0) ((usedMb.toFloat() / totalMb) * 100).roundToInt() else 45

            return MemoryMetrics(
                totalRamMb = totalMb,
                availableRamMb = availMb,
                usedRamMb = usedMb,
                ramUsagePercent = pct,
                isLowMemory = memoryInfo.lowMemory,
                thresholdMb = memoryInfo.threshold / (1024 * 1024)
            )
        } else {
            val maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024)
            val totalMem = Runtime.getRuntime().totalMemory() / (1024 * 1024)
            val freeMem = Runtime.getRuntime().freeMemory() / (1024 * 1024)
            val usedMb = totalMem - freeMem
            return MemoryMetrics(
                totalRamMb = maxMem,
                availableRamMb = maxMem - usedMb,
                usedRamMb = usedMb,
                ramUsagePercent = 50,
                isLowMemory = false,
                thresholdMb = 256
            )
        }
    }

    private fun probeThermalState(): ThermalState {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            return when (powerManager.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalState.NORMAL
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalState.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalState.CRITICAL
                else -> ThermalState.NORMAL
            }
        }
        return ThermalState.NORMAL
    }

    private fun probeBattery(): BatteryMetrics {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus: Intent? = context.registerReceiver(null, intentFilter)

        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct = if (level != -1 && scale != -1) ((level.toFloat() / scale) * 100).roundToInt() else 85

        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 320) ?: 320
        val tempCelsius = tempTenths / 10f

        return BatteryMetrics(
            levelPercent = pct.coerceIn(0, 100),
            isCharging = isCharging,
            temperatureCelsius = tempCelsius
        )
    }

    private fun determineTier(
        cpu: CpuTopology,
        memory: MemoryMetrics,
        thermal: ThermalState
    ): PerformanceTier {
        // Severe thermal throttle forces Low / Eco tier immediately to prevent overheating
        if (thermal == ThermalState.SEVERE || thermal == ThermalState.CRITICAL || memory.isLowMemory) {
            return PerformanceTier.LOW
        }

        if (thermal == ThermalState.MODERATE) {
            return PerformanceTier.MEDIUM
        }

        val ramGb = memory.totalRamMb / 1024.0
        val cores = cpu.coreCount

        return when {
            cores >= 8 && ramGb >= 5.5 -> PerformanceTier.ULTRA
            cores >= 6 && ramGb >= 3.5 -> PerformanceTier.HIGH
            cores >= 4 && ramGb >= 2.0 -> PerformanceTier.MEDIUM
            else -> PerformanceTier.LOW
        }
    }
}
