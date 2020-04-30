package com.pushpushgo.sdk.di

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.pushpushgo.sdk.network.ApiRepository
import com.pushpushgo.sdk.network.ApiService
import com.pushpushgo.sdk.network.interceptor.ConnectivityInterceptor
import com.pushpushgo.sdk.network.interceptor.RequestInterceptor
import com.pushpushgo.sdk.network.interceptor.ResponseInterceptor
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.provider
import org.kodein.di.generic.singleton

internal class NetworkModule(context: Context, apiKey: String) : KodeinAware {

    override val kodein by Kodein.lazy {
        bind<Context>() with provider { context }
        bind<ChuckerInterceptor>() with singleton { ChuckerInterceptor(instance()) }
        bind<ConnectivityInterceptor>() with singleton { ConnectivityInterceptor(instance()) }
        bind<RequestInterceptor>() with singleton { RequestInterceptor(apiKey) }
        bind<ResponseInterceptor>() with singleton { ResponseInterceptor(instance()) }
        bind() from singleton {
            ApiService(
                instance(),
                instance(),
                instance(),
                instance()
            )
        }
        bind() from singleton {
            ApiRepository(instance())
        }
    }

    val apiRepository by instance<ApiRepository>()
}
