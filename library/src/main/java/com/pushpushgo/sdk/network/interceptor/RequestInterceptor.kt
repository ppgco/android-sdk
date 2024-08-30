package com.pushpushgo.sdk.network.interceptor
import java.util.Locale
import okhttp3.Interceptor
import okhttp3.Response

internal class RequestInterceptor : Interceptor {

    private val defaultLocale = Locale.getDefault()
    private val languageCode = defaultLocale.language // e.g., "en", "fr", "es"
    private val countryCode = defaultLocale.country // e.g., "US", "FR", "ES"

    private val acceptLanguage = if (countryCode.isNotEmpty()) {
        "$languageCode-$countryCode"
    } else {
        languageCode
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        return chain.proceed(
            chain.request()
                .newBuilder()
                .header("Content-Type", "application/json")
                .header("Accept-Language", acceptLanguage)
                .build()
        )
    }
}
