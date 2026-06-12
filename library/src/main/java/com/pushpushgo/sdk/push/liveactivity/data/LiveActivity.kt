package com.pushpushgo.sdk.push.liveactivity.data

/**
 * In-memory representation of a tracked Live Activity. Combines the static
 * [configuration] (received once with the `start` event) and the latest dynamic
 * [liveData] / [hotMessage] (refreshed by `update` events). Mirrors the backend
 * / iOS Live Notification contract.
 */
data class LiveActivity(
  val id: String,
  val projectId: String,
  val subscriberId: String,
  val template: LiveActivityTemplate,
  val status: LiveActivityStatus,
  val configuration: FootballMatchConfiguration,
  val liveData: FootballMatchLiveData,
  val hotMessage: HotMessage?,
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  /** Pre-match countdown message (from `startPolicy.countdown`), if any. */
  val countdownMessage: String? = null,
  /** Epoch millis of `startPolicy.scheduledAt` — the moment the countdown hits zero. */
  val countdownEndAtMs: Long? = null,
)
