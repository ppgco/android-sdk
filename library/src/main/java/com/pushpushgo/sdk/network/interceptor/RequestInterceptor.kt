package com.pushpushgo.sdk.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

internal class RequestInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(
            chain.request()
                .newBuilder()
                .header("Content-Type", "application/json")
                .build()
        )
    }
}
