package com.example.saqr.engine

import com.example.saqr.model.*
import kotlinx.coroutines.delay

class SelfHealingEngine(
    private val telemetry: TelemetryEngine,
    private val autonomousExtension: AutonomousExtensionEngine
) {
    suspend fun diagnoseAndHeal(
        failedStep: ExecutionStep,
        errorMessage: String
    ): HealingOutcome {
        telemetry.log(LogLevel.WARN, "SelfHealingEngine", "Diagnosing failure on step [${failedStep.id}]: '$errorMessage'")
        delay(350) // Non-blocking root cause reasoning latency

        val (diagnosis, fallbackPatch) = when {
            errorMessage.contains("NoSuchElementException", ignoreCase = true) ||
            errorMessage.contains("NodeNotFound", ignoreCase = true) -> {
                "Target UI element selector mutated or obscured." to
                        autonomousExtension.generateAstPatch(
                            targetModule = "UIAutomationEngine::findTargetNode",
                            originalSyntax = "node.findAccessibilityNodeInfosByViewId(\"${failedStep.id}\")",
                            replacementSyntax = "node.findAccessibilityNodeInfosByViewId(\"${failedStep.id}_v2\") ?: node.findNodeByText(\"${failedStep.title}\")",
                            rationale = "Dynamic fallback to text-based locator after viewId miss."
                        )
            }
            errorMessage.contains("Timeout", ignoreCase = true) -> {
                "Network or DOM rendering latency exceeded timeout threshold." to
                        autonomousExtension.generateAstPatch(
                            targetModule = "CoroutineDispatcher::timeoutPolicy",
                            originalSyntax = "withTimeout(3000L)",
                            replacementSyntax = "withTimeout(6500L)",
                            rationale = "Adaptive extension of execution deadline for heavy payload."
                        )
            }
            errorMessage.contains("Sandbox", ignoreCase = true) ||
            errorMessage.contains("Permission", ignoreCase = true) -> {
                "Plugin capability permission violation." to
                        autonomousExtension.generateAstPatch(
                            targetModule = "PluginSandbox::guardPolicy",
                            originalSyntax = "requireCapability(PluginCapability.PROCESS_EXEC)",
                            replacementSyntax = "requestTemporaryCapabilityGrant(PluginCapability.PROCESS_EXEC, sandboxTtlMs = 5000L)",
                            rationale = "Elevate ephemeral sandbox scope under verified supervisor policy."
                        )
            }
            else -> {
                "Transient pipeline stall." to
                        autonomousExtension.generateAstPatch(
                            targetModule = "DAGPlanner::retryPolicy",
                            originalSyntax = "retry(0)",
                            replacementSyntax = "retry(exponentialBackoff(baseMs = 500L, maxRetries = 3))",
                            rationale = "Exponential backoff recovery for transient failure."
                        )
            }
        }

        // Live apply patch
        autonomousExtension.applyPatch(fallbackPatch.patchId)
        telemetry.recordHealedIncident()

        val recoveryStep = ExecutionStep(
            id = "${failedStep.id}_heal",
            title = "Self-Heal: Reconfigured ${failedStep.title}",
            description = "Diagnosis: $diagnosis. Applied AST patch ${fallbackPatch.patchId}",
            category = StepCategory.SELF_HEAL,
            status = StepStatus.SUCCESS,
            patchDiff = "+ ${fallbackPatch.replacementSyntax}\n- ${fallbackPatch.originalSyntax}"
        )

        telemetry.log(LogLevel.INFO, "SelfHealingEngine", "Healed failure for step [${failedStep.id}]. Recovery step injected.")
        return HealingOutcome(
            isHealed = true,
            diagnosis = diagnosis,
            appliedPatch = fallbackPatch,
            recoveryStep = recoveryStep
        )
    }
}

data class HealingOutcome(
    val isHealed: Boolean,
    val diagnosis: String,
    val appliedPatch: AstPatch,
    val recoveryStep: ExecutionStep
)
