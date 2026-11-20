package com.pushpushgo.sdk.push.network.interceptor

import com.pushpushgo.sdk.push.exception.PushPushException
import com.pushpushgo.sdk.push.network.data.ErrorResponse
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.Response

internal class ResponseInterceptor(
  moshi: Moshi,
) : Interceptor {
  private val errorAdapter = moshi.adapter(ErrorResponse::class.java)

  override fun intercept(chain: Interceptor.Chain): Response {
    val response = chain.proceed(chain.request())
    if (response.isSuccessful) return response

    val responseCode = response.code
    val errorResponse =
      runCatching {
        response.body?.source()?.let(errorAdapter::fromJson)
      }.getOrNull()

    response.close()

    val message = errorResponse?.message?.takeIf(String::isNotBlank)

    throw PushPushException(message ?: "HTTP $responseCode", responseCode)
  }
}
