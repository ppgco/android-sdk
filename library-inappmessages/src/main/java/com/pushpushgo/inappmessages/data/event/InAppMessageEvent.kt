package com.pushpushgo.inappmessages.data.event

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class InAppEventRequest(
    val action: String,
    val inApp: String
)
