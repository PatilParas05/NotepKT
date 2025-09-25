package com.example.notepkt.models

import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val userId: Int
)