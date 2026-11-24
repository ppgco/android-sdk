package com.pushpushgo.sdk.inapp.network

import com.pushpushgo.sdk.inapp.utils.ZonedDateTimeAdapter
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

internal object RetrofitProvider {
  private const val API_KEY_HEADER = "X-Token"

  fun buildRetrofit(
    baseUrl: String,
    isDebug: Boolean,
  ): Retrofit {
    val okHttpClient =
      OkHttpClient
        .Builder()
        .addInterceptor(
          HttpLoggingInterceptor().apply {
            level = if (isDebug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
            redactHeader(API_KEY_HEADER)
          },
        ).build()

    val moshi =
      Moshi
        .Builder()
        .add(ZonedDateTimeAdapter.FACTORY)
        .build()

    return Retrofit
      .Builder()
      .baseUrl(baseUrl)
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
  }
}
