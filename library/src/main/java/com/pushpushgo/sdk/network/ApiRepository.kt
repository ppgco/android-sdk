package com.pushpushgo.sdk.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.network.data.TokenRequest
import com.pushpushgo.sdk.utils.getPlatformPushToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException

internal class ApiRepository(
    private val apiService: ApiService,
    private val context: Context,
    private val sharedPref: SharedPreferencesHelper,
    private val projectId: String,
    private val apiKey: String,
) {

    suspend fun registerToken(token: String?) {
        Timber.tag(PushPushGo.TAG).d("registerToken invoked: $token")
        val tokenToRegister = token ?: sharedPref.lastToken.takeIf { it.isNotEmpty() } ?: withContext(Dispatchers.IO) {
            getPlatformPushToken(context)
        }
        Timber.d("Token to register: $tokenToRegister")

        try {
            val data = apiService.registerSubscriber(apiKey, projectId, TokenRequest(tokenToRegister))
            if (data.id.isNotBlank()) {
                sharedPref.subscriberId = data.id
                sharedPref.isSubscribed = true
            }
            Timber.tag(PushPushGo.TAG).d("RegisterSubscriber received: $data")
        }
        catch (ex: IOException)
        {
            Timber.tag(PushPushGo.TAG).w(ex, "RegisterSubscriber exception")
        }
    }

    suspend fun unregisterSubscriber(isSubscribed: Boolean = false) {
        Timber.tag(PushPushGo.TAG).d("unregisterSubscriber($isSubscribed) invoked")
        try {
            apiService.unregisterSubscriber(
                token = apiKey,
                projectId = projectId,
                subscriberId = sharedPref.subscriberId,
            )

        }
        catch (ex: IOException)
        {
            Timber.tag(PushPushGo.TAG).w(ex, "UnregisterSubscriber exception")
        }

        sharedPref.subscriberId = ""
        sharedPref.isSubscribed = false
    }

    suspend fun migrateSubscriber(oldProjectId: String?, oldToken: String?, oldSubscriberId: String?) {
        Timber.tag(PushPushGo.TAG).d("migrateSubscriber($oldProjectId, $oldToken, $oldSubscriberId) invoked")

        if (oldProjectId.isNullOrBlank() || oldToken.isNullOrBlank() || oldSubscriberId.isNullOrBlank()) {
            return Timber.tag(PushPushGo.TAG).i("Empty old project info!")
        }

        // unregister previous
        try {
            apiService.unregisterSubscriber(
                token = oldToken,
                projectId = oldProjectId,
                subscriberId = oldSubscriberId,
            )
        } catch (e: PushPushException) {
            if (!e.message.orEmpty().contains("Subscriber not found")) {
                throw e
            }
        }

        // register new
        registerToken(null)
    }

    suspend fun sendBeacon(beacon: String) {
        Timber.tag(PushPushGo.TAG).d("sendBeacon($beacon) invoked")

        apiService.sendBeacon(
            token = apiKey,
            projectId = projectId,
            subscriberId = sharedPref.subscriberId,
            beacon = beacon.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        )
    }

    suspend fun sendEvent(event: String) {
        Timber.tag(PushPushGo.TAG).d("sendEvent($event) invoked")

        apiService.sendEvent(
            token = apiKey,
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
