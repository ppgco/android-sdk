package com.pushpushgo.sdk.bridge

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pushpushgo.sdk.PushPushGo

/**
 * Bridge interface that allows other components (in-app messages library)
 * to request push notification subscription without creating direct dependencies.
 */
interface PushSubscriptionBridgeManager {
    /**
     * Request user to subscribe to push notifications
     * 
     * @param context Android context
     * @return true if the subscription request was successfully initiated
     */
    fun requestSubscription(context: Context): Boolean
}

class PushPushGoSubscriptionBridgeManager : PushSubscriptionBridgeManager {
    companion object {
        private const val TAG = "PushSubscriptionManager"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun requestSubscription(context: Context): Boolean {
        try {
            if (!PushPushGo.isInitialized()) {
                Log.e(TAG, "PushPushGo SDK is not initialized")
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                    != PackageManager.PERMISSION_GRANTED) {

                    if (context is Activity) {
                        Log.d(TAG, "Requesting POST_NOTIFICATIONS permission")
                        ActivityCompat.requestPermissions(
                            context,
                            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                            NOTIFICATION_PERMISSION_REQUEST_CODE
                        )
                    } else {
                        Log.w(TAG, "Cannot request permission: context is not an Activity")
                        return false
                    }
                }
            }

            val pushSdk = PushPushGo.getInstance()
            Log.d(TAG, "Requesting push notification subscription")
            pushSdk.registerSubscriber()
            
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting subscription", e)
            return false
        }
    }
}
