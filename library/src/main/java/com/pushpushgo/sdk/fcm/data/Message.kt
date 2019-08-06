package com.pushpushgo.sdk.fcm.data

import org.json.JSONObject
import java.io.Serializable

data class Message(
    val from:String?,
    val payload: Map<String,String>,
    val body: String?
):Serializable