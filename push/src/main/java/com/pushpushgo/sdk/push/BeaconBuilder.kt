package com.pushpushgo.sdk.push

import com.pushpushgo.sdk.push.data.BeaconTag
import com.pushpushgo.sdk.push.work.UploadDelegate
import org.json.JSONArray
import org.json.JSONObject

enum class BeaconTagStrategy {
  APPEND,
  REWRITE,
}

class BeaconBuilder internal constructor(
  private val uploadDelegate: UploadDelegate,
) {
  private val selectors = mutableMapOf<String, Any>()

  private val tags = mutableListOf<BeaconTag>()

  private val tagsToDelete = mutableListOf<Pair<String, String>>()

  private var customId = ""

  private var assignToGroup: String? = null

  private var unassignFromGroup: String? = null

  /**
   * @param key Selector key.
   * @param value Selector value.
   */
  fun set(
    key: String,
    value: String,
  ) = apply {
    selectors[key] = value
  }

  /**
   * @param key Selector key.
   * @param value Selector value.
   */
  fun set(
    key: String,
    value: Number,
  ) = apply {
    selectors[key] = value
  }

  /**
   * @param key Selector key.
   * @param value Selector value.
   */
  fun set(
    key: String,
    value: Boolean,
  ) = apply {
    selectors[key] = value
  }

  /**
   * @param key Selector key.
   * @param value Selector value.
   */
  fun set(
    key: String,
    value: Char,
  ) = apply {
    selectors[key] = value
  }

  fun getTags(): List<Pair<String, String>> = tags.map { it.tag to it.label }.toList()

  fun getTagsToDelete(): List<String> = tagsToDelete.map { it.first }

  /**
   * @param tag Tag name.
   * @param label Tag label. Defaults to `"default"`.
   * @param strategy Tag assignment strategy:
   * - [BeaconTagStrategy.APPEND] to accumulate values
   * - [BeaconTagStrategy.REWRITE] to overwrite existing values
   * @param ttl Time to live for the tag in seconds.
   * Use `0` to keep the tag indefinitely.
   *
   * @return This builder instance.
   */
  @JvmOverloads
  fun appendTag(
    tag: String,
    label: String = "default",
    strategy: BeaconTagStrategy = BeaconTagStrategy.APPEND,
    ttl: Int = 0,
  ): BeaconBuilder {
    tags.add(BeaconTag(tag = tag, label = label, strategy = strategy, ttl = ttl))

    return this
  }

  /**
   * Removes tags using the default label.
   * Only tags with label `"default"` will be removed.
   *
   * @param name One or more tag names.
   * @return This builder instance.
   */
  fun removeTag(vararg name: String): BeaconBuilder {
    tagsToDelete.addAll(name.map { it to "default" })

    return this
  }

  /**
   * Removes tags using explicit tag–label mapping.
   * Only tags matching both the name and label will be removed.
   *
   * @param tags Map where:
   * - key: tag name
   * - value: tag label
   *
   * @return This builder instance.
   */
  fun removeTags(tags: Map<String, String>): BeaconBuilder {
    tagsToDelete.addAll(tags.map { it.key to it.value })

    return this
  }

  /**
   * @param tags List of tag-label pairs to remove
   *
   * @return instance of builder
   */
  fun removeTags(tags: List<Pair<String, String>>): BeaconBuilder {
    tagsToDelete.addAll(tags)

    return this
  }

  /**
   * Sets a custom beacon identifier.
   *
   * Passing `null` clears the custom ID.
   *
   * @param id Custom identifier.
   * @return This builder instance.
   */
  fun setCustomId(id: String?): BeaconBuilder {
    customId = id.orEmpty()

    return this
  }

  /**
   * Assign subscriber to a dynamic group
   *
   * @param groupId ID of the dynamic group to assign to
   *
   * @return instance of builder
   */
  fun assignToGroup(groupId: String): BeaconBuilder {
    assignToGroup = groupId

    return this
  }

  /**
   * Unassign subscriber from a dynamic group
   *
   * @param groupId ID of the dynamic group to unassign from
   *
   * @return instance of builder
   */
  fun unassignFromGroup(groupId: String): BeaconBuilder {
    unassignFromGroup = groupId

    return this
  }

  /**
   * Dispatches the configured beacon.
   */
  fun send() {
    uploadDelegate.sendBeacon(
      JSONObject().apply {
        addSelectors()
        addTags()
        addTagsToDelete()
        if (customId.isNotEmpty()) put("customId", customId)
        assignToGroup?.let { put("assignToGroup", it) }
        unassignFromGroup?.let { put("unassignFromGroup", it) }
      },
    )
  }

  private fun JSONObject.addSelectors() {
    selectors.forEach { (key, value) ->
      when (value) {
        is Boolean -> put(key, value)
        is String -> put(key, value)
        is Char -> put(key, value)
        is Number -> put(key, value)
        else -> throw IllegalArgumentException("Invalid type of beacon selector value. Supported types: boolean, string, char, number")
      }
    }
  }

  private fun JSONObject.addTags() {
    tags.ifEmpty { return }

    put(
      "tags",
      JSONArray().apply {
        tags.forEach { (tag, label, strategy, ttl) ->
          put(
            JSONObject().apply {
              put("tag", tag)
              put("label", label)
              put("strategy", if (strategy == BeaconTagStrategy.APPEND) "append" else "rewrite")
              put("ttl", ttl)
            },
          )
        }
      },
    )
  }

  private fun JSONObject.addTagsToDelete() {
    tagsToDelete.ifEmpty { return }

    put(
      "tagsToDelete",
      JSONArray().apply {
        if (tagsToDelete.all { it.second == "default" }) {
          tagsToDelete.forEach {
            put(it.first)
          }
        } else {
          tagsToDelete.forEach { (tag, label) ->
            put(
              JSONObject().apply {
                put("tag", tag)
                put("label", label)
              },
            )
          }
        }
      },
    )
  }
}
