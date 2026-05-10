package com.healthsync.ai.model

data class AgentChatRequest(
    val message: String,
    val userId: String,
    val deviceId: String? = null,
    val timezoneOffsetMinutes: Int = 0,
    val chatId: String? = null
)
