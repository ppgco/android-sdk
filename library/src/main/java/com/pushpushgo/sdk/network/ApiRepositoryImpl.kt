package com.pushpushgo.sdk.network

import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.exception.NoConnectivityException
import com.pushpushgo.sdk.network.data.TokenRequest
import retrofit2.HttpException
import timber.log.Timber
import java.net.ConnectException
import java.net.SocketTimeoutException

internal class ApiRepositoryImpl(private val apiService: ApiService) : ApiRepository {

    override suspend fun unregisterSubscriber(token: String) {
        try {
            apiService.unregisterSubscriberAsync(
                PushPushGo.INSTANCE!!.getProjectId(),
                token
            )
            Timber.tag(PushPushGo.TAG).d("unregisterSubscriberAsync invoked")
            getDefaultSharedPreferences(PushPushGo.INSTANCE!!.getApplication())
                .edit().putString(PushPushGo.SUBSCRIBER_ID, "")
                .apply()
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

    override suspend fun registerToken(token: String) {
        try {
            Timber.tag(PushPushGo.TAG).d("RegisterSubscriberAsync invoked")
            val data = apiService.registerSubscriberAsync(
                PushPushGo.INSTANCE!!.getProjectId(),
                TokenRequest(token)
            )
            if (!data._id.isNullOrBlank()) {
                getDefaultSharedPreferences(PushPushGo.INSTANCE!!.getApplication())
                    .edit().putString(PushPushGo.SUBSCRIBER_ID, data._id)
                    .apply()
                getDefaultSharedPreferences(PushPushGo.INSTANCE!!.getApplication())
                    .edit().putString(PushPushGo.LAST_TOKEN, token)
                    .apply()
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
