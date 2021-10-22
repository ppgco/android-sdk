package com.pushpushgo.sdk.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
internal data class PushPushNotification(

    @Json(name = "project")
    val project: String,

    @Json(name = "subscriber")
    val subscriber: String,

    @Json(name = "campaignId")
    val campaignId: String,

    @Json(name = "notification")
    val notification: Notification,

    @Json(name = "actions")
    val actions: List<Action>,

    @Json(name = "icon")
    val icon: String,

    @Json(name = "image")
    val image: String,

    @Json(name = "redirectLink")
    val redirectLink: String,
)

@JsonClass(generateAdapter = true)
internal data class Notification(

    @Json(name = "badge")
    val badge: Int = 0,

    @Json(name = "sound")
    val sound: String?,

    @Json(name = "vibrate")
    val vibrate: String = "true",

    @Json(name = "title")
    val title: String?,

    @Json(name = "body")
    val body: String?,

    @Json(name = "priority")
    val priority: Int = 0,

    @Json(name = "click_action")
    val click_action: String?,
)

@JsonClass(generateAdapter = true)
internal data class Action(

    @Json(name = "link")
    val link: String,

    @Json(name = "action")
    val action: String,

    @Json(name = "title")
    val title: String,
)
