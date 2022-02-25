package com.pushpushgo.sdk.di

import android.content.Context
import androidx.work.WorkManager
import com.pushpushgo.sdk.network.SharedPreferencesHelper
import com.pushpushgo.sdk.work.UploadManager
import org.kodein.di.*

internal class WorkModule(context: Context) : DIAware {

    override val di by DI.lazy {
        bind<Context>() with provider { context }
        bind<SharedPreferencesHelper>() with singleton { SharedPreferencesHelper(instance()) }
        bind<WorkManager>() with singleton { WorkManager.getInstance(instance()) }
        bind<UploadManager>() with singleton { UploadManager(instance(), instance()) }
    }

    val uploadManager by instance<UploadManager>()
}
