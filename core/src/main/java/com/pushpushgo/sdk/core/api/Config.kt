package com.pushpushgo.sdk.core.api

class Config private constructor(
  val projectId: String,
  val apiKey: String,
  val apiUrl: String,
  val isDebug: Boolean,
) {
  companion object {
    private const val API_URL = "https://api.pushpushgo.com"
    private val PROJECT_ID_REGEX = Regex("^[a-z0-9]{24}$")
    private val API_KEY_REGEX = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

    @JvmStatic
    fun isProjectIdFormatValid(projectId: String): Boolean = PROJECT_ID_REGEX.matches(projectId)

    @JvmStatic
    fun isApiKeyFormatValid(apiKey: String): Boolean = API_KEY_REGEX.matches(apiKey)

    @JvmStatic
    @JvmOverloads
    fun create(
      projectId: String,
      apiKey: String,
      apiUrl: String? = API_URL,
      isDebug: Boolean? = false,
    ): Config = Config(projectId, apiKey, apiUrl ?: API_URL, isDebug ?: false)
  }

  init {
    require(isProjectIdFormatValid(projectId)) {
      "Invalid project ID format! - $projectId"
    }

    require(isApiKeyFormatValid(apiKey)) {
      "Invalid API key format"
    }
  }
}
