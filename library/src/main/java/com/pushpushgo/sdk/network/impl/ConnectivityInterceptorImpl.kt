package com.pushpushgo.sdk.network.impl

import android.content.Context
import android.net.ConnectivityManager
import okhttp3.Interceptor
import okhttp3.Response
import com.pushpushgo.sdk.network.ConnectivityInterceptor
import com.pushpushgo.sdk.exception.NoConnectivityException

internal class ConnectivityInterceptorImpl(context: Context) : ConnectivityInterceptor {

    private val appContext = context.applicationContext

    override fun intercept(chain: Interceptor.Chain): Response {

            if (!isOnline())
                throw NoConnectivityException("You are offline")

            return chain.proceed(chain.request())
    }
    private fun isOnline(): Boolean{
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo

        return networkInfo != null && networkInfo.isConnected
    }
}