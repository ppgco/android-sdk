package com.pushpushgo.sdk.data

internal data class BeaconTag(
    val tag: String,
    val label: String,
    val strategy: String = "append",
    val ttl: Int = 0,
)
