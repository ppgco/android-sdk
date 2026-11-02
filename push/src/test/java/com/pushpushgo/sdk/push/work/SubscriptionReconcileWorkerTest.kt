package com.pushpushgo.sdk.push.work

import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.pushpushgo.sdk.push.PushNotifications
import com.pushpushgo.sdk.push.exception.PushPushException
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class SubscriptionReconcileWorkerTest {
  @After
  fun tearDown() {
    unmockkObject(PushNotifications)
  }

  @Test
  fun `succeeds when the SDK is not initialized`() =
    runBlocking {
      assertFalse(PushNotifications.isInitialized())

      assertEquals(ListenableWorker.Result.success(), worker().doWork())
    }

  @Test
  fun `reconciles the subscription`() =
    runBlocking {
      mockkObject(PushNotifications)
      coEvery { PushNotifications.reconcileSubscription() } just runs

      assertEquals(ListenableWorker.Result.success(), worker().doWork())
      coVerify(exactly = 1) { PushNotifications.reconcileSubscription() }
    }

  @Test
  fun `transient failures are retried`() =
    runBlocking {
      mockkObject(PushNotifications)

      listOf(IOException("offline"), PushPushException("Server error", 503)).forEach { error ->
        coEvery { PushNotifications.reconcileSubscription() } throws error

        assertEquals(error.message, ListenableWorker.Result.retry(), worker().doWork())
      }
    }

  @Test
  fun `rejected request fails the work`() =
    runBlocking {
      mockkObject(PushNotifications)
      coEvery { PushNotifications.reconcileSubscription() } throws PushPushException("Bad request", 400)

      assertEquals(ListenableWorker.Result.failure(), worker().doWork())
    }

  @Test
  fun `gives up after the last attempt`() =
    runBlocking {
      mockkObject(PushNotifications)
      coEvery { PushNotifications.reconcileSubscription() } throws IOException("offline")

      assertEquals(ListenableWorker.Result.failure(), worker(runAttemptCount = 29).doWork())
    }

  private fun worker(runAttemptCount: Int = 0) =
    TestListenableWorkerBuilder<SubscriptionReconcileWorker>(
      context = getApplicationContext(),
      runAttemptCount = runAttemptCount,
    ).build()
}
