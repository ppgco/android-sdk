package com.pushpushgo.sdk.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.firebase.iid.FirebaseInstanceId
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.network.data.TokenRequest
import com.pushpushgo.sdk.utils.deviceToken
import okhttp3.MediaType
import okhttp3.RequestBody
import timber.log.Timber

internal class ApiRepository(
    private val apiService: ApiService,
    private val sharedPref: SharedPreferencesHelper,
    private val projectId: String
) {

    suspend fun registerToken() {
        Timber.tag(PushPushGo.TAG).d("registerToken invoked")

        val token = sharedPref.lastToken.takeIf { it.isNotEmpty() } ?: FirebaseInstanceId.getInstance().deviceToken

        val data = apiService.registerSubscriber(projectId, TokenRequest(token))
        if (data.id.isNotBlank()) {
            sharedPref.subscriberId = data.id
            sharedPref.lastToken = token
            sharedPref.isSubscribed = true
        }
        Timber.tag(PushPushGo.TAG).d("RegisterSubscriber received: $data")
    }

    suspend fun unregisterSubscriber(isSubscribed: Boolean = false) {
        Timber.tag(PushPushGo.TAG).d("unregisterSubscriber($isSubscribed) invoked")

        apiService.unregisterSubscriber(projectId, sharedPref.subscriberId)
        sharedPref.subscriberId = ""
        sharedPref.isSubscribed = isSubscribed
    }

    suspend fun sendBeacon(beacon: String) {
        Timber.tag(PushPushGo.TAG).d("sendBeacon($beacon) invoked")

        apiService.sendBeacon(
            projectId = projectId,
            subscriberId = sharedPref.subscriberId,
            beacon = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), beacon)
        )
    }

    suspend fun sendEvent(event: String) {
        Timber.tag(PushPushGo.TAG).d("sendEvent($event) invoked")

        apiService.sendEvent(
            projectId = projectId,
            event = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), event)
        )
    }

    suspend fun getBitmapFromUrl(url: String?): Bitmap? {
        Timber.tag(PushPushGo.TAG).d("getDrawable($url) invoked")

        if (url.isNullOrBlank()) return null

        return BitmapFactory.decodeStream(
            apiService.getRawResponse(url).byteStream()
        )
    }
}
