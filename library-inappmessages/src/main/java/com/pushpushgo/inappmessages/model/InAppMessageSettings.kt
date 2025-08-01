package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data class representing the settings for an in-app message's behavior.
 */
@JsonClass(generateAdapter = true)
data class InAppMessageSettings(
    @Json(name = "triggerType") val triggerType: TriggerType,
    @Json(name = "showAfterDelay") val showAfterDelay: Long,
    @Json(name = "display") val display: DisplayType,
    @Json(name = "displayOn") val displayOn: List<DisplayOn>,
    @Json(name = "showAgain") val showAgain: ShowAgainType,
    @Json(name = "priority") val priority: Int,
    @Json(name = "scrollDepth") val scrollDepth: Int?,
    @Json(name = "showAfterTime") val showAfterTime: Long?,
    @Json(name = "customTriggerKey") val customTriggerKey: String? = null,
    @Json(name = "customTriggerValue") val customTriggerValue: String? = null
)
