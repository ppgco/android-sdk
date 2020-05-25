package com.pushpushgo.sdk.network.data

import com.google.gson.annotations.SerializedName

internal data class TokenRequest(
    @SerializedName("token")
    val token: String
)
