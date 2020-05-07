package com.pushpushgo.sdk.di

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.pushpushgo.sdk.network.ApiRepository
import com.pushpushgo.sdk.network.ApiService
import com.pushpushgo.sdk.network.SharedPreferencesHelper
import com.pushpushgo.sdk.network.interceptor.ConnectivityInterceptor
import com.pushpushgo.sdk.network.interceptor.RequestInterceptor
import com.pushpushgo.sdk.network.interceptor.ResponseInterceptor
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.generic.*

internal class NetworkModule(context: Context, apiKey: String, projectId: String) : KodeinAware {

    companion object {
        const val API_KEY = "api_key"
        const val PROJECT_ID = "project_id"
    }

    override val kodein by Kodein.lazy {
        constant(tag = API_KEY) with apiKey
        constant(tag = PROJECT_ID) with projectId
        bind<Context>() with provider { context }
        bind<ChuckerInterceptor>() with singleton { ChuckerInterceptor(instance()) }
        bind<ConnectivityInterceptor>() with singleton { ConnectivityInterceptor(instance()) }
        bind<RequestInterceptor>() with singleton { RequestInterceptor(instance(API_KEY)) }
        bind<ResponseInterceptor>() with singleton { ResponseInterceptor() }
        bind<SharedPreferencesHelper>() with singleton { SharedPreferencesHelper(instance()) }
        bind() from singleton {
            ApiService(
                instance(),
                instance(),
                instance(),
                instance()
            )
        }
        bind() from singleton { ApiRepository(kodein) }
    }

    val sharedPref by instance<SharedPreferencesHelper>()

    val apiRepository by instance<ApiRepository>()
}
