package com.pushpushgo.sdk.push.dto

data class PushPushGoNotification(
  val campaignId: String,
  val title: String,
  val body: String,
  val redirectLink: String,
  val priority: Int = 0,
)
