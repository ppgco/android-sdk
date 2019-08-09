package com.pushpushgo.sdk.data

import java.io.Serializable

data class Payload(
    val button: Int,
    val campaign: String,
    val subscriber: String,
    val timestamp: Int
):Serializable