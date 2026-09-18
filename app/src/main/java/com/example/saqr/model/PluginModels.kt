package com.example.saqr.model

enum class PluginCapability(val label: String, val iconName: String) {
    UI_AUTOMATION("UI Automation", "touch_app"),
    SPATIAL_VISION("Spatial Vision", "visibility"),
    VECTOR_MEMORY("Vector Memory", "psychology"),
    CODE_INTERPRETER("Code Interpreter", "terminal"),
    PROCESS_EXEC("Process Executor", "memory"),
    SYSTEM_SETTINGS("System Control", "settings"),
    SPEECH_TTS("Speech & Acoustic", "record_voice_over"),
    SYNC_MESH("Sync Mesh", "hub")
}

data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val capabilities: Set<PluginCapability>,
    val isSandboxed: Boolean = true,
    val isEnabled: Boolean = true,
    val executionCount: Int = 0,
    val avgLatencyMs: Long = 0L,
    val errorRatePercent: Float = 0.0f
)

data class PluginExecutionEvent(
    val eventId: String,
    val pluginId: String,
    val actionName: String,
    val params: Map<String, Any?>,
    val result: String,
    val durationMs: Long,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
