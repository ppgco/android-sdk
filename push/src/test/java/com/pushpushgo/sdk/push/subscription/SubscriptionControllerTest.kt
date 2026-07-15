package com.pushpushgo.sdk.push.subscription

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.push.network.ApiRepository
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.work.UploadManager
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verifyOrder
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class SubscriptionControllerTest {
  private val apiRepository = mockk<ApiRepository>(relaxed = true)
  private val uploadManager = mockk<UploadManager>(relaxed = true)
  private val sharedPref = mockk<SharedPreferencesHelper>(relaxed = true)

  private fun controller(notificationsEnabled: Boolean = true) =
    SubscriptionController(
      mutex = Mutex(),
      apiRepository = apiRepository,
      uploadManager = uploadManager,
      sharedPref = sharedPref,
      notificationsEnabled = { notificationsEnabled },
    )

  @Test
  fun `subscribe registers directly and marks subscribed`() =
    runBlocking {
      controller().subscribe()

      coVerify { apiRepository.registerToken(null) }
      verifyOrder {
        sharedPref.isSubscribed = true
        uploadManager.schedulePeriodicTokenSync()
      }
    }

  @Test(expected = IllegalStateException::class)
  fun `subscribe throws when notifications are disabled`() =
    runBlocking {
      controller(notificationsEnabled = false).subscribe()
    }

  @Test
  fun `unsubscribe unregisters and clears subscribed flag`() =
    runBlocking {
      controller().unsubscribe()

      coVerify { apiRepository.unregisterSubscriber() }
      verifyOrder {
        uploadManager.cancelPeriodicTokenSync()
        sharedPref.isSubscribed = false
      }
    }
}
