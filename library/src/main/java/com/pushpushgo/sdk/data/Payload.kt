package com.pushpushgo.sdk.data

import java.io.Serializable

internal data class Payload(
    val button: Int,
    val campaign: String,
    val subscriber: String,
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
