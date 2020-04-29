package com.pushpushgo.sdk

import android.app.Application
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
import com.readystatesoftware.chuck.ChuckInterceptor
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.android.x.androidXModule
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import timber.log.Timber
import java.util.*

internal class PushPushGo(application: Application, apiKey: String, projectId: String) :
    KodeinAware {

    private val dataSource by instance<ObjectResponseDataSource>()
    private var listener: PushPushGoMessagingListener? = null
    private var application: Application? = null
    private var timer: InternalTimerTask? = null
    private var apiKey: String = ""
    private var projectId: String = ""

    init {
        if (BuildConfig.DEBUG)
            Timber.plant(Timber.DebugTree())
        else
            Timber.plant(NotLoggingTree())
        Timber.tag(PushPushGoFacade.TAG).d("Register API Key: $apiKey")
        Timber.tag(PushPushGoFacade.TAG).d("Register ProjectId Key: $projectId")
        this.apiKey = apiKey
        this.projectId = projectId
        this.application = application
        this.timer = InternalTimerTask()
        checkNotifications()
    }

    private fun checkNotifications() {
        Timer().scheduleAtFixedRate(ForegroundTaskChecker(application!!, timer!!), Date(), 10000)
    }

    fun registerListener(listener: PushPushGoMessagingListener) {
        this.listener = listener
        Timber.tag(PushPushGoFacade.TAG).d("Registered PushPushGoMessagingListener")
    }

    fun getListener(): PushPushGoMessagingListener {
        if (this.listener == null)
            throw PushPushException("Listener not registered")
        return this.listener!!
    }

    fun getApiKey(): String {
        return this.apiKey
    }

    fun getProjectId(): String {
        return this.projectId
    }

    fun getApplication(): Application {
        return application!!
    }

    fun getNetwork(): ObjectResponseDataSource {
        return dataSource
    }

    override val kodein = Kodein.lazy {
        import(androidXModule(this@PushPushGo.application!!))
        bind<ChuckInterceptor>() with singleton { ChuckInterceptor(this@PushPushGo.application!!) }
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
