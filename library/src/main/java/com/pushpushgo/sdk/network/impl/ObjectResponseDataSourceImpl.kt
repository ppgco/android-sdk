package com.pushpushgo.sdk.network.impl

import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.pushpushgo.sdk.exception.NoConnectivityException
import com.pushpushgo.sdk.facade.PushPushGoFacade
import com.pushpushgo.sdk.network.ApiService
import com.pushpushgo.sdk.network.ObjectResponseDataSource
import com.pushpushgo.sdk.network.data.TokenRequest
import retrofit2.HttpException
import timber.log.Timber
import java.net.ConnectException
import java.net.SocketTimeoutException

internal class ObjectResponseDataSourceImpl(private val apiService: ApiService) : ObjectResponseDataSource {

    override suspend fun unregisterSubscriber(token: String) {
        try {
            apiService.unregisterSubscriberAsync(
                PushPushGoFacade.INSTANCE!!.getProjectId(),
                token
            ).await()
            Timber.tag(PushPushGoFacade.TAG).d("unregisterSubscriberAsync invoked")
            getDefaultSharedPreferences(PushPushGoFacade.INSTANCE!!.getApplication())
                .edit().putString(PushPushGoFacade.SUBSCRIBER_ID, "")
                .apply()
        } catch (e: NoConnectivityException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: ConnectException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: SocketTimeoutException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: HttpException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection forbidden %s", e.message)

        } catch (e: Exception) {
            Timber.tag(PushPushGoFacade.TAG).e("Unknown exception %s", e.message)
        }
    }

    override suspend fun registerToken(token: String) {
        try {
            Timber.tag(PushPushGoFacade.TAG).d("RegisterSubscriberAsync invoked")
            val data = apiService.registerSubscriberAsync(
                PushPushGoFacade.INSTANCE!!.getProjectId(),
                TokenRequest(token)
            ).await()
            if (!data._id.isNullOrBlank()) {
                getDefaultSharedPreferences(PushPushGoFacade.INSTANCE!!.getApplication())
                    .edit().putString(PushPushGoFacade.SUBSCRIBER_ID, data._id)
                    .apply()
                getDefaultSharedPreferences(PushPushGoFacade.INSTANCE!!.getApplication())
                    .edit().putString(PushPushGoFacade.LAST_TOKEN, token)
                    .apply()
            }
            Timber.tag(PushPushGoFacade.TAG).d("RegisterSubscriberAsync received: $data")
        } catch (e: NoConnectivityException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: ConnectException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: SocketTimeoutException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection error %s", e.message)
        } catch (e: HttpException) {
            Timber.tag(PushPushGoFacade.TAG).e("Connection forbidden %s", e.message)

        } catch (e: Exception) {
            Timber.tag(PushPushGoFacade.TAG).e("Unknown exception %s", e.message)
        }
    }
}
