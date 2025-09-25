package com.example.notepkt.models

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: Int? = null,
    val userId: Int? = null,
    val title: String,
    val content: String,
    val timestamp: Long? = null
)