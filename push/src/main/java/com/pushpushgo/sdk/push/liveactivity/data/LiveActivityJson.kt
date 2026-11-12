package com.pushpushgo.sdk.push.liveactivity.data

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.JsonQualifier
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import java.lang.reflect.Type
import java.time.Instant
import java.time.OffsetDateTime

/** A timestamp given as ISO-8601, or as epoch seconds or millis in a number or a string; read as epoch millis. */
@JsonQualifier
internal annotation class EpochMillis

/** Status labels given as an array ordered like [MatchPhase], or as a `{phase: label}` object. */
@JsonQualifier
internal annotation class StatusLabels

/** A JSON object kept as its raw JSON text. */
@JsonQualifier
internal annotation class RawJsonObject

/**
 * Moshi for Live Activity payloads. Their format is not part of the backend contract,
 * so reading is lenient:
 * - colors, status labels, numbers and timestamps are accepted in all their encodings;
 * - a value of an unexpected type reads as missing instead of failing the whole block.
 */
internal object LiveActivityJson {
  val moshi: Moshi =
    Moshi
      .Builder()
      .add(NullOnTypeMismatchFactory)
      .add(LenientValueFactory)
      .build()
}

/** Reads a value of an unexpected type as null instead of failing the whole payload. */
private object NullOnTypeMismatchFactory : JsonAdapter.Factory {
  override fun create(
    type: Type,
    annotations: Set<Annotation>,
    moshi: Moshi,
  ): JsonAdapter<*> {
    val delegate = moshi.nextAdapter<Any?>(this, type, annotations)
    return object : JsonAdapter<Any?>() {
      override fun fromJson(reader: JsonReader): Any? {
        val value =
          try {
            delegate.fromJson(reader.peekJson())
          } catch (e: JsonDataException) {
            null
          }
        reader.skipValue()
        return value
      }

      override fun toJson(
        writer: JsonWriter,
        value: Any?,
      ) = delegate.toJson(writer, value)
    }
  }
}

/** Adapters for the encodings Live Activity values come in. */
private object LenientValueFactory : JsonAdapter.Factory {
  override fun create(
    type: Type,
    annotations: Set<Annotation>,
    moshi: Moshi,
  ): JsonAdapter<*>? =
    when {
      annotations.any { it is EpochMillis } -> EpochMillisAdapter
      annotations.any { it is StatusLabels } -> StatusLabelsAdapter
      annotations.any { it is RawJsonObject } -> RawJsonObjectAdapter
      annotations.isNotEmpty() -> null
      type == String::class.java -> StringAdapter
      type == Int::class.javaObjectType || type == Int::class.javaPrimitiveType -> IntAdapter
      type == LiveActivityColor::class.java -> ColorAdapter
      else -> null
    }
}

/** A string; any other value, numbers included, reads as null. */
private object StringAdapter : JsonAdapter<String>() {
  override fun fromJson(reader: JsonReader): String? {
    if (reader.peek() == JsonReader.Token.STRING) return reader.nextString()
    reader.skipValue()
    return null
  }

  override fun toJson(
    writer: JsonWriter,
    value: String?,
  ) {
    writer.value(value)
  }
}

/** An int given as a number or a numeric string; decimals are truncated. */
private object IntAdapter : JsonAdapter<Int>() {
  override fun fromJson(reader: JsonReader): Int? =
    when (val value = reader.readJsonValue()) {
      is Number -> value.toInt()
      is String -> value.toIntOrNull() ?: value.toDoubleOrNull()?.toInt()
      else -> null
    }

  override fun toJson(
    writer: JsonWriter,
    value: Int?,
  ) {
    writer.value(value)
  }
}

/** A color given as a hex string, `{hex}` or a gradient `{fromHex, ...}`, which resolves to its first color. */
private object ColorAdapter : JsonAdapter<LiveActivityColor>() {
  override fun fromJson(reader: JsonReader): LiveActivityColor? {
    val hex =
      when (val value = reader.readJsonValue()) {
        is String -> value
        is Map<*, *> -> (value["hex"] as? String) ?: (value["fromHex"] as? String)
        else -> null
      }
    return hex?.takeIf { it.isNotBlank() }?.let { LiveActivityColor(it) }
  }

  override fun toJson(
    writer: JsonWriter,
    value: LiveActivityColor?,
  ) {
    writer.value(value?.hex)
  }
}

private object EpochMillisAdapter : JsonAdapter<Long>() {
  /** Numbers below this are epoch seconds, the others epoch millis. */
  private const val MIN_EPOCH_MILLIS = 1_000_000_000_000L

  override fun fromJson(reader: JsonReader): Long? =
    when (val value = reader.readJsonValue()) {
      is Number -> value.toLong().toEpochMillis()
      is String -> value.takeIf { it.isNotBlank() }?.let { it.toLongOrNull()?.toEpochMillis() ?: parseIso8601(it) }
      else -> null
    }

  override fun toJson(
    writer: JsonWriter,
    value: Long?,
  ) {
    writer.value(value)
  }

  private fun Long.toEpochMillis(): Long = if (this < MIN_EPOCH_MILLIS) this * 1000L else this

  private fun parseIso8601(value: String): Long? =
    runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
      ?: runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}

private object StatusLabelsAdapter : JsonAdapter<Map<String, String>>() {
  override fun fromJson(reader: JsonReader): Map<String, String> =
    when (val value = reader.readJsonValue()) {
      is List<*> ->
        value
          .mapIndexedNotNull { index, label ->
            val phase = MatchPhase.entries.getOrNull(index) ?: return@mapIndexedNotNull null
            (label as? String)?.takeIf { it.isNotBlank() }?.let { phase.value to it }
          }.toMap()
      is Map<*, *> ->
        value.entries
          .mapNotNull { (phase, label) -> if (phase is String && label is String) phase to label else null }
          .toMap()
      else -> emptyMap()
    }

  override fun toJson(
    writer: JsonWriter,
    value: Map<String, String>?,
  ) {
    if (value == null) {
      writer.nullValue()
      return
    }
    writer.beginObject()
    value.forEach { (phase, label) -> writer.name(phase).value(label) }
    writer.endObject()
  }
}

private object RawJsonObjectAdapter : JsonAdapter<String>() {
  override fun fromJson(reader: JsonReader): String? {
    if (reader.peek() != JsonReader.Token.BEGIN_OBJECT) {
      reader.skipValue()
      return null
    }
    return reader.nextSource().use { it.readUtf8() }
  }

  override fun toJson(
    writer: JsonWriter,
    value: String?,
  ) {
    if (value == null) {
      writer.nullValue()
      return
    }
    writer.valueSink().use { it.writeUtf8(value) }
  }
}
