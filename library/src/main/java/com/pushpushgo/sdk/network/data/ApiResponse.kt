package com.pushpushgo.sdk.network.data

internal data class ApiResponse(
    val _id: String? = "",
    val message: String? = "",
    val messages: List<String>? = emptyList()
)
