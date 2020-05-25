package com.pushpushgo.sdk.data

import com.google.gson.annotations.SerializedName
import java.io.Serializable

internal data class Message(

    @SerializedName("from")
    val from: String?,

    @SerializedName("payload")
    val payload: Map<String, String>,

    @SerializedName("body")
    val body: String?
) : Serializable
