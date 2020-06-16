package com.pushpushgo.sdk.network.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class TokenRequest(

    @Json(name = "token")
    val token: String
)
