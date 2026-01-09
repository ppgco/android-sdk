package com.pushpushgo.sdk.push.di

import android.content.Context
import androidx.work.WorkManager
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.work.UploadManager
import org.kodein.di.DI
import org.kodein.di.DIAware
import org.kodein.di.bind
import org.kodein.di.instance
import org.kodein.di.provider
import org.kodein.di.singleton

internal class WorkModule(
  context: Context,
) : DIAware {
  override val di by DI.lazy {
    bind<Context>() with provider { context }
    bind<SharedPreferencesHelper>() with singleton { SharedPreferencesHelper(instance()) }
    bind<WorkManager>() with singleton { WorkManager.getInstance(instance()) }
    bind<UploadManager>() with singleton { UploadManager(instance(), instance()) }
  }

  val uploadManager by instance<UploadManager>()
}
