package com.pushpushgo.sdk.network.interceptor

import android.content.Context
import android.net.ConnectivityManager
import com.pushpushgo.sdk.exception.NoConnectivityException
import okhttp3.Interceptor
import okhttp3.Response

internal class ConnectivityInterceptor(private val context: Context) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!isOnline()) throw NoConnectivityException("You are offline")

        return chain.proceed(chain.request())
    }

    private fun isOnline(): Boolean {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo

        return networkInfo != null && networkInfo.isConnected
    }
}
