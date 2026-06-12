package com.pushpushgo.sdk.network.interceptor

import android.util.JsonReader
import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.utils.logError
import okhttp3.Interceptor
import okhttp3.Response
import java.io.StringReader

internal class ResponseInterceptor : Interceptor {
  override fun intercept(chain: Interceptor.Chain): Response {
    val response = chain.proceed(chain.request())
    if (!response.isSuccessful) {
      val responseBodyCopy = response.peekBody(java.lang.Long.MAX_VALUE).string()
      // Only attempt to extract a `message` when the body is actually a JSON
      // object; error responses can be HTML (e.g. a gateway 404), which would
      // otherwise spam misleading JSON-parsing exceptions.
      if (responseBodyCopy.trimStart().startsWith("{")) {
        try {
          val reader =
            JsonReader(StringReader(responseBodyCopy)).apply {
              isLenient = true
            }
          reader.beginObject()
          if (reader.nextName() == "message") {
            throw PushPushException(reader.nextString())
          }
        } catch (e: RuntimeException) {
          logError(e)
        }
      }
    }
    return response
  }
}
