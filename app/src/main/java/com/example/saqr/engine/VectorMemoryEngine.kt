package com.example.saqr.engine

import com.example.saqr.model.LogLevel
import com.example.saqr.model.MemoryNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

class VectorMemoryEngine(
    private val telemetry: TelemetryEngine
) {
    private val memoryStore = ConcurrentHashMap<String, MemoryNode>()
    private val _nodesState = MutableStateFlow<List<MemoryNode>>(emptyList())
    val nodesState: StateFlow<List<MemoryNode>> = _nodesState.asStateFlow()

    init {
        seedInitialKnowledge()
    }

    private fun seedInitialKnowledge() {
        val seeds = listOf(
            MemoryNode(
                id = "mem-sys-01",
                title = "Android Accessibility Bridge",
                content = "Access node tree via AccessibilityNodeInfo, filter visible clickable nodes, dispatch ACTION_CLICK.",
                tags = listOf("accessibility", "ui_action", "bridge"),
                vectorEmbedding = generateEmbedding("Access node tree via AccessibilityNodeInfo"),
                recencyScore = 0.95f,
                accessCount = 14
            ),
            MemoryNode(
                id = "mem-sys-02",
                title = "Thermal Throttling Fallback",
                content = "When thermal state enters SEVERE, scale down worker coroutine concurrency and reduce canvas shader layers.",
                tags = listOf("hardware", "thermal", "throttle", "concurrency"),
                vectorEmbedding = generateEmbedding("When thermal state enters SEVERE, scale down worker coroutine concurrency"),
                recencyScore = 0.92f,
                accessCount = 9
            ),
            MemoryNode(
                id = "mem-sys-03",
                title = "AST Self-Repair Heuristics",
                content = "Detect obsolete UI element selectors, apply dynamic AST syntax transform to target updated resource id.",
                tags = listOf("ast", "patch", "self_repair", "dom"),
                vectorEmbedding = generateEmbedding("Detect obsolete UI element selectors, apply dynamic AST syntax transform"),
                recencyScore = 0.88f,
                accessCount = 22
            ),
            MemoryNode(
                id = "mem-sys-04",
                title = "Plugin Sandbox Security Guard",
                content = "Sandbox wrappers enforce capability permission validation and non-blocking timeout boundaries.",
                tags = listOf("security", "sandbox", "plugins", "permissions"),
                vectorEmbedding = generateEmbedding("Sandbox wrappers enforce capability permission validation"),
                recencyScore = 0.81f,
                accessCount = 6
            )
        )
        seeds.forEach { memoryStore[it.id] = it }
        _nodesState.value = memoryStore.values.toList()
    }

    fun storeNode(title: String, content: String, tags: List<String>): MemoryNode {
        val id = "mem-${System.currentTimeMillis().toString().takeLast(6)}"
        val embedding = generateEmbedding("$title $content ${tags.joinToString(" ")}")
        val node = MemoryNode(
            id = id,
            title = title,
            content = content,
            tags = tags,
            vectorEmbedding = embedding,
            recencyScore = 1.0f,
            accessCount = 1
        )
        memoryStore[id] = node
        _nodesState.value = memoryStore.values.toList()
        telemetry.log(LogLevel.DEBUG, "VectorMemoryEngine", "Stored vector node: '$title' [ID: $id]")
        return node
    }

    fun querySimilar(query: String, topK: Int = 3): List<Pair<MemoryNode, Float>> {
        val start = System.currentTimeMillis()
        val queryVector = generateEmbedding(query)
        val scored = memoryStore.values.map { node ->
            val sim = cosineSimilarity(queryVector, node.vectorEmbedding)
            val weightedScore = (sim * 0.7f) + (node.recencyScore * 0.2f) + ((node.accessCount / 30f).coerceAtMost(0.1f))
            node to weightedScore
        }.sortedByDescending { it.second }.take(topK)

        // Increment access on recalled nodes
        scored.forEach { (node, _) ->
            val updated = node.copy(accessCount = node.accessCount + 1, recencyScore = (node.recencyScore * 0.98f + 0.02f).coerceAtMost(1f))
            memoryStore[node.id] = updated
        }
        _nodesState.value = memoryStore.values.toList()

        val latency = System.currentTimeMillis() - start
        telemetry.log(LogLevel.INFO, "VectorMemoryEngine", "Vector query '$query' returned ${scored.size} matches", latency)
        return scored
    }

    private fun generateEmbedding(text: String): List<Float> {
        // High-dimensional deterministic embedding projection (16-dimensions)
        val dimensions = 16
        val vector = FloatArray(dimensions)
        val words = text.lowercase().split("\\s+".toRegex())
        words.forEachIndexed { wordIdx, word ->
            val hash = word.hashCode()
            for (i in 0 until dimensions) {
                val weight = ((hash shr (i * 2)) and 0xFF) / 255f
                vector[i] += weight * (1f / (wordIdx + 1))
            }
        }
        // Normalize
        val norm = sqrt(vector.map { it * it }.sum())
        return if (norm > 0f) vector.map { it / norm } else vector.toList()
    }

    private fun cosineSimilarity(v1: List<Float>, v2: List<Float>): Float {
        if (v1.isEmpty() || v2.isEmpty() || v1.size != v2.size) return 0f
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in v1.indices) {
            dot += v1[i] * v2[i]
            normA += v1[i] * v1[i]
            normB += v2[i] * v2[i]
        }
        val denominator = (sqrt(normA) * sqrt(normB))
        return if (denominator > 0f) (dot / denominator).coerceIn(0f, 1f) else 0f
    }
}
