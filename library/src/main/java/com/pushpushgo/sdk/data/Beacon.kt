package com.pushpushgo.sdk.data

import org.json.JSONObject
import java.io.Serializable

data class Beacon(
    val your_selector_name: String,
    val tags: JSONObject,
    val tagsToDelete: List<String>,
    val customId: String
) : Serializable
