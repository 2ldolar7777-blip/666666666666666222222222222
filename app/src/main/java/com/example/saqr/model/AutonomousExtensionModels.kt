package com.example.saqr.model

enum class PatchStatus(val label: String) {
    GENERATED("Synthesized"),
    VALIDATED("Sandbox Verified"),
    APPLIED("Live Applied"),
    ROLLED_BACK("Safely Rolled Back"),
    REJECTED("Safety Rejected")
}

data class AstNode(
    val id: String,
    val type: String, // e.g. "SelectorRule", "EventHandler", "CoroutinePipeline", "FallbackRoute"
    val name: String,
    val parameters: Map<String, String> = emptyMap(),
    val children: List<AstNode> = emptyList()
)

data class AstPatch(
    val patchId: String,
    val targetModule: String,
    val originalSyntax: String,
    val replacementSyntax: String,
    val rationale: String,
    val confidenceScore: Float,
    val status: PatchStatus = PatchStatus.GENERATED,
    val timestamp: Long = System.currentTimeMillis()
)

data class ScriptExecutionResult(
    val isSuccess: Boolean,
    val output: String,
    val executionDurationMs: Long,
    val sandboxViolations: List<String> = emptyList(),
    val modifiedAstNodes: List<String> = emptyList()
)

data class StateCheckpoint(
    val checkpointId: String,
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val snapshotData: Map<String, String>
)
