package com.example.saqr.engine

import android.content.Context
import com.example.saqr.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class SaqrSystemState(
    val hardwareProfile: HardwareProfile,
    val currentPlan: DAGPlan?,
    val systemHealth: SystemHealth,
    val telemetryLogs: List<TelemetryLog>,
    val plugins: List<PluginManifest>,
    val memoryNodes: List<MemoryNode>,
    val astPatches: List<AstPatch>,
    val checkpoints: List<StateCheckpoint>,
    val isInitialized: Boolean = true
)

class SaqrMaestroKernel(context: Context) {
    // Structured coroutine scope with SupervisorJob to prevent child failures from crashing the kernel
    private val kernelScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val telemetry = TelemetryEngine()
    val hardwareProfiler = HardwareProfilerEngine(context, kernelScope)
    val memoryEngine = VectorMemoryEngine(telemetry)
    val autonomousExtension = AutonomousExtensionEngine(telemetry)
    val pluginManager = PluginManagerEngine(telemetry, memoryEngine, autonomousExtension)
    val selfHealing = SelfHealingEngine(telemetry, autonomousExtension)
    val dagPlanner = DAGPlannerEngine(
        telemetry = telemetry,
        selfHealingEngine = selfHealing,
        pluginManager = pluginManager,
        hardwareProfiler = hardwareProfiler,
        autonomousExtension = autonomousExtension
    )

    // Unified reactive StateFlow for the entire UI
    val systemState: StateFlow<SaqrSystemState> = combine(
        hardwareProfiler.hardwareProfile,
        dagPlanner.currentPlan,
        telemetry.healthState,
        telemetry.logsState
    ) { profile, plan, health, logs ->
        SaqrSystemState(
            hardwareProfile = profile,
            currentPlan = plan,
            systemHealth = health,
            telemetryLogs = logs,
            plugins = pluginManager.pluginsState.value,
            memoryNodes = memoryEngine.nodesState.value,
            astPatches = autonomousExtension.patchesState.value,
            checkpoints = autonomousExtension.checkpointsState.value
        )
    }.stateIn(
        scope = kernelScope,
        started = SharingStarted.Eagerly,
        initialValue = SaqrSystemState(
            hardwareProfile = hardwareProfiler.hardwareProfile.value,
            currentPlan = null,
            systemHealth = telemetry.healthState.value,
            telemetryLogs = emptyList(),
            plugins = emptyList(),
            memoryNodes = emptyList(),
            astPatches = emptyList(),
            checkpoints = emptyList(),
            isInitialized = true
        )
    )

    init {
        telemetry.log(LogLevel.INFO, "SaqrMaestroKernel", "SAQR OS Autonomous Agent Kernel initialized. Dynamic scheduler online.")
        // Preload default system audit plan
        dagPlanner.generatePlan("Perform Real-Time System Integrity & Hardware Audit")
    }

    fun executeGoal(prompt: String, simulateFailureOnStepId: String? = null) {
        dagPlanner.generatePlan(prompt)
        dagPlanner.executePlan(kernelScope, simulateFailureOnStepId)
    }

    fun runCurrentPlan(simulateFailureOnStepId: String? = null) {
        dagPlanner.executePlan(kernelScope, simulateFailureOnStepId)
    }

    fun cancelExecution() {
        dagPlanner.cancelExecution()
    }

    fun triggerThermalSpikeSimulation(forceThrottle: Boolean) {
        if (forceThrottle) {
            hardwareProfiler.simulateThermalState(ThermalState.SEVERE)
            telemetry.log(LogLevel.WARN, "SaqrMaestroKernel", "SIMULATION: Hardware thermal spike to 52°C injected. Profiler adapting UI & concurrency.")
        } else {
            hardwareProfiler.simulateThermalState(null)
            telemetry.log(LogLevel.INFO, "SaqrMaestroKernel", "SIMULATION: Thermal spike cleared. Resumed full compute profile.")
        }
    }

    fun shutdown() {
        telemetry.log(LogLevel.WARN, "SaqrMaestroKernel", "Kernel shutdown initiated.")
        hardwareProfiler.stopProfiling()
        kernelScope.cancel()
    }
}
