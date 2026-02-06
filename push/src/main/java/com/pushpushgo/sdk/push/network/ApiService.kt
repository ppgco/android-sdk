package com.pushpushgo.sdk.push.network

import com.pushpushgo.sdk.core.config.Config
import com.pushpushgo.sdk.push.data.Event
import com.pushpushgo.sdk.push.network.data.TokenRequest
import com.pushpushgo.sdk.push.network.data.TokenResponse
import com.pushpushgo.sdk.push.network.interceptor.RequestInterceptor
import com.pushpushgo.sdk.push.network.interceptor.ResponseInterceptor
import com.pushpushgo.sdk.push.utils.getPlatformType
import com.pushpushgo.sdk.push.utils.logDebug
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url

internal interface ApiService {
  @POST("{projectId}/subscriber")
  suspend fun registerSubscriber(
    @Header("X-Token") token: String,
    @Path("projectId") projectId: String,
    @Body body: TokenRequest,
  ): TokenResponse

  @DELETE("{projectId}/subscriber/{subscriberId}")
  suspend fun unregisterSubscriber(
    @Header("X-Token") token: String,
    @Path("projectId") projectId: String,
    @Path("subscriberId") subscriberId: String,
  ): Response<Void>

  @POST("{projectId}/subscriber/{subscriberId}/beacon")
  suspend fun sendBeacon(
    @Header("X-Token") token: String,
    @Path("projectId") projectId: String,
    @Path("subscriberId") subscriberId: String,
    @Body beacon: RequestBody,
  ): Response<Void>

  @POST("{projectId}/event/")
  suspend fun sendEvent(
    @Header("X-Token") token: String,
    @Path("projectId") projectId: String,
    @Body event: Event,
  ): Response<Void>

  @GET
  suspend fun getRawResponse(
    @Url url: String,
  ): ResponseBody

  companion object {
    fun fromConfig(config: Config): ApiService {
      val client =
        OkHttpClient
          .Builder()
          .addInterceptor(RequestInterceptor())
          .addInterceptor(ResponseInterceptor())
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
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create()
    }
  }
}
