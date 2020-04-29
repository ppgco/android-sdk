package com.pushpushgo.sdk

import android.app.Application
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.facade.PushPushGoFacade
import com.pushpushgo.sdk.fcm.PushPushGoMessagingListener
import com.pushpushgo.sdk.network.ApiService
import com.pushpushgo.sdk.network.ConnectivityInterceptor
import com.pushpushgo.sdk.network.ObjectResponseDataSource
import com.pushpushgo.sdk.network.ResponseInterceptor
import com.pushpushgo.sdk.network.impl.ConnectivityInterceptorImpl
import com.pushpushgo.sdk.network.impl.ObjectResponseDataSourceImpl
import com.pushpushgo.sdk.network.impl.ResponseInterceptorImpl
import com.pushpushgo.sdk.utils.NotLoggingTree
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.android.x.androidXModule
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import timber.log.Timber
import java.util.*

internal class PushPushGo(
    val application: Application,
    val apiKey: String,
    val projectId: String
) : KodeinAware {

    private var listener: PushPushGoMessagingListener? = null

    val network by instance<ObjectResponseDataSource>()

    init {
        if (BuildConfig.DEBUG)
            Timber.plant(Timber.DebugTree())
        else
            Timber.plant(NotLoggingTree())
        Timber.tag(PushPushGoFacade.TAG).d("Register API Key: $apiKey")
        Timber.tag(PushPushGoFacade.TAG).d("Register ProjectId Key: $projectId")
        checkNotifications()
    }

    private fun checkNotifications() {
        Timer().scheduleAtFixedRate(ForegroundTaskChecker(application, InternalTimerTask()), Date(), 10000)
    }

    fun registerListener(pushPushGoMessagingListener: PushPushGoMessagingListener) {
        listener = pushPushGoMessagingListener
        Timber.tag(PushPushGoFacade.TAG).d("Registered PushPushGoMessagingListener")
    }

    fun getListener() = listener ?: throw PushPushException("Listener not registered")

    override val kodein = Kodein.lazy {
        import(androidXModule(this@PushPushGo.application))
        bind<ChuckerInterceptor>() with singleton { ChuckerInterceptor(this@PushPushGo.application) }
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
