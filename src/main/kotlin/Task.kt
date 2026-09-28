package com.example

import kotlinx.serialization.Serializable

@Serializable
data class Task(
    val id: Int? = null,
    val name: String,
    val description: String
)