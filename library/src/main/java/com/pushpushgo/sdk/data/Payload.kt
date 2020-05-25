package com.pushpushgo.sdk.data

import com.google.gson.annotations.SerializedName
import java.io.Serializable

internal data class Payload(

    @SerializedName("button")
    val button: Int,

    @SerializedName("campaign")
    val campaign: String,

    @SerializedName("subscriber")
    val subscriber: String,

    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
