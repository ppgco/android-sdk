package com.pushpushgo.sdk.network.impl

import com.pushpushgo.sdk.BuildConfig
import com.pushpushgo.sdk.exception.NoConnectivityException
import com.pushpushgo.sdk.network.ApiService
import com.pushpushgo.sdk.network.ObjectResponseDataSource
import com.pushpushgo.sdk.utils.Helper
import okhttp3.MediaType
import okhttp3.RequestBody
import retrofit2.HttpException
import timber.log.Timber
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.*

class ObjectResponseDataSourceImpl(
    private val apiService: ApiService
) : ObjectResponseDataSource {
    override suspend fun registerApiKey(apiKey: String) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override suspend fun sendToken(apiKey: String, token: String) {
        try {
            val model = Helper.getDeviceName()
            val type = "android"
            val version = BuildConfig.VERSION_NAME

            apiService.sendTokenAsync(
                RequestBody.create(MediaType.parse("multipart/form-data"),model!!),
                RequestBody.create(MediaType.parse("multipart/form-data"),type),
                RequestBody.create(MediaType.parse("multipart/form-data"),version),
                RequestBody.create(MediaType.parse("multipart/form-data"),apiKey),
                RequestBody.create(MediaType.parse("multipart/form-data"),token)).await()

        } catch (e: NoConnectivityException) {
            Timber.e("Connection error %s", e.message)
        } catch (e: ConnectException) {
            Timber.e("Connection error %s", e.message)
        } catch (e: SocketTimeoutException) {
            Timber.e("Connection error %s", e.message)
        } catch (e: HttpException) {
            Timber.e("Connection forbidden %s", e.message)

        }

    }
}