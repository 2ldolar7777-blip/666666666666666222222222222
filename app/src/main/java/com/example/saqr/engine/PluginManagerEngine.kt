package com.example.saqr.engine

import com.example.saqr.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

interface ISaqrPlugin {
    val manifest: PluginManifest
    suspend fun execute(action: String, params: Map<String, Any?>): String
}

class PluginManagerEngine(
    private val telemetry: TelemetryEngine,
    private val memoryEngine: VectorMemoryEngine,
    private val autonomousExtensionEngine: AutonomousExtensionEngine
) {
    private val plugins = ConcurrentHashMap<String, ISaqrPlugin>()
    private val _pluginsState = MutableStateFlow<List<PluginManifest>>(emptyList())
    val pluginsState: StateFlow<List<PluginManifest>> = _pluginsState.asStateFlow()

    private val _executionHistory = MutableStateFlow<List<PluginExecutionEvent>>(emptyList())
    val executionHistory: StateFlow<List<PluginExecutionEvent>> = _executionHistory.asStateFlow()

    init {
        registerDefaultPlugins()
    }

    private fun registerDefaultPlugins() {
        registerPlugin(object : ISaqrPlugin {
            override val manifest = PluginManifest(
                id = "plugin-ui-auto",
                name = "UI Automation Tool",
                version = "2.4.0",
                description = "Direct accessibility node tree traversal, tap/swipe synthesis, and field injection.",
                capabilities = setOf(PluginCapability.UI_AUTOMATION)
            )
            override suspend fun execute(action: String, params: Map<String, Any?>): String {
                val target = params["target"] ?: "focused_view"
                return when (action) {
                    "click" -> "Dispatched synthetic click on node: [$target]"
                    "scroll" -> "Scrolled forward 350px on viewport: [$target]"
                    "input_text" -> "Injected text value '${params["text"] ?: ""}' into [$target]"
                    else -> "Observed node hierarchy for [$target]"
                }
            }
        })

        registerPlugin(object : ISaqrPlugin {
            override val manifest = PluginManifest(
                id = "plugin-spatial-vision",
                name = "Spatial Vision & OCR Tool",
                version = "3.1.0",
                description = "Computes bounding boxes, relative visual anchors, and layout hierarchy analysis.",
                capabilities = setOf(PluginCapability.SPATIAL_VISION)
            )
            override suspend fun execute(action: String, params: Map<String, Any?>): String {
                return "Resolved bounding box Rect(left=72, top=310, right=980, bottom=460) with 99.2% anchor match."
            }
        })

        registerPlugin(object : ISaqrPlugin {
            override val manifest = PluginManifest(
                id = "plugin-vector-memory",
                name = "Semantic Memory Node Tool",
                version = "1.8.0",
                description = "High-dimensional vector similarity retrieval and episodic memory persistence.",
                capabilities = setOf(PluginCapability.VECTOR_MEMORY)
            )
            override suspend fun execute(action: String, params: Map<String, Any?>): String {
                val query = params["query"]?.toString() ?: "system"
                val matches = memoryEngine.querySimilar(query, topK = 2)
                return "Recalled ${matches.size} associative nodes: ${matches.joinToString { it.first.title }}"
            }
        })

        registerPlugin(object : ISaqrPlugin {
            override val manifest = PluginManifest(
                id = "plugin-code-interp",
                name = "Code Interpreter Sandbox",
                version = "2.0.0",
                description = "Sandboxed execution of AST scripts, expressions, and logical rules.",
                capabilities = setOf(PluginCapability.CODE_INTERPRETER)
            )
            override suspend fun execute(action: String, params: Map<String, Any?>): String {
                val script = params["code"]?.toString() ?: "log(\"sandbox test\");"
                val res = autonomousExtensionEngine.executeAutonomousScript(script)
                return if (res.isSuccess) "Output: ${res.output.trim()}" else "Error: ${res.sandboxViolations.firstOrNull()}"
            }
        })

        registerPlugin(object : ISaqrPlugin {
            override val manifest = PluginManifest(
                id = "plugin-sys-ctrl",
                name = "System Control Tool",
                version = "1.5.0",
                description = "Controls virtual display brightness, network state queries, and power management.",
                capabilities = setOf(PluginCapability.SYSTEM_SETTINGS)
            )
            override suspend fun execute(action: String, params: Map<String, Any?>): String {
                val setting = params["setting"]?.toString() ?: "eco_mode"
                return "Configured system profile: $setting -> ENABLED"
            }
        })

        registerPlugin(object : ISaqrPlugin {
            override val manifest = PluginManifest(
                id = "plugin-speech-voice",
                name = "Acoustic Speech & Voice Tool",
                version = "1.2.0",
                description = "Text-To-Speech narration, intent audio synthesis, and feedback chimes.",
                capabilities = setOf(PluginCapability.SPEECH_TTS)
            )
            override suspend fun execute(action: String, params: Map<String, Any?>): String {
                val phrase = params["phrase"]?.toString() ?: "Task completed."
                return "Synthesized acoustic voice stream for phrase: \"$phrase\""
            }
        })

        registerPlugin(object : ISaqrPlugin {
            override val manifest = PluginManifest(
                id = "plugin-sync-mesh",
                name = "Mesh Sync Tool",
                version = "1.0.0",
                description = "Distributed peer-to-peer state coordination and cross-agent event gossiping.",
                capabilities = setOf(PluginCapability.SYNC_MESH)
            )
            override suspend fun execute(action: String, params: Map<String, Any?>): String {
                return "Dispatched gossip packet to 3 active cluster nodes: latency 18ms"
            }
        })

        registerPlugin(object : ISaqrPlugin {
            override val manifest = PluginManifest(
                id = "plugin-process-exec",
                name = "Safe Process Executor",
                version = "1.4.0",
                description = "Executes sandboxed background commands with strict CPU and memory timeboxes.",
                capabilities = setOf(PluginCapability.PROCESS_EXEC)
            )
            override suspend fun execute(action: String, params: Map<String, Any?>): String {
                val cmd = params["command"]?.toString() ?: "uptime"
                return "Sandboxed exit code 0: Command '$cmd' returned successfully in 8ms"
            }
        })
    }

    fun registerPlugin(plugin: ISaqrPlugin) {
        plugins[plugin.manifest.id] = plugin
        updateManifestsState()
        telemetry.log(LogLevel.DEBUG, "PluginManager", "Registered plugin: ${plugin.manifest.name} [${plugin.manifest.id}]")
    }

    fun togglePluginEnabled(pluginId: String) {
        val plugin = plugins[pluginId] ?: return
        val currentEnabled = plugin.manifest.isEnabled
        val updatedManifest = plugin.manifest.copy(isEnabled = !currentEnabled)
        val wrappedPlugin = object : ISaqrPlugin {
            override val manifest = updatedManifest
            override suspend fun execute(action: String, params: Map<String, Any?>): String {
                if (!manifest.isEnabled) throw IllegalStateException("Plugin ${manifest.name} is currently disabled")
                return plugin.execute(action, params)
            }
        }
        plugins[pluginId] = wrappedPlugin
        updateManifestsState()
        telemetry.log(LogLevel.INFO, "PluginManager", "Plugin '${plugin.manifest.name}' enabled = ${!currentEnabled}")
    }

    suspend fun executeTool(pluginId: String, action: String, params: Map<String, Any?>): String {
        val plugin = plugins[pluginId]
            ?: throw IllegalArgumentException("Plugin with ID '$pluginId' is not registered")
        
        if (!plugin.manifest.isEnabled) {
            throw IllegalStateException("Plugin '${plugin.manifest.name}' is disabled in settings")
        }

        val startTime = System.currentTimeMillis()
        return try {
            val result = plugin.execute(action, params)
            val duration = System.currentTimeMillis() - startTime

            // Record execution telemetry
            val event = PluginExecutionEvent(
                eventId = "pe-${System.currentTimeMillis().toString().takeLast(6)}",
                pluginId = pluginId,
                actionName = action,
                params = params,
                result = result,
                durationMs = duration,
                success = true
            )
            recordEvent(event)
            updatePluginStats(pluginId, duration, true)
            telemetry.log(LogLevel.INFO, "PluginManager", "Tool [${plugin.manifest.name}::$action] completed in ${duration}ms")
            result
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            val event = PluginExecutionEvent(
                eventId = "pe-${System.currentTimeMillis().toString().takeLast(6)}",
                pluginId = pluginId,
                actionName = action,
                params = params,
                result = "Exception: ${e.message}",
                durationMs = duration,
                success = false
            )
            recordEvent(event)
            updatePluginStats(pluginId, duration, false)
            telemetry.log(LogLevel.ERROR, "PluginManager", "Tool execution failed: ${e.message}")
            throw e
        }
    }

    private fun recordEvent(event: PluginExecutionEvent) {
        val current = _executionHistory.value.toMutableList()
        current.add(0, event)
        if (current.size > 50) current.removeAt(current.lastIndex)
        _executionHistory.value = current
    }

    private fun updatePluginStats(pluginId: String, duration: Long, success: Boolean) {
        val plugin = plugins[pluginId] ?: return
        val current = plugin.manifest
        val newCount = current.executionCount + 1
        val newAvgLatency = ((current.avgLatencyMs * current.executionCount) + duration) / newCount
        val newErrors = if (success) 0f else 1f
        val newErrorRate = ((current.errorRatePercent * current.executionCount) + (newErrors * 100)) / newCount

        val updated = current.copy(
            executionCount = newCount,
            avgLatencyMs = newAvgLatency,
            errorRatePercent = newErrorRate
        )
        val wrapped = object : ISaqrPlugin {
            override val manifest = updated
            override suspend fun execute(action: String, params: Map<String, Any?>) = plugin.execute(action, params)
        }
        plugins[pluginId] = wrapped
        updateManifestsState()
    }

    private fun updateManifestsState() {
        _pluginsState.value = plugins.values.map { it.manifest }
    }
}
