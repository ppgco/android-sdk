package com.pushpushgo.sdk.push.data

import com.pushpushgo.sdk.push.BeaconTagStrategy

internal data class BeaconTag(
  val tag: String,
  val label: String,
  val strategy: BeaconTagStrategy = BeaconTagStrategy.APPEND,
  val ttl: Int = 0,
)
