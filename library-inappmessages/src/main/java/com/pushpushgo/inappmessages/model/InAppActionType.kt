package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json

/**
 * Enum representing the type of action to be performed by an in-app message button.
 */
enum class InAppActionType {
  @Json(name = "SUBSCRIBE")
  SUBSCRIBE,

  @Json(name = "REDIRECT")
  REDIRECT,

  @Json(name = "JS")
  JS,

  @Json(name = "CLOSE")
  CLOSE,
}
