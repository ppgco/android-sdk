package com.pushpushgo.sdk.data

import java.io.Serializable

data class Message(
    val from:String?,
    val payload: Map<String,String>,
    val body: String?
):Serializable