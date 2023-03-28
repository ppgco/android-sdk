package com.pushpushgo.sdk

import com.pushpushgo.sdk.data.BeaconTag
import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.work.UploadDelegate
import org.json.JSONArray
import org.json.JSONObject

class BeaconBuilder internal constructor(private val uploadDelegate: UploadDelegate) {

    private val selectors = mutableMapOf<String, Any>()

    private val tags = mutableListOf<BeaconTag>()

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
     * @param strategy Determining whether the tags assigned to the given label should be accumulated (append) or overwritten (rewrite) (then the subscriber may have only one, most up-to-date tag at a time)
     * @param ttl Time to Live (TTL) is a parameter that specifies the time (in days or hours) after which a given tag is going to be removed. If you don't want to remove tags, type 0.
     *
     * @return instance of builder
     */
    @JvmOverloads
    fun appendTag(tag: String, label: String = "default", strategy: String = "append", ttl: Int = 0): BeaconBuilder {
        tags.add(BeaconTag(tag = tag, label = label, strategy = strategy, ttl = ttl))

        return this
    }

    fun getTags(): MutableList<Pair<String, String>> {
        return tags.map { it.tag to it.label }.toMutableList()
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
    fun setCustomId(id: String?): BeaconBuilder {
        customId = id.orEmpty()

        return this
    }

    fun setCustomId(id: Int?): BeaconBuilder {
        customId = (id ?: 0).toString().takeIf { it != "0" }.orEmpty()

        return this
    }

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
            tags.forEach { (tag, label, strategy, ttl) ->
                put(JSONObject().apply {
                    put("tag", tag)
                    put("label", label)
                    put("strategy", strategy)
                    put("ttl", ttl)
                })
            }
        })
    }

    private fun JSONObject.addTagsToDelete() {
        tagsToDelete.ifEmpty { return }

        put("tagsToDelete", JSONArray().apply {
            tagsToDelete.forEach {
                put(it)
            }
        })
    }
}
