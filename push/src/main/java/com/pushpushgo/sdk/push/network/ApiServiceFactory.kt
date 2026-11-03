package com.pushpushgo.sdk.push.network

import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.push.network.interceptor.RequestInterceptor
import com.pushpushgo.sdk.push.network.interceptor.ResponseInterceptor
import com.pushpushgo.sdk.push.utils.getPlatformType
import com.pushpushgo.sdk.push.utils.logDebug
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create

internal fun interface ApiServiceFactory {
  fun create(config: Config): ApiService
}

internal object DefaultApiServiceFactory : ApiServiceFactory {
  override fun create(config: Config): ApiService {
    val moshi = Moshi.Builder().build()

    val client =
      OkHttpClient
        .Builder()
        .addInterceptor(RequestInterceptor())
        .addInterceptor(ResponseInterceptor(moshi))
        .addNetworkInterceptor(
          HttpLoggingInterceptor {
            logDebug(it)
          }.setLevel(
            if (config.isDebug) {
              HttpLoggingInterceptor.Level.BODY
            } else {
              HttpLoggingInterceptor.Level.BASIC
            },
          ),
        ).build()

    val platformType = getPlatformType()

    return Retrofit
      .Builder()
      .client(client)
      .baseUrl("${config.apiUrl}/v1/${platformType.apiName}/")
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create()
  }
}
