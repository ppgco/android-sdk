package com.pushpushgo.sdk.data

import java.io.Serializable

data class Event(
    val payload: Payload,
    val type: String
) : Serializable

enum class EventType(val value: String) {
    CLICKED("clicked"),
    DELIVERED("delivered")
}
