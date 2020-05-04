package com.pushpushgo.sdk.network

import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.google.gson.GsonBuilder
import com.pushpushgo.sdk.BuildConfig
import com.pushpushgo.sdk.data.Beacon
import com.pushpushgo.sdk.data.Event
import com.pushpushgo.sdk.network.data.ApiResponse
import com.pushpushgo.sdk.network.data.TokenRequest
import com.pushpushgo.sdk.network.interceptor.ConnectivityInterceptor
import com.pushpushgo.sdk.network.interceptor.RequestInterceptor
import com.pushpushgo.sdk.network.interceptor.ResponseInterceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.create
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

internal interface ApiService {

    @POST("/v1/android/{projectId}/subscriber")
    suspend fun registerSubscriberAsync(
        @Path("projectId") projectId: String, //projectId - id projektu z pushpushGO
        @Body body: TokenRequest //TokenRequest {"token":""}
    ): ApiResponse

    @DELETE("/v1/android/{projectId}/subscriber/{subscriberId}") //gdy chcemy sie wyrejestrowac z pushy
    suspend fun unregisterSubscriberAsync(
        @Path("projectId") projectId: String,
        @Path("subscriberId") subscriberId: String
    ): Response<Void>

    @POST("/v1/android/{projectId}/subscriber/{subscriberId}/beacon")
    suspend fun sendBeaconAsync(
        @Path("projectId") projectId: String,
        @Path("subscriberId") version: String,
        @Body body: Beacon
    ): ApiResponse

    @POST("/v1/android/{projectId}/event/")
    suspend fun sendEventAsync(
        @Path("projectId") projectId: String,
        @Body body: Event
    ): Response<Void>

    companion object {
        operator fun invoke(
            chuckerInterceptor: ChuckerInterceptor,
            connectivityInstance: ConnectivityInterceptor,
            requestInterceptor: RequestInterceptor,
            responseInterceptor: ResponseInterceptor
        ): ApiService {
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(requestInterceptor)
                .addInterceptor(chuckerInterceptor)
                .addInterceptor(connectivityInstance)
                .addInterceptor(responseInterceptor)
                .addNetworkInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .build()
            val gson = GsonBuilder()
                .create()

            return Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(BuildConfig.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build().create()
        }
    }
}
