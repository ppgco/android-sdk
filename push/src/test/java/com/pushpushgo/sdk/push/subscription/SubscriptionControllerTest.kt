package com.pushpushgo.sdk.push.subscription

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.work.UploadManager
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class SubscriptionControllerTest {
  private val apiRepository = mockk<ApiRepository>(relaxed = true)
  private val uploadManager = mockk<UploadManager>(relaxed = true)
  private val sharedPref = mockk<SharedPreferencesHelper>(relaxed = true)
  private val isMigrating = AtomicBoolean(false)

  private fun controller(notificationsEnabled: Boolean = true) =
    SubscriptionController(
      scope = CoroutineScope(Dispatchers.Unconfined),
      mutex = Mutex(),
      apiRepository = apiRepository,
      uploadManager = uploadManager,
      sharedPref = sharedPref,
      isMigrating = isMigrating,
      notificationsEnabled = { notificationsEnabled },
    )

  @Test
  fun `subscribe marks subscribed and enqueues a register`() {
    controller().subscribe()

    verifyOrder {
      sharedPref.isSubscribed = true
      uploadManager.sendRegister(null)
    }
  }

  @Test
  fun `subscribe is ignored when notifications are disabled`() {
    controller(notificationsEnabled = false).subscribe()

    verify(exactly = 0) { uploadManager.sendRegister(any()) }
  }

  @Test
  fun `subscribe is ignored while migrating`() {
    isMigrating.set(true)

    controller().subscribe()

    verify(exactly = 0) { uploadManager.sendRegister(any()) }
  }

  @Test
  fun `subscribeNow registers directly and marks subscribed`() =
    runBlocking {
      controller().subscribeNow()

      coVerify { apiRepository.registerToken(null) }
      verify { sharedPref.isSubscribed = true }
    }

  @Test(expected = IllegalStateException::class)
  fun `subscribeNow throws while migrating`() =
    runBlocking {
      isMigrating.set(true)
      controller().subscribeNow()
    }

  @Test(expected = IllegalStateException::class)
  fun `subscribeNow throws when notifications are disabled`() =
    runBlocking {
      controller(notificationsEnabled = false).subscribeNow()
    }

  @Test
  fun `unsubscribeNow unregisters and clears subscribed flag`() =
    runBlocking {
      controller().unsubscribeNow()

      coVerify { apiRepository.unregisterSubscriber() }
      verify { sharedPref.isSubscribed = false }
    }
}
