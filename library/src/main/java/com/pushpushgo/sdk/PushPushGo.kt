package com.pushpushgo.sdk

import android.app.Application
import android.content.Context
import com.pushpushgo.sdk.exception.PushPushException
import android.content.pm.PackageManager
import com.pushpushgo.sdk.facade.PushPushGoFacade
import com.pushpushgo.sdk.network.ApiService
import com.pushpushgo.sdk.network.ConnectivityInterceptor
import com.pushpushgo.sdk.network.ObjectResponseDataSource
import com.pushpushgo.sdk.network.ResponseInterceptor
import com.pushpushgo.sdk.network.impl.ConnectivityInterceptorImpl
import com.pushpushgo.sdk.network.impl.ObjectResponseDataSourceImpl
import com.pushpushgo.sdk.network.impl.ResponseInterceptorImpl
import com.pushpushgo.sdk.utils.NotLoggingTree
import com.readystatesoftware.chuck.ChuckInterceptor
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.android.x.androidXModule
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import timber.log.Timber


class PushPushGo(application: Application, apiKey: String) : KodeinAware {
    var application: Application? = null
    var apiKey: String? = null

    companion object {
        var INSTANCE: PushPushGo? = null
        /**
         * function to create an instance of PushPushGo object to handle push notifications
         * @param context - context of an application to get apiKey from META DATA stored in Your Manifest.xml file
         * @return PushPushGo instance
         */
        fun getInstance(context: Context): PushPushGo {
            if (INSTANCE == null) {
                val ai = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                val bundle = ai.metaData
                val apiKey = bundle.getString("com.pushpushgo.apikey")
                    ?: throw PushPushException("You have to declare apiKey in Your Manifest file")
                INSTANCE = PushPushGo(context.applicationContext as Application, apiKey)
            }
            return INSTANCE as PushPushGo
        }

        /**
         * function to create an instance of PushPushGo object to handle push notifications
         * @param context - context of an application to handle DI
         * @param apiKey - key to communicate with RESTFul API
         * @return PushPushGo instance
         */
        fun getInstance(context: Context, apiKey: String): PushPushGo {
            if (INSTANCE == null) {
                INSTANCE = PushPushGo(context.applicationContext as Application, apiKey)
            }
            return INSTANCE as PushPushGo
        }

    }

    init {
        this.apiKey = apiKey


        if (BuildConfig.DEBUG)
            Timber.plant(Timber.DebugTree())
        else
            Timber.plant(NotLoggingTree())
        Timber.d("Register API Key: $apiKey")
        PushPushGoFacade(application).registerApiKey(apiKey)
    }



    override val kodein = Kodein.lazy {
        import(androidXModule(this@PushPushGo.application!!))
        bind<ChuckInterceptor>() with singleton { ChuckInterceptor(instance()) }
        bind<ConnectivityInterceptor>() with singleton { ConnectivityInterceptorImpl(instance()) }
        bind<ResponseInterceptor>() with singleton { ResponseInterceptorImpl(instance()) }
        bind() from singleton {
            ApiService(
                instance(),
                instance(),
                instance()
            )
        }
        bind<ObjectResponseDataSource>() with singleton { ObjectResponseDataSourceImpl(instance()) }
    }
}
