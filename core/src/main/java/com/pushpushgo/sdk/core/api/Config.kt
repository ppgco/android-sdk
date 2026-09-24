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

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Config) return false

    return projectId == other.projectId &&
      apiKey == other.apiKey &&
      apiUrl == other.apiUrl &&
      isDebug == other.isDebug
  }

  override fun hashCode(): Int {
    var result = projectId.hashCode()
    result = 31 * result + apiKey.hashCode()
    result = 31 * result + apiUrl.hashCode()
    result = 31 * result + isDebug.hashCode()
    return result
  }
}
