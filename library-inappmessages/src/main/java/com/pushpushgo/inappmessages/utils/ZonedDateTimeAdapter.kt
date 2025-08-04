package com.pushpushgo.inappmessages.utils

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import java.io.IOException
import java.lang.reflect.Type
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal class ZonedDateTimeAdapter : JsonAdapter<ZonedDateTime>() {
  companion object {
    val FACTORY =
      object : Factory {
        override fun create(
          type: Type,
          annotations: Set<Annotation>,
          moshi: Moshi,
        ): JsonAdapter<*>? {
          if (type == ZonedDateTime::class.java) {
            return ZonedDateTimeAdapter().nullSafe()
          }
          return null
        }
      }
  }

  @Throws(IOException::class)
  override fun fromJson(reader: JsonReader): ZonedDateTime? {
    val dateString = reader.nextString()
    return ZonedDateTime.parse(dateString, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  }

  @Throws(IOException::class)
  override fun toJson(
    writer: JsonWriter,
    value: ZonedDateTime?,
  ) {
    writer.value(value?.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
  }
}
