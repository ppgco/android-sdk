package com.pushpushgo.sdk.data

import com.google.gson.annotations.SerializedName
import java.io.Serializable

internal data class Event(

    @SerializedName("payload")
    val payload: Payload,

    @SerializedName("type")
    val type: String
) : Serializable

internal enum class EventType(val value: String) {
    CLICKED("clicked"),
    DELIVERED("delivered")
}
