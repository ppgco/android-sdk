package com.pushpushgo.sdk

@Suppress("unused", "UNUSED_PARAMETER")
class BeaconBuilder internal constructor() {
    @JvmOverloads
    fun appendTag(tag: String, label: String = "default", strategy: String = "append", ttl: Int = 0): BeaconBuilder = this
    fun getTags(): MutableList<Pair<String, String>> = mutableListOf()
    fun getTagsToDelete(): MutableList<String> = mutableListOf()
    fun removeTag(vararg name: String): BeaconBuilder = this
    fun send() = Unit
    fun set(key: String, value: Any): BeaconBuilder = this
    fun setCustomId(id: Int?): BeaconBuilder = this
    fun setCustomId(id: String?): BeaconBuilder = this
}
