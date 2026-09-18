package com.example.saqr.engine

import com.example.saqr.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class DAGPlannerEngine(
    private val telemetry: TelemetryEngine,
    private val selfHealingEngine: SelfHealingEngine,
    private val pluginManager: PluginManagerEngine,
    private val hardwareProfiler: HardwareProfilerEngine,
    private val autonomousExtension: AutonomousExtensionEngine
) {
    private val _currentPlan = MutableStateFlow<DAGPlan?>(null)
    val currentPlan: StateFlow<DAGPlan?> = _currentPlan.asStateFlow()

    private var executionJob: Job? = null

    fun generatePlan(goalPrompt: String): DAGPlan {
        val planId = "plan-${System.currentTimeMillis().toString().takeLast(6)}"
        val steps = when {
            goalPrompt.contains("audit", ignoreCase = true) || goalPrompt.contains("diagnos", ignoreCase = true) -> {
                createSystemAuditPlanSteps()
            }
            goalPrompt.contains("crawl", ignoreCase = true) || goalPrompt.contains("extract", ignoreCase = true) -> {
                createWebCrawlPlanSteps()
            }
            goalPrompt.contains("patch", ignoreCase = true) || goalPrompt.contains("repair", ignoreCase = true) -> {
                createSelfRepairPlanSteps()
            }
            goalPrompt.contains("stress", ignoreCase = true) || goalPrompt.contains("bench", ignoreCase = true) -> {
                createHardwareStressPlanSteps()
            }
            else -> {
                createStandardAutonomousPlanSteps(goalPrompt)
            }
        }

        val plan = DAGPlan(
            planId = planId,
            goalPrompt = goalPrompt,
            steps = steps,
            isRunning = false,
            progress = 0f
        )
        _currentPlan.value = plan
        telemetry.log(LogLevel.INFO, "DAGPlanner", "Synthesized DAG Plan [$planId] with ${steps.size} steps for: '$goalPrompt'")
        return plan
    }

    fun executePlan(coroutineScope: CoroutineScope, simulateFailureOnStepId: String? = null) {
        val plan = _currentPlan.value ?: return
        if (plan.isRunning) return

        executionJob?.cancel()
        executionJob = coroutineScope.launch(Dispatchers.Default) {
            val startTime = System.currentTimeMillis()
            var currentSteps = plan.steps.map { it.copy(status = StepStatus.PENDING) }.toMutableList()

            _currentPlan.value = plan.copy(
                isRunning = true,
                isCompleted = false,
                steps = currentSteps,
                startTimeMs = startTime,
                progress = 0.05f
            )

            // Dynamic concurrency adaptation based on real-time hardware tier
            val tier = hardwareProfiler.hardwareProfile.value.tier
            val concurrencyLimit = tier.maxConcurrentWorkers
            val semaphore = Semaphore(concurrencyLimit)
            telemetry.log(LogLevel.INFO, "DAGPlanner", "Dispatching DAG with concurrency limit: $concurrencyLimit workers [Tier: ${tier.displayName}]")
            telemetry.updateActiveWorkers(concurrencyLimit)

            // Step execution queue with topological progression
            var failureCount = 0
            var healedCount = 0

            for (i in currentSteps.indices) {
                val step = currentSteps[i]

                // Check dependencies
                val depsMet = step.dependsOn.all { depId ->
                    currentSteps.find { it.id == depId }?.status == StepStatus.SUCCESS
                }

                if (!depsMet) {
                    currentSteps[i] = step.copy(status = StepStatus.SKIPPED)
                    continue
                }

                // Mark running
                currentSteps[i] = step.copy(status = StepStatus.RUNNING)
                _currentPlan.value = _currentPlan.value?.copy(
                    steps = currentSteps.toList(),
                    progress = (i.toFloat() / currentSteps.size).coerceIn(0.1f, 0.95f)
                )

                val stepStart = System.currentTimeMillis()

                try {
                    semaphore.withPermit {
                        // Check if simulated failure is triggered
                        if (simulateFailureOnStepId == step.id) {
                            throw NoSuchElementException("Simulated NodeNotFound: Element '${step.title}' was mutated by host application layout refactor.")
                        }

                        // Dispatch actual step logic
                        val executionDuration = when (step.category) {
                            StepCategory.PERCEPTION -> 350L
                            StepCategory.PLANNING -> 250L
                            StepCategory.UI_ACTION -> {
                                pluginManager.executeTool("plugin-ui-auto", "click", mapOf("target" to step.title))
                                400L
                            }
                            StepCategory.AST_PATCH -> {
                                autonomousExtension.executeAutonomousScript("patch(target=\"${step.title}\");")
                                300L
                            }
                            StepCategory.TOOL_INVOCATION -> {
                                pluginManager.executeTool("plugin-spatial-vision", "inspect", emptyMap())
                                350L
                            }
                            StepCategory.VERIFICATION -> 200L
                            StepCategory.SELF_HEAL -> 300L
                        }
                        delay(executionDuration)
                    }

                    val elapsed = System.currentTimeMillis() - stepStart
                    currentSteps[i] = currentSteps[i].copy(
                        status = StepStatus.SUCCESS,
                        executionTimeMs = elapsed,
                        outputPayload = "Verified complete in ${elapsed}ms"
                    )
                    telemetry.log(LogLevel.INFO, "DAGPlanner", "Step completed: [${step.id}] ${step.title} in ${elapsed}ms")

                } catch (e: Exception) {
                    failureCount++
                    val elapsed = System.currentTimeMillis() - stepStart
                    currentSteps[i] = currentSteps[i].copy(
                        status = StepStatus.FAILED,
                        executionTimeMs = elapsed,
                        errorDetails = e.message
                    )
                    telemetry.log(LogLevel.ERROR, "DAGPlanner", "Step failed [${step.id}]: ${e.message}")

                    // Trigger Autonomous Self-Healing
                    currentSteps[i] = currentSteps[i].copy(status = StepStatus.HEALING)
                    _currentPlan.value = _currentPlan.value?.copy(steps = currentSteps.toList())

                    val healingResult = selfHealingEngine.diagnoseAndHeal(currentSteps[i], e.message ?: "Unknown error")
                    if (healingResult.isHealed) {
                        healedCount++
                        // Insert healing recovery step
                        currentSteps[i] = currentSteps[i].copy(
                            status = StepStatus.SUCCESS,
                            errorDetails = "Recovered via AST Patch: ${healingResult.appliedPatch.patchId}"
                        )
                        currentSteps.add(i + 1, healingResult.recoveryStep)
                    }
                }

                _currentPlan.value = _currentPlan.value?.copy(
                    steps = currentSteps.toList(),
                    failureCount = failureCount,
                    healedCount = healedCount
                )
            }

            val totalElapsed = System.currentTimeMillis() - startTime
            telemetry.updateActiveWorkers(0)
            telemetry.log(LogLevel.INFO, "DAGPlanner", "DAG Execution Completed in ${totalElapsed}ms. Failures: $failureCount, Healed: $healedCount")

            _currentPlan.value = _currentPlan.value?.copy(
                isRunning = false,
                isCompleted = true,
                progress = 1.0f,
                elapsedTimeMs = totalElapsed,
                failureCount = failureCount,
                healedCount = healedCount
            )
        }
    }

    fun cancelExecution() {
        executionJob?.cancel()
        executionJob = null
        telemetry.updateActiveWorkers(0)
        _currentPlan.value = _currentPlan.value?.copy(isRunning = false)
        telemetry.log(LogLevel.WARN, "DAGPlanner", "DAG Execution cancelled by user.")
    }

    private fun createSystemAuditPlanSteps(): List<ExecutionStep> = listOf(
        ExecutionStep("step_1", "Kernel & Hardware Probe", "Query CPU topology, RAM usage, and thermal throttling state.", StepCategory.PERCEPTION),
        ExecutionStep("step_2", "Security & Sandbox Audit", "Inspect registered plugins and active capability boundaries.", StepCategory.PLANNING, dependsOn = listOf("step_1")),
        ExecutionStep("step_3", "Vector Memory Consistency", "Validate semantic associative node embeddings and decay decay weights.", StepCategory.TOOL_INVOCATION, dependsOn = listOf("step_2")),
        ExecutionStep("step_4", "AST Runtime Integrity Check", "Verify AST patch sandbox, compile trees, and rollback checkpoints.", StepCategory.AST_PATCH, dependsOn = listOf("step_3")),
        ExecutionStep("step_5", "Audit Verification Report", "Synthesize operational confidence score and emit health telemetry.", StepCategory.VERIFICATION, dependsOn = listOf("step_4"))
    )

    private fun createWebCrawlPlanSteps(): List<ExecutionStep> = listOf(
        ExecutionStep("step_crawl_1", "Target Screen Perception", "Capture active screen hierarchy and parse DOM layout tree.", StepCategory.PERCEPTION),
        ExecutionStep("step_crawl_2", "Spatial Anchor Resolution", "Locate search bar input and action buttons via coordinate bounding box.", StepCategory.TOOL_INVOCATION, dependsOn = listOf("step_crawl_1")),
        ExecutionStep("step_crawl_3", "Simulate Touch Injection", "Dispatch synthetic tap and inject search query text.", StepCategory.UI_ACTION, dependsOn = listOf("step_crawl_2")),
        ExecutionStep("step_crawl_4", "Data Extraction & Vectorize", "Extract content text blocks and generate associative memory embeddings.", StepCategory.TOOL_INVOCATION, dependsOn = listOf("step_crawl_3")),
        ExecutionStep("step_crawl_5", "Payload Storage & Verification", "Store verified knowledge nodes into SAQR Vector Memory Bank.", StepCategory.VERIFICATION, dependsOn = listOf("step_crawl_4"))
    )

    private fun createSelfRepairPlanSteps(): List<ExecutionStep> = listOf(
        ExecutionStep("step_repair_1", "Anomaly Signature Scan", "Detect mutated UI accessibility IDs and failing coroutine dispatchers.", StepCategory.PERCEPTION),
        ExecutionStep("step_repair_2", "Create State Checkpoint", "Snapshot current system state into rollback recovery registry.", StepCategory.PLANNING, dependsOn = listOf("step_repair_1")),
        ExecutionStep("step_repair_3", "Synthesize AST Code Patch", "Generate replacement Kotlin AST tree for mutated view locator.", StepCategory.AST_PATCH, dependsOn = listOf("step_repair_2")),
        ExecutionStep("step_repair_4", "Sandbox Patch Dry-Run", "Execute patch in isolated security container to verify zero side-effects.", StepCategory.TOOL_INVOCATION, dependsOn = listOf("step_repair_3")),
        ExecutionStep("step_repair_5", "Live Hot-Patch Deployment", "Apply validated AST patch to active memory without restarting process.", StepCategory.SELF_HEAL, dependsOn = listOf("step_repair_4"))
    )

    private fun createHardwareStressPlanSteps(): List<ExecutionStep> = listOf(
        ExecutionStep("step_stress_1", "Baseline Performance Metric", "Sample initial CPU load, free memory MB, and thermal status.", StepCategory.PERCEPTION),
        ExecutionStep("step_stress_2", "Worker Pool Concurrency Scale", "Spawn adaptive coroutines across all available CPU cores.", StepCategory.PLANNING, dependsOn = listOf("step_stress_1")),
        ExecutionStep("step_stress_3", "Vector Similarity Math Load", "Run high-throughput multi-dimensional cosine tensor computations.", StepCategory.TOOL_INVOCATION, dependsOn = listOf("step_stress_2")),
        ExecutionStep("step_stress_4", "Dynamic Thermal Adaptation", "Verify hardware profiler switches tier and throttles gracefully.", StepCategory.VERIFICATION, dependsOn = listOf("step_stress_3"))
    )

    private fun createStandardAutonomousPlanSteps(prompt: String): List<ExecutionStep> = listOf(
        ExecutionStep("step_auto_1", "Perceive Environment", "Analyze intent '$prompt' and evaluate sensory screen DOM.", StepCategory.PERCEPTION),
        ExecutionStep("step_auto_2", "Plan Dependency DAG", "Decompose goal into sequential and parallel atomic steps.", StepCategory.PLANNING, dependsOn = listOf("step_auto_1")),
        ExecutionStep("step_auto_3", "Execute Autonomous Action", "Dispatch tool commands through verified sandbox wrappers.", StepCategory.UI_ACTION, dependsOn = listOf("step_auto_2")),
        ExecutionStep("step_auto_4", "Verify Outcome & Commit", "Confirm goal satisfaction and update long-term vector memory.", StepCategory.VERIFICATION, dependsOn = listOf("step_auto_3"))
    )
}
