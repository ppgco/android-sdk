package com.pushpushgo.sdk.network.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ProjectTag(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "tagLabel") val label: String?
)
