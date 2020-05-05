package com.pushpushgo.sdk.network

import com.google.firebase.iid.FirebaseInstanceId
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.data.Event
import com.pushpushgo.sdk.data.EventType
import com.pushpushgo.sdk.data.Payload
import com.pushpushgo.sdk.di.NetworkModule.Companion.PROJECT_ID
import com.pushpushgo.sdk.exception.NoConnectivityException
import com.pushpushgo.sdk.network.data.TokenRequest
import com.pushpushgo.sdk.utils.deviceToken
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

    suspend fun registerToken() {
        Timber.tag(PushPushGo.TAG).d("registerToken invoked")

        try {
            val token = sharedPref.lastToken.takeIf { it.isNotEmpty() } ?: FirebaseInstanceId.getInstance().deviceToken

            val data = apiService.registerSubscriber(projectId, TokenRequest(token))
            if (!data._id.isNullOrBlank()) {
                sharedPref.subscriberId = data._id
                sharedPref.lastToken = token
                sharedPref.isSubscribed = true
            }
            Timber.tag(PushPushGo.TAG).d("RegisterSubscriber received: $data")
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

    suspend fun unregisterSubscriber(isSubscribed: Boolean = false) {
        Timber.tag(PushPushGo.TAG).d("unregisterSubscriber($isSubscribed) invoked")

        try {
            apiService.unregisterSubscriber(projectId, sharedPref.subscriberId)
            sharedPref.subscriberId = ""
            sharedPref.isSubscribed = isSubscribed
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

    suspend fun sendEvent(type: EventType, buttonId: Int, campaign: String) {
        Timber.tag(PushPushGo.TAG).d("sendEvent(${type.value}, $buttonId, $campaign) invoked")

        try {
            apiService.sendEvent(projectId, Event(
                type = type.value,
                payload = Payload(
                    button = buttonId,
                    campaign = campaign,
                    subscriber = sharedPref.subscriberId
                )
            ))
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
