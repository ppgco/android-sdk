package com.pushpushgo.inappmessages.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Enum representing the font families that can be received from the backend.
 */
@JsonClass(generateAdapter = false)
internal enum class FontFamily {
  @Json(name = "Roboto")
  ROBOTO,

  @Json(name = "Open Sans")
  OPEN_SANS,

  @Json(name = "Montserrat")
  MONTSERRAT,

  @Json(name = "Inter")
  INTER,

  @Json(name = "Poppins")
  POPPINS,

  @Json(name = "Lato")
  LATO,

  @Json(name = "Playfair Display")
  PLAYFAIR_DISPLAY,

  @Json(name = "Fira Sans")
  FIRA_SANS,

  @Json(name = "Arial")
  ARIAL,

  @Json(name = "Georgia")
  GEORGIA,
}
