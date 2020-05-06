package com.pushpushgo.sdk

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.pushpushgo.sdk.exception.PushPushException

class BeaconBuilder internal constructor(private var onBeaconSend: (JsonObject) -> Unit) {

    private val selectors = mutableMapOf<String, Any>()

    private val tags = mutableListOf<Pair<String, String>>()

    private val tagsToDelete = mutableListOf<String>()

    private var customId = ""

    /**
     * Add beacon selector
     *
     * @param key
     * @param value Selector value. Supported types: boolean, string, char, number
     * @return instance of builder
     */
    fun set(key: String, value: Any): BeaconBuilder {
        selectors[key] = value

        return this
    }

    /**
     * @param tag Tag name
     * @param label Tag label
     *
     * @return instance of builder
     */
    fun appendTag(tag: String, label: String): BeaconBuilder {
        tags.add(tag to label)

        return this
    }

    fun appendTag(tag: String): BeaconBuilder {
        tags.add(tag to "default")

        return this
    }

    fun getTags(): MutableList<Pair<String, String>> {
        return tags
    }

    /**
     * @param name Tag name
     *
     * @return instance of builder
     */
    fun removeTag(vararg name: String): BeaconBuilder {
        tagsToDelete.addAll(name)

        return this
    }

    fun getTagsToDelete(): MutableList<String> {
        return tagsToDelete
    }

    /**
     * Set custom beacon ID
     *
     * @param id Custom ID
     *
     * @return instance of builder
     */
    fun setCustomId(id: String): BeaconBuilder {
        customId = id

        return this
    }

    /**
     * @throws PushPushException on unsupported selector value type
     */
    fun send() {
        onBeaconSend(JsonObject().apply {
            addSelectors()
            addTags()
            addTagsToDelete()
            addProperty("customId", customId)
        })
    }

    private fun JsonObject.addSelectors() {
        selectors.forEach { (key, value) ->
            when (value) {
                is Boolean -> addProperty(key, value)
                is String -> addProperty(key, value)
                is Char -> addProperty(key, value)
                is Number -> addProperty(key, value)
                else -> throw PushPushException("Invalid type of beacon selector value. Supported types: boolean, string, char, number")
            }
        }
    }

    private fun JsonObject.addTags() {
        add("tags", JsonArray().apply {
            tags.forEach { (tag, label) ->
                add(JsonObject().apply {
                    addProperty("tag", tag)
                    addProperty("label", label)
                })
            }
        })
    }

    private fun JsonObject.addTagsToDelete() {
        add("tagsToDelete", JsonArray().apply {
            tagsToDelete.forEach {
                add(it)
            }
        })
    }
}
