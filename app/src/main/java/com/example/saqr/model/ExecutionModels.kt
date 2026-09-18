package com.example.saqr.model

enum class StepStatus(val label: String) {
    PENDING("Pending"),
    RUNNING("Running"),
    SUCCESS("Completed"),
    FAILED("Failed"),
    HEALING("Self-Healing"),
    ROLLED_BACK("Rolled Back"),
    SKIPPED("Skipped")
}

enum class StepCategory(val label: String) {
    PERCEPTION("Visual Perception"),
    PLANNING("DAG Planning"),
    UI_ACTION("UI Automation"),
    AST_PATCH("AST Self-Modification"),
    TOOL_INVOCATION("Plugin Tool"),
    SELF_HEAL("Autonomous Recovery"),
    VERIFICATION("State Verification")
}

data class ExecutionStep(
    val id: String,
    val title: String,
    val description: String,
    val category: StepCategory,
    val status: StepStatus = StepStatus.PENDING,
    val dependsOn: List<String> = emptyList(),
    val executionTimeMs: Long = 0L,
    val retryCount: Int = 0,
    val errorDetails: String? = null,
    val patchDiff: String? = null,
    val outputPayload: String? = null
)

data class DAGPlan(
    val planId: String,
    val goalPrompt: String,
    val steps: List<ExecutionStep>,
    val isRunning: Boolean = false,
    val isCompleted: Boolean = false,
    val progress: Float = 0f,
    val startTimeMs: Long = 0L,
    val elapsedTimeMs: Long = 0L,
    val failureCount: Int = 0,
    val healedCount: Int = 0
)
