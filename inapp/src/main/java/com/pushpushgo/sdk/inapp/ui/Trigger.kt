package com.pushpushgo.sdk.inapp.ui

class Trigger private constructor(
  val key: String,
  val value: String? = null,
) {
  init {
    require(key.isNotBlank()) {
      "Trigger key must not be blank"
    }

    require(value?.isNotBlank() ?: true) {
      "Trigger value must not be blank"
    }
  }

  companion object {
    /**
     * Creates a key-only trigger.
     *
     * @param key Non-blank trigger identifier.
     *
     * @throws IllegalArgumentException if key is blank.
     */
    fun key(key: String) = Trigger(key)

    /**
     * Creates a key–value trigger.
     *
     * @param key Non-blank trigger identifier.
     * @param value Non-blank trigger value.
     *
     * @throws IllegalArgumentException if key or value is blank.
     */
    fun keyValue(
      key: String,
      value: String,
    ) = Trigger(key, value)
  }
}
