package com.pushpushgo.sdk.network

import com.google.gson.GsonBuilder
import com.pushpushgo.sdk.BuildConfig
import com.pushpushgo.sdk.data.Beacon
import com.pushpushgo.sdk.data.Event
import com.pushpushgo.sdk.facade.PushPushGoFacade
import com.pushpushgo.sdk.network.data.ObjectResponse
import com.pushpushgo.sdk.network.data.TokenRequest
import com.readystatesoftware.chuck.ChuckInterceptor
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path

internal interface ApiService {

    @POST("/v1/android/{projectId}/subscriber")
    suspend fun registerSubscriberAsync(
        @Path("projectId") projectId: String, //projectId - id projektu z pushpushGO
        @Body body: TokenRequest //TokenRequest {"token":""}
    ): ObjectResponse

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
    ): ObjectResponse

    @POST("/v1/android/{projectId}/event/")
    suspend fun sendEventAsync(
        @Path("projectId") projectId: String,
        @Body body: Event
    ): ObjectResponse


    companion object {
        operator fun invoke(
            chuckInterceptor: ChuckInterceptor,
            connectivityInstance: ConnectivityInterceptor,
            responseInterceptor: ResponseInterceptor
        ): ApiService {


            val requestInterceptor = Interceptor { chain ->

                val request = chain.request()
                    .newBuilder()
                    .header("Content-Type", "application/json")
                    .header("X-Token", PushPushGoFacade.INSTANCE!!.getApiKey())
                    .build()


                return@Interceptor chain.proceed(request)
            }
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(requestInterceptor)
                .addInterceptor(chuckInterceptor)
                .addInterceptor(connectivityInstance)
                .addInterceptor(responseInterceptor)
                .build()
            val gson = GsonBuilder()
                .create()

            return Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(BuildConfig.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build().create(ApiService::class.java)
        }
    }
}
