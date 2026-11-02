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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SubscriptionReconcileWorkerTest {
  @After
  fun tearDown() {
    unmockkObject(PushNotifications)
  }

  @Test
  fun `succeeds when the SDK is not initialized`() =
    runTest {
      PushNotifications.reset()

      val result = worker().doWork()

      assertEquals(ListenableWorker.Result.success(), result)
    }

  @Test
  fun `reconciles the subscription`() =
    runTest {
      mockkObject(PushNotifications)
      coEvery { PushNotifications.reconcileSubscription() } just runs

      val result = worker().doWork()

      assertEquals(ListenableWorker.Result.success(), result)
      coVerify(exactly = 1) { PushNotifications.reconcileSubscription() }
    }

  @Test
  fun `transient failure is retried and rejected request fails the work`() =
    runTest {
      mockkObject(PushNotifications)

      coEvery { PushNotifications.reconcileSubscription() } throws IOException("offline")
      val transientResult = worker().doWork()
      coEvery { PushNotifications.reconcileSubscription() } throws PushPushException("Bad request", 400)
      val rejectedResult = worker().doWork()

      assertEquals(ListenableWorker.Result.retry(), transientResult)
      assertEquals(ListenableWorker.Result.failure(), rejectedResult)
    }

  @Test
  fun `gives up after the last attempt`() =
    runTest {
      mockkObject(PushNotifications)
      coEvery { PushNotifications.reconcileSubscription() } throws IOException("offline")

      val result = worker(runAttemptCount = 29).doWork()

      assertEquals(ListenableWorker.Result.failure(), result)
    }

  private fun worker(runAttemptCount: Int = 0) =
    TestListenableWorkerBuilder<SubscriptionReconcileWorker>(
      context = getApplicationContext(),
      runAttemptCount = runAttemptCount,
    ).build()
}
