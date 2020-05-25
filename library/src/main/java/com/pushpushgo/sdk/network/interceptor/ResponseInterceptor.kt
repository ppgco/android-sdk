package com.pushpushgo.sdk.network.interceptor

import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.pushpushgo.sdk.exception.PushPushException
import okhttp3.Interceptor
import okhttp3.Response
import java.io.StringReader

internal class ResponseInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) {
            val responseBodyCopy = response.peekBody(java.lang.Long.MAX_VALUE).string()
            val reader = JsonReader(StringReader(responseBodyCopy)).apply {
                isLenient = true
            }
            JsonParser.parseReader(reader).asJsonObject.get("message")?.asString?.let {
                throw PushPushException(it)
            }
        }
        return response
    }
}
