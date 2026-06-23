package com.pushpushgo.sdk.push.network.interceptor

import android.util.JsonReader
import com.pushpushgo.sdk.push.exception.PushPushException
import com.pushpushgo.sdk.push.utils.logError
import okhttp3.Interceptor
import okhttp3.Response
import java.io.StringReader

internal class ResponseInterceptor : Interceptor {
  companion object {
    // Cap the buffered copy of an error body. A misbehaving/hostile endpoint
    // could otherwise stream an arbitrarily large body and OOM the process via
    // peekBody(Long.MAX_VALUE).
    private const val MAX_ERROR_BODY_BYTES = 64L * 1024
  }

  override fun intercept(chain: Interceptor.Chain): Response {
    val response = chain.proceed(chain.request())
    if (response.isSuccessful) return response

    val message = parseErrorMessage(response.peekBody(MAX_ERROR_BODY_BYTES).string())
    if (message != null) {
      throw PushPushException(message)
    }
    return response
  }

  /** Extracts the top-level `message` field from a JSON error body, if present. */
  private fun parseErrorMessage(body: String): String? =
    try {
      JsonReader(StringReader(body)).use { reader ->
        reader.isLenient = true
        reader.beginObject()
        if (reader.nextName() == "message") reader.nextString() else null
      }
    } catch (e: Exception) {
      // Body wasn't the expected JSON error envelope (e.g. HTML/plain text, or
      // malformed JSON which throws IOException); fall through and let the caller
      // see the raw HTTP failure instead of masking it with a parse error.
      logError(e)
      null
    }
}
