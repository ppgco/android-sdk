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
            try {
                val reader = JsonReader(StringReader(responseBodyCopy)).apply {
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
        return response
    }
}
