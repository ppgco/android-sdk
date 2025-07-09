package com.pushpushgo.inappmessages.utils

import android.content.Context
import android.util.Log
import java.lang.reflect.InvocationTargetException

/**
 * Interface for requesting push notification subscription.
 * This provides a clean way for the in-app messages to trigger a subscription request
 * without directly depending on the push notification SDK.
 */
interface PushNotificationSubscriber {
    /**
     * Request user to subscribe to push notifications
     * 
     * @param context Android context
     * @return true if the subscription request was successfully initiated
     */
    fun requestSubscription(context: Context): Boolean
}

/**
 * Default implementation that attempts to find the PushPushGoSubscriptionManager 
 * in the push notifications SDK using reflection.
 * 
 * This provides automatic integration between the in-app messages library and 
 * the push notifications SDK without creating direct compile-time dependencies.
 */
internal class DefaultPushNotificationSubscriber : PushNotificationSubscriber {
    companion object {
        private const val TAG = "DefaultPushSubscriber"
        private const val BRIDGE_CLASS = "com.pushpushgo.sdk.bridge.PushPushGoSubscriptionBridgeManager"
    }
    
    override fun requestSubscription(context: Context): Boolean {
        try {
            // Try to find the PushPushGoSubscriptionManager class
            val managerClass = Class.forName(BRIDGE_CLASS)
            
            // Create a new instance of the manager
            val manager = managerClass.getDeclaredConstructor().newInstance()
            
            // Call the requestSubscription method
            val method = managerClass.getMethod("requestSubscription", Context::class.java)
            return method.invoke(manager, context) as Boolean
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "PushPushGoSubscriptionManager not found. Make sure the push notifications SDK is included", e)
        } catch (e: InvocationTargetException) {
            Log.e(TAG, "Error calling requestSubscription", e.targetException)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting subscription", e)
        }
        
        Log.w(TAG, "Subscription requested but could not be processed automatically")
        return false
    }
}
