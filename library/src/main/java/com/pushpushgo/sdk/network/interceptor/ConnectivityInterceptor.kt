package com.pushpushgo.sdk.network.interceptor

import android.content.Context
import android.net.ConnectivityManager
import com.pushpushgo.sdk.exception.NoConnectivityException
import com.pushpushgo.sdk.facade.PushPushGoFacade
import okhttp3.Interceptor
import okhttp3.Response
import timber.log.Timber

internal class ConnectivityInterceptor(context: Context) : Interceptor {

    private val appContext = context.applicationContext

    override fun intercept(chain: Interceptor.Chain): Response {
        Timber.tag(PushPushGoFacade.TAG).d("address: ${chain.request().url()}")

        if (!isOnline()) throw NoConnectivityException("You are offline")

        return chain.proceed(chain.request())
    }

    private fun isOnline(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo

        return networkInfo != null && networkInfo.isConnected
    }
}
