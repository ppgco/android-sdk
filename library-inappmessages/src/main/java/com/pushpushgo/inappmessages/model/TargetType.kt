package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json

/**
 * Enum representing the target for a redirect action.
 */
enum class TargetType {
    @Json(name = "_self") SELF,
    @Json(name = "_blank") BLANK
}
