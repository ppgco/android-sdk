package com.pushpushgo.sdk.network.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class TokenResponse(

    @Json(name = "_id")
    val id: String
)
