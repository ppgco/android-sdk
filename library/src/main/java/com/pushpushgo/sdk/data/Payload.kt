package com.pushpushgo.sdk.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
internal data class Payload(

    @Json(name = "button")
    val button: Int,

    @Json(name = "campaign")
    val campaign: String,

    @Json(name = "subscriber")
    val subscriber: String,

    @Json(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
