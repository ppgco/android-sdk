package com.pushpushgo.sdk.network

import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.di.NetworkModule.Companion.PROJECT_ID
import com.pushpushgo.sdk.exception.NoConnectivityException
import com.pushpushgo.sdk.network.data.TokenRequest
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.generic.instance
import retrofit2.HttpException
import timber.log.Timber
import java.net.ConnectException
import java.net.SocketTimeoutException

internal class ApiRepository(override val kodein: Kodein) : KodeinAware {

    private val apiService by instance<ApiService>()

    private val sharedPref by instance<SharedPreferencesHelper>()

    private val projectId by instance<String>(PROJECT_ID)

    suspend fun unregisterSubscriber() {
        try {
            Timber.tag(PushPushGo.TAG).d("unregisterSubscriberAsync invoked")
            apiService.unregisterSubscriberAsync(projectId, sharedPref.subscriberId)
            sharedPref.subscriberId = ""
        } catch (e: NoConnectivityException) {
            Timber.tag(PushPushGo.TAG).e("Connection error %s", e.message)
        } catch (e: ConnectException) {
            Timber.tag(PushPushGo.TAG).e("Connection error %s", e.message)
        } catch (e: SocketTimeoutException) {
            Timber.tag(PushPushGo.TAG).e("Connection error %s", e.message)
        } catch (e: HttpException) {
            Timber.tag(PushPushGo.TAG).e("Connection forbidden %s", e.message)
        } catch (e: Exception) {
            Timber.tag(PushPushGo.TAG).e("Unknown exception %s", e.message)
        }
    }

    suspend fun registerToken(token: String) {
        try {
            Timber.tag(PushPushGo.TAG).d("RegisterSubscriberAsync invoked")
            val data = apiService.registerSubscriberAsync(projectId, TokenRequest(token))
            if (!data._id.isNullOrBlank()) {
                sharedPref.subscriberId = data._id
                sharedPref.lastToken = token
            }
            Timber.tag(PushPushGo.TAG).d("RegisterSubscriberAsync received: $data")
        } catch (e: NoConnectivityException) {
            Timber.tag(PushPushGo.TAG).e("Connection error %s", e.message)
        } catch (e: ConnectException) {
            Timber.tag(PushPushGo.TAG).e("Connection error %s", e.message)
        } catch (e: SocketTimeoutException) {
            Timber.tag(PushPushGo.TAG).e("Connection error %s", e.message)
        } catch (e: HttpException) {
            Timber.tag(PushPushGo.TAG).e("Connection forbidden %s", e.message)
        } catch (e: Exception) {
            Timber.tag(PushPushGo.TAG).e("Unknown exception %s", e.message)
        }
    }
}
