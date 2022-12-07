package com.pushpushgo.sdk.beacon

import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.work.UploadDelegate
import org.json.JSONArray
import org.json.JSONObject

class BeaconBuilder internal constructor(private val uploadDelegate: UploadDelegate) {

    private val selectors = mutableMapOf<String, Any>()

    private val tags = mutableListOf<BeaconTag>()

    private val tagsToDelete = mutableListOf<BeaconTag>()

    private var customId = ""

    /**
     * Add beacon selector
     *
     * @param key
     * @param value Selector value. Supported types: boolean, string, char, number
     * @return instance of builder
     */
    fun set(key: String, value: Any) = apply { selectors[key] = value }

    /**
     * @param tag BeaconTag object containing tag and label
     *
     * @return instance of builder
     */
    fun appendTag(tag: BeaconTag) = apply { tags.add(tag) }

    fun appendTag(tag: String) = apply { tags.add(BeaconTag(tag, "default")) }

    fun appendTags(vararg beaconTags: BeaconTag) = apply { tags.addAll(beaconTags) }

    fun getTags() = tags

    /**
     * @param tag BeaconTag object containing tag and label
     *
     * @return instance of builder
     */
    fun removeTag(tag: BeaconTag) = apply { tagsToDelete.add(tag) }

    fun removeTag(tag: String) = apply { tagsToDelete.add(BeaconTag(tag, "default")) }

    fun removeTags(vararg beaconTags: BeaconTag) = apply { tagsToDelete.addAll(beaconTags) }

    fun getTagsToDelete() = tagsToDelete

    /**
     * Set custom beacon ID
     *
     * @param id Custom ID
     *
     * @return instance of builder
     */
    fun setCustomId(id: String?) = apply { customId = id.orEmpty() }

    fun setCustomId(id: Int?) = apply { customId = (id ?: 0).toString().takeIf { it != "0" }.orEmpty() }

    /**
     * @throws PushPushException on unsupported selector value type
     */
    fun send() {
        uploadDelegate.sendBeacon(JSONObject().apply {
            addSelectors()
            addTags()
            addTagsToDelete()
            if (customId.isNotEmpty()) put("customId", customId)
        })
    }

    private fun JSONObject.addSelectors() {
        selectors.forEach { (key, value) ->
            when (value) {
                is Boolean -> put(key, value)
                is String -> put(key, value)
                is Char -> put(key, value)
                is Number -> put(key, value)
                else -> throw PushPushException("Invalid type of beacon selector value. Supported types: boolean, string, char, number")
            }
        }
    }

    private fun JSONObject.addTags() {
        tags.ifEmpty { return }

        put("tags", JSONArray().apply {
            tags.forEach { (tag, label) ->
                put(JSONObject().apply {
                    put("tag", tag)
                    put("label", label)
                })
            }
        })
    }

    private fun JSONObject.addTagsToDelete() {
        tagsToDelete.ifEmpty { return }

        put("tagsToDelete", JSONArray().apply {
            tagsToDelete.forEach { (tag, label) ->
                put(JSONObject().apply {
                    put("tag", tag)
                    put("label", label)
                })
            }
        })
    }
}
