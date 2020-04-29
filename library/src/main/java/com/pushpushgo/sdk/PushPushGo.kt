package com.pushpushgo.sdk

import android.app.Application
import android.content.Context
import com.pushpushgo.sdk.exception.PushPushException
import android.preference.PreferenceManager
import androidx.core.app.NotificationManagerCompat
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
import kotlinx.coroutines.*
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.android.x.androidXModule
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.singleton
import timber.log.Timber
import java.util.*
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.ActivityManager

internal class InternalTimerTask : Timer() {

    private var hasStarted = false


    override fun scheduleAtFixedRate(task: TimerTask?, delay: Long, period: Long) {
        this.hasStarted = true
        super.scheduleAtFixedRate(task, delay, period)
    }

    override fun cancel() {
        this.hasStarted = false
        super.cancel()
    }
    fun cancelIfRunning(){
        if (isRunning()) cancel()
    }

    private fun isRunning(): Boolean {
        return this.hasStarted
    }
}
internal class ForegroundTaskChecker(private val application: Application, private val notifTimer: InternalTimerTask) : TimerTask() {
    private fun isAppOnForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val appProcesses = activityManager.runningAppProcesses ?: return false
        val packageName = context.packageName
        for (appProcess in appProcesses) {
            if (appProcess.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND && appProcess.processName == packageName) {
                return true
            }
        }
        return false
    }

    override fun run() {
        if (isAppOnForeground(application)) {
            notifTimer.cancelIfRunning()
            notifTimer.scheduleAtFixedRate(NotificationTimerTask(application), Date(), 30000)
        } else {
            notifTimer.cancelIfRunning()
        }
    }
}

internal class NotificationTimerTask(private val application: Application) : TimerTask() {

    override fun run() {
        if (NotificationManagerCompat.from(application).areNotificationsEnabled()) {
            Timber.tag(PushPushGoFacade.TAG).d("Notifications enabled")
            val subscriberId =
                PreferenceManager.getDefaultSharedPreferences(application).getString(PushPushGoFacade.SUBSCRIBER_ID, "")
            val token =
                PreferenceManager.getDefaultSharedPreferences(application).getString(PushPushGoFacade.LAST_TOKEN, "")
            if (subscriberId.isNullOrBlank() && !token.isNullOrBlank()) {
                GlobalScope.launch { PushPushGoFacade.INSTANCE?.getNetwork()?.registerToken(token) }
            }
        } else {
            Timber.tag(PushPushGoFacade.TAG).d("Notifications disabled")
            val subscriberId =
                PreferenceManager.getDefaultSharedPreferences(application).getString(PushPushGoFacade.SUBSCRIBER_ID, "")
            if (!subscriberId.isNullOrBlank() && PushPushGoFacade.INSTANCE != null) {
                GlobalScope.launch { PushPushGoFacade.INSTANCE!!.getNetwork().unregisterSubscriber(subscriberId) }
                this.cancel()
            }
        }
    }

}

internal class PushPushGo(application: Application, apiKey: String, projectId: String) : KodeinAware {

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
