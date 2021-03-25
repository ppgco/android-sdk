package com.pushpushgo.sdk.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.network.data.TokenRequest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber

internal class ApiRepository(
    private val apiService: ApiService,
    private val sharedPref: SharedPreferencesHelper,
    private val projectId: String
) {

    suspend fun registerToken(token: String) {
        Timber.tag(PushPushGo.TAG).d("registerToken invoked: $token")

        val data = apiService.registerSubscriber(projectId, TokenRequest(token))
        if (data.id.isNotBlank()) {
            sharedPref.subscriberId = data.id
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
            beacon = beacon.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        )
    }

    suspend fun sendEvent(event: String) {
        Timber.tag(PushPushGo.TAG).d("sendEvent($event) invoked")

        apiService.sendEvent(
            projectId = projectId,
            event = event.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
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
