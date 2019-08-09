package com.pushpushgo.sdk.data

import org.json.JSONObject
import java.io.Serializable

data class Beacon(
    val your_selector_name:String,
    val tags:JSONObject,
    val tagsToDelete:Array<String>,
    val customId: String
):Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Beacon

        if (your_selector_name != other.your_selector_name) return false
        if (tags != other.tags) return false
        if (!tagsToDelete.contentEquals(other.tagsToDelete)) return false
        if (customId != other.customId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = your_selector_name.hashCode()
        result = 31 * result + tags.hashCode()
        result = 31 * result + tagsToDelete.contentHashCode()
        result = 31 * result + customId.hashCode()
        return result
    }
}