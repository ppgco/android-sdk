package com.pushpushgo.sdk.network.impl

import com.pushpushgo.sdk.BuildConfig
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.exception.NoConnectivityException
import com.pushpushgo.sdk.facade.PushPushGoFacade
import com.pushpushgo.sdk.network.ApiService
import com.pushpushgo.sdk.network.ObjectResponseDataSource
import com.pushpushgo.sdk.utils.Helper
import retrofit2.HttpException
import timber.log.Timber
import java.lang.Exception
import java.net.ConnectException
import java.net.SocketTimeoutException

internal class ObjectResponseDataSourceImpl(
    private val apiService: ApiService
) : ObjectResponseDataSource {
    override suspend fun unregisterSubscriber(apiKey: String,token: String) {
        try {
            apiService.unregisterSubscriberAsync(
                apiKey,
                token).await()
            Timber.tag(PushPushGoFacade.TAG).d("unregisterSubscriberAsync invoked")

        } catch (e: NoConnectivityException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: ConnectException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: SocketTimeoutException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: HttpException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection forbidden %s", e.message)

        }catch (e: Exception){
            Timber.tag(PushPushGoFacade.TAG).e("Unknown exception %s", e.message)
        }
    }

    override suspend fun registerApiKey(apiKey: String) {
        try {
            val model = Helper.getDeviceName()
            val type = "android"
            val version = BuildConfig.VERSION_NAME
            apiService.registerSubscriberAsync(
                apiKey,
                version).await()
            Timber.tag(PushPushGoFacade.TAG).d("RegisterSubscriberAsync invoked")

        } catch (e: NoConnectivityException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: ConnectException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: SocketTimeoutException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: HttpException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection forbidden %s", e.message)

        }catch (e: Exception){
            Timber.tag(PushPushGoFacade.TAG).e("Unknown exception %s", e.message)
        }
    }

    override suspend fun sendToken(apiKey: String, token: String) {
        try {
            val model = Helper.getDeviceName()
            val type = "android"
            val version = BuildConfig.VERSION_NAME

            apiService.sendBeaconAsync(
                apiKey,
                version,
                token).await()

            Timber.tag(PushPushGoFacade.TAG).d("sendBeaconAsync invoked")

        } catch (e: NoConnectivityException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: ConnectException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: SocketTimeoutException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: HttpException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection forbidden %s", e.message)

        }catch (e: Exception){
            Timber.tag(PushPushGoFacade.TAG).e("Unknown exception %s", e.message)
        }

    }
}