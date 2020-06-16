package com.pushpushgo.sdk.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
internal data class Message(

    @Json(name = "from")
    val from: String?,

    @Json(name = "payload")
    val payload: Map<String, String>,

    @Json(name = "body")
    val body: String?
) : Serializable
