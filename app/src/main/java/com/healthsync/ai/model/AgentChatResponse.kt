package com.healthsync.ai.model

data class AgentChatResponse(
    val assistantResponse: String,
    val stepsToday: Double? = null,
    val memoryId: String? = null
)
