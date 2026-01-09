package com.pushpushgo.sdk.push.di

import android.content.Context
import com.pushpushgo.sdk.core.config.Config
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.ApiService
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.network.interceptor.RequestInterceptor
import com.pushpushgo.sdk.push.network.interceptor.ResponseInterceptor
import com.pushpushgo.sdk.push.utils.PlatformType
import com.pushpushgo.sdk.push.utils.getPlatformType
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.provider
import org.kodein.di.singleton
import org.kodein.di.with

internal class NetworkModule(
  context: Context,
  config: Config,
) : DIAware {
  companion object {
    private const val API_KEY = "api_key"
    private const val PROJECT_ID = "project_id"
  }

  override val di by DI.lazy {
    constant(tag = PROJECT_ID) with config.projectId
    constant(tag = API_KEY) with config.apiKey
    bind<PlatformType>() with singleton { getPlatformType() }
    bind<Context>() with provider { context }
    bind<RequestInterceptor>() with singleton { RequestInterceptor() }
    bind<ResponseInterceptor>() with singleton { ResponseInterceptor() }
    bind<SharedPreferencesHelper>() with singleton { SharedPreferencesHelper(instance()) }
    bind<ApiService>() with
      singleton {
        ApiService(
          requestInterceptor = instance(),
          responseInterceptor = instance(),
          platformType = instance(),
          isNetworkDebug = config.isDebug,
          baseUrl = config.apiUrl,
        )
      }
    bind<ApiRepository>() with
      singleton {
        ApiRepository(
          apiService = instance(),
          context = instance(),
          sharedPref = instance(),
          projectId = instance(PROJECT_ID),
          apiKey = instance(API_KEY),
        )
      }
  }

  val sharedPref by instance<SharedPreferencesHelper>()
  val apiRepository by instance<ApiRepository>()
}
