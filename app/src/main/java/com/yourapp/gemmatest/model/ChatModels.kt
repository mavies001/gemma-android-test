package com.yourapp.gemmatest.model

enum class Role { USER, AI }

data class ChatMsg(
    val role: Role,
    val text: String,
    val streaming: Boolean = false
)
