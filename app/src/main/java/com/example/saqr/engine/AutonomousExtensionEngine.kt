package com.example.saqr.engine

import com.example.saqr.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class AutonomousExtensionEngine(
    private val telemetry: TelemetryEngine
) {
    private val activePatches = ConcurrentHashMap<String, AstPatch>()
    private val checkpoints = ConcurrentHashMap<String, StateCheckpoint>()

    private val _patchesState = MutableStateFlow<List<AstPatch>>(emptyList())
    val patchesState: StateFlow<List<AstPatch>> = _patchesState.asStateFlow()

    private val _checkpointsState = MutableStateFlow<List<StateCheckpoint>>(emptyList())
    val checkpointsState: StateFlow<List<StateCheckpoint>> = _checkpointsState.asStateFlow()

    init {
        seedInitialPatch()
    }

    private fun seedInitialPatch() {
        val initialPatch = AstPatch(
            patchId = "patch-sys-101",
            targetModule = "UIAutomationEngine::findTargetNode",
            originalSyntax = "node.findAccessibilityNodeInfosByViewId(\"btn_submit\")",
            replacementSyntax = "node.findAccessibilityNodeInfosByViewId(\"btn_submit_v2\") ?: node.findNodeByText(\"Confirm\")",
            rationale = "Element ID mutation detected in target host app; adapted selector with text fallback.",
            confidenceScore = 0.96f,
            status = PatchStatus.APPLIED
        )
        activePatches[initialPatch.patchId] = initialPatch
        _patchesState.value = activePatches.values.toList()

        createCheckpoint("Initial Stable System State", mapOf("active_patch_count" to "1", "runtime_mode" to "STABLE"))
    }

    fun createCheckpoint(description: String, snapshot: Map<String, String>): StateCheckpoint {
        val id = "ckpt-${System.currentTimeMillis().toString().takeLast(6)}"
        val checkpoint = StateCheckpoint(
            checkpointId = id,
            description = description,
            snapshotData = snapshot
        )
        checkpoints[id] = checkpoint
        _checkpointsState.value = checkpoints.values.sortedByDescending { it.timestamp }
        telemetry.log(LogLevel.INFO, "AutonomousExtensionEngine", "Created state rollback checkpoint: $id ($description)")
        return checkpoint
    }

    fun rollbackToCheckpoint(checkpointId: String): Boolean {
        val checkpoint = checkpoints[checkpointId] ?: return false
        telemetry.log(LogLevel.WARN, "AutonomousExtensionEngine", "Rolling back to checkpoint: ${checkpoint.checkpointId} - ${checkpoint.description}")
        
        // Revert patches generated after checkpoint timestamp
        activePatches.values.forEach { patch ->
            if (patch.timestamp > checkpoint.timestamp) {
                activePatches[patch.patchId] = patch.copy(status = PatchStatus.ROLLED_BACK)
            }
        }
        _patchesState.value = activePatches.values.toList()
        return true
    }

    fun generateAstPatch(
        targetModule: String,
        originalSyntax: String,
        replacementSyntax: String,
        rationale: String
    ): AstPatch {
        val patchId = "patch-${System.currentTimeMillis().toString().takeLast(6)}"
        // Calculate simulated AST validation & confidence
        val hasSyntaxParenBalance = replacementSyntax.count { it == '(' } == replacementSyntax.count { it == ')' }
        val confidence = if (hasSyntaxParenBalance) 0.94f else 0.45f
        val initialStatus = if (confidence >= 0.8f) PatchStatus.VALIDATED else PatchStatus.REJECTED

        val patch = AstPatch(
            patchId = patchId,
            targetModule = targetModule,
            originalSyntax = originalSyntax,
            replacementSyntax = replacementSyntax,
            rationale = rationale,
            confidenceScore = confidence,
            status = initialStatus
        )

        activePatches[patchId] = patch
        _patchesState.value = activePatches.values.toList()
        telemetry.log(LogLevel.INFO, "AutonomousExtensionEngine", "AST patch synthesized [$patchId] for $targetModule (Confidence: ${(confidence * 100).toInt()}%)")
        return patch
    }

    fun applyPatch(patchId: String): Boolean {
        val patch = activePatches[patchId] ?: return false
        if (patch.status == PatchStatus.REJECTED) {
            telemetry.log(LogLevel.ERROR, "AutonomousExtensionEngine", "Cannot apply rejected patch [$patchId]")
            return false
        }
        val updated = patch.copy(status = PatchStatus.APPLIED)
        activePatches[patchId] = updated
        _patchesState.value = activePatches.values.toList()
        telemetry.recordAstPatchApplied()
        telemetry.log(LogLevel.INFO, "AutonomousExtensionEngine", "Hot-patched module runtime: ${patch.targetModule}")
        return true
    }

    fun executeAutonomousScript(scriptCode: String): ScriptExecutionResult {
        val startTime = System.currentTimeMillis()
        val violations = mutableListOf<String>()

        // Safety sandbox check
        if (scriptCode.contains("System.exit", ignoreCase = true)) {
            violations.add("Forbidden operation: System.exit blocked by security guard")
        }
        if (scriptCode.contains("Runtime.getRuntime().exec", ignoreCase = true) && !scriptCode.contains("safe_sandboxed")) {
            violations.add("Direct raw shell access without sandbox wrapper rejected")
        }

        if (violations.isNotEmpty()) {
            val duration = System.currentTimeMillis() - startTime
            telemetry.log(LogLevel.ERROR, "AutonomousExtensionEngine", "Script sandbox blocked violations: ${violations.joinToString(", ")}")
            return ScriptExecutionResult(
                isSuccess = false,
                output = "Execution halted by Sandbox Guard.",
                executionDurationMs = duration,
                sandboxViolations = violations
            )
        }

        // Evaluate script AST logic
        val lines = scriptCode.lines().filter { it.isNotBlank() }
        val outputBuffer = StringBuilder()
        outputBuffer.appendLine("[SAQR Runtime Environment initialized]")

        var modifiedNodes = mutableListOf<String>()
        lines.forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.startsWith("fun ") || trimmed.startsWith("def ") -> {
                    outputBuffer.appendLine("Synthesized AST Declaration: $trimmed")
                    modifiedNodes.add("AST_FUNC_${trimmed.take(15)}")
                }
                trimmed.startsWith("patch(") -> {
                    outputBuffer.appendLine("Inlined Patch Applied: $trimmed")
                    modifiedNodes.add("DYNAMIC_INLINE_PATCH")
                }
                trimmed.startsWith("log(") || trimmed.startsWith("print(") -> {
                    val content = trimmed.substringAfter("(").substringBeforeLast(")")
                    outputBuffer.appendLine("Stdout: $content")
                }
                else -> {
                    outputBuffer.appendLine("Exec op: $trimmed")
                }
            }
        }
        outputBuffer.appendLine("[Script Execution Completed Successfully]")

        val duration = System.currentTimeMillis() - startTime
        telemetry.log(LogLevel.INFO, "AutonomousExtensionEngine", "Autonomous script completed in ${duration}ms (${lines.size} ops evaluated)")

        return ScriptExecutionResult(
            isSuccess = true,
            output = outputBuffer.toString(),
            executionDurationMs = duration,
            modifiedAstNodes = modifiedNodes
        )
    }
}
