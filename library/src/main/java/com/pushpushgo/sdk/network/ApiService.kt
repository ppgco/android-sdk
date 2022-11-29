package com.pushpushgo.sdk.network

import com.pushpushgo.sdk.network.data.ProjectTag
import com.pushpushgo.sdk.network.interceptor.RequestInterceptor
import com.pushpushgo.sdk.network.interceptor.ResponseInterceptor
import com.pushpushgo.sdk.utils.PlatformType
import com.pushpushgo.sdk.utils.logDebug
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Url

internal interface ApiService {

    @POST("project/{projectId}/tag/search/")
    suspend fun getAllTags(
        @Header("X-Token") token: String,
        @Path("projectId") projectId: String
    ): List<ProjectTag>

    companion object {
        operator fun invoke(
            requestInterceptor: RequestInterceptor,
            responseInterceptor: ResponseInterceptor,
            baseUrl: String,
            isNetworkDebug: Boolean,
        ): ApiService {
            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor(requestInterceptor)
                .addInterceptor(responseInterceptor)
                .addNetworkInterceptor(
                    HttpLoggingInterceptor {
                        logDebug(it)
                    }.setLevel(
                        if (isNetworkDebug) HttpLoggingInterceptor.Level.BODY
                        else HttpLoggingInterceptor.Level.BASIC
                    )
                )
                .build()

            return Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(baseUrl)
                .addConverterFactory(MoshiConverterFactory.create())
                .build().create()
        }
    }
}
