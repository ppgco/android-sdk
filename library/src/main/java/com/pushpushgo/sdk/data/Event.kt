package com.pushpushgo.sdk.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
internal data class Event(

    @Json(name = "payload")
    val payload: Payload,

    @Json(name = "type")
    val type: String
) : Serializable

internal enum class EventType(val value: String) {
    CLICKED("clicked"),
    DELIVERED("delivered")
}
