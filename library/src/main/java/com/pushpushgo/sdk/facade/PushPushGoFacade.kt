package com.pushpushgo.sdk.facade

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.pushpushgo.sdk.PushPushGo
import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.fcm.PushPushGoMessagingListener

class PushPushGoFacade(application: Application, apiKey: String) {

    private var pushPushGo: PushPushGo = PushPushGo(application, apiKey)

    companion object {
        internal const val TAG = "_PushPushGoSDKProvider_"
        /**
         * an instance of PushPushGo library
         */
        internal var INSTANCE: PushPushGoFacade? = null

        /**
         * function to create an instance of PushPushGo object to handle push notifications
         * @param context - context of an application to get apiKey from META DATA stored in Your Manifest.xml file
         * @return PushPushGoFacade instance
         */
        @kotlin.jvm.JvmStatic
        fun getInstance(context: Context): PushPushGoFacade {
            if (INSTANCE == null) {
                val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                val bundle = ai.metaData
                val apiKey = bundle.getString("com.pushpushgo.apikey")
                    ?: throw PushPushException("You have to declare apiKey in Your Manifest file")
                INSTANCE = PushPushGoFacade(context.applicationContext as Application, apiKey)
            }
            return INSTANCE as PushPushGoFacade
        }

        /**
         * function to create an instance of PushPushGo object to handle push notifications
         * @param context - context of an application to handle DI
         * @param apiKey - key to communicate with RESTFul API
         * @return PushPushGoFacade instance
         */
        @kotlin.jvm.JvmStatic
        fun getInstance(context: Context, apiKey: String): PushPushGoFacade {
            if (INSTANCE == null) {
                INSTANCE = PushPushGoFacade(context.applicationContext as Application, apiKey)
            }
            return INSTANCE as PushPushGoFacade
        }


    }

    /**
     * function to register a listener and handle RemoteMessage from push notifications
     * @param listener - implementation of PushPushGoMessagingListener
     */
    fun registerListener(listener: PushPushGoMessagingListener) {
        this.pushPushGo.registerListener(listener)
    }

    /**
     * function to read Your API Key from an PushPushGo library instance
     * @return API Key String
     */
    fun getApiKey(): String {
        return this.pushPushGo.getApiKey()
    }

    /**
     * function to get Your API MessageListener from an PushPushGo library instance
     * @return PushPushGoMessagingListener listener implementation
     * @throws PushPushException if listener is not set
     */
    @Throws(PushPushException::class)
    fun getListener(): PushPushGoMessagingListener {
        return this.pushPushGo.getListener()
    }

}