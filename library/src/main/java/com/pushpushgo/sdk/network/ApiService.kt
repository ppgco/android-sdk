package com.pushpushgo.sdk.network

import android.content.res.Resources
import android.os.Build
import com.google.gson.GsonBuilder
import com.jakewharton.retrofit2.adapter.kotlin.coroutines.CoroutineCallAdapterFactory
import com.pushpushgo.sdk.BuildConfig
import com.pushpushgo.sdk.network.data.ObjectResponse
import com.readystatesoftware.chuck.ChuckInterceptor
import kotlinx.coroutines.Deferred
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.*

internal interface ApiService {

    @FormUrlEncoded
    @POST("/v1/android/{projectId}/subscriber")
    fun registerSubscriberAsync(
        @Path("projectId") projectId: String,
        @Field("version") version: String
    ): Deferred<ObjectResponse>

    @FormUrlEncoded
    @DELETE("/v1/android/{projectId}/subscriber/{subscriberId}")
    fun unregisterSubscriberAsync(
        @Path("projectId") projectId: String,
        @Field("subscriberId") version: String
    ): Deferred<ObjectResponse>

    @FormUrlEncoded
    @POST("/v1/android/{projectId}/subscriber/{subscriberId}/beacon")
    fun sendBeaconAsync(
        @Path("projectId") projectId: String,
        @Path("subscriberId") version: String,
        @Field("token") token: String
    ): Deferred<ObjectResponse>


    companion object {
        operator fun invoke(
            chuckInterceptor: ChuckInterceptor,
            connectivityInstance: ConnectivityInterceptor,
            responseInterceptor: ResponseInterceptor
        ): ApiService {
            val locale: Locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Resources.getSystem().configuration.locales.get(0)
            } else {

                Resources.getSystem().configuration.locale
            }

            val requestInterceptor = Interceptor { chain ->

                val request = chain.request()
                    .newBuilder()
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
                .addCallAdapterFactory(CoroutineCallAdapterFactory())
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build().create(ApiService::class.java)
        }
    }
}