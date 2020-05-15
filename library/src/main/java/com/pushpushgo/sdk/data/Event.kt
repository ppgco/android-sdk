package com.pushpushgo.sdk.data

import java.io.Serializable

internal data class Event(
    val payload: Payload,
    val type: String
) : Serializable

internal enum class EventType(val value: String) {
    CLICKED("clicked"),
    DELIVERED("delivered")
}
