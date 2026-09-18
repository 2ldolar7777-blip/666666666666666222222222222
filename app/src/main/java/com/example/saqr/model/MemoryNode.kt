package com.example.saqr.model

data class MemoryNode(
    val id: String,
    val title: String,
    val content: String,
    val tags: List<String>,
    val vectorEmbedding: List<Float>,
    val recencyScore: Float,
    val accessCount: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)
