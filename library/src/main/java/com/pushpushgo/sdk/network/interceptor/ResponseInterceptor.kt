package com.pushpushgo.sdk.network.interceptor

import com.google.gson.JsonParser
import com.pushpushgo.sdk.exception.PushPushException
import okhttp3.Interceptor
import okhttp3.Response

internal class ResponseInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isSuccessful) {
            val responseBodyCopy = response.peekBody(java.lang.Long.MAX_VALUE).string()
            JsonParser.parseString(responseBodyCopy).asJsonObject.get("message")?.asString?.let {
                throw PushPushException(it)
            }
        }
        return response
    }
}
