package com.pushpushgo.sdk.di

import android.content.Context
import androidx.work.WorkManager
import com.pushpushgo.sdk.work.UploadManager
import org.kodein.di.Kodein
import org.kodein.di.KodeinAware
import org.kodein.di.generic.bind
import org.kodein.di.generic.instance
import org.kodein.di.generic.provider
import org.kodein.di.generic.singleton

internal class WorkModule(context: Context) : KodeinAware {

    override val kodein by Kodein.lazy {
        bind<Context>() with provider { context }
        bind<WorkManager>() with singleton { WorkManager.getInstance(instance()) }
        bind<UploadManager>() with singleton { UploadManager(instance()) }
    }

    val uploadManager by instance<UploadManager>()
}
