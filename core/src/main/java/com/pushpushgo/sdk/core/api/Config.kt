package com.pushpushgo.sdk.core.api

class Config(
  val projectId: String,
  val apiKey: String,
  apiUrl: String? = API_URL,
  isDebug: Boolean? = false,
) {
  val apiUrl: String = apiUrl ?: API_URL
  val isDebug: Boolean = isDebug ?: false

  companion object {
    private const val API_URL = "https://api.pushpushgo.com"
    private val PROJECT_ID_REGEX = Regex("^[a-z0-9]{24}$")
    private val API_KEY_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
  }

  init {
    require(PROJECT_ID_REGEX.matches(this.projectId)) {
      "Invalid project ID format! - $projectId"
    }

    require(API_KEY_REGEX.matches(this.apiKey)) {
      "Invalid API key format"
    }
  }
}
