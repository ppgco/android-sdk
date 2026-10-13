package com.pushpushgo.sdk.push

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.areNotificationsEnabled
import com.pushpushgo.sdk.push.utils.logError
import com.pushpushgo.sdk.push.utils.logWarning
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class NotificationStatusCheckerTest {
  private val application: Application = getApplicationContext()

  // Unconfined runs the checks triggered by activity callbacks right away, on the calling thread
  private val sdkScope = CoroutineScope(Job() + Dispatchers.Unconfined)

  private lateinit var preferences: SharedPreferencesHelper
  private lateinit var checker: NotificationStatusChecker
  private var registerCalls = 0
  private var unregisterCalls = 0
  private var checkedCalls = 0
  private var registerAction: suspend () -> Unit = {}

  @Before
  fun setUp() {
    mockkStatic(NOTIFICATION_UTILS, LOG_UTILS)
    preferences = SharedPreferencesHelper(application, prefsName = "status_checker_test_prefs")
    checker =
      NotificationStatusChecker(
        application = application,
        sdkScope = sdkScope,
        sharedPreferencesHelper = preferences,
        register = {
          registerCalls++
          registerAction()
        },
        unregister = { unregisterCalls++ },
        onChecked = { checkedCalls++ },
      )
  }

  @After
  fun tearDown() {
    checker.stop()
    sdkScope.cancel()
    preferences.clearProjectData()
    unmockkStatic(NOTIFICATION_UTILS, LOG_UTILS)
  }

  @Test
  fun `registers again when notifications are enabled back and subscription is still requested`() =
    runBlocking {
      givenState(notificationsEnabled = true, requested = true, subscriberId = null)

      checker.checkNotificationsStatus()

      assertCalls(register = 1, unregister = 0)
    }

  @Test
  fun `unregisters when notifications get disabled`() =
    runBlocking {
      givenState(notificationsEnabled = false, requested = true, subscriberId = "sub-123")

      checker.checkNotificationsStatus()

      assertCalls(register = 0, unregister = 1)
    }

  @Test
  fun `unregisters a registration left without a subscription request`() =
    runBlocking {
      givenState(notificationsEnabled = true, requested = false, subscriberId = "sub-123")

      checker.checkNotificationsStatus()

      assertCalls(register = 0, unregister = 1)
    }

  @Test
  fun `does not register when subscription was not requested`() =
    runBlocking {
      givenState(notificationsEnabled = true, requested = false, subscriberId = null)

      checker.checkNotificationsStatus()

      assertCalls(register = 0, unregister = 0)
    }

  @Test
  fun `does nothing while registration matches notification state`() =
    runBlocking {
      givenState(notificationsEnabled = true, requested = true, subscriberId = "sub-123")
      checker.checkNotificationsStatus()

      givenState(notificationsEnabled = false, requested = true, subscriberId = null)
      checker.checkNotificationsStatus()

      assertCalls(register = 0, unregister = 0)
    }

  @Test
  fun `resumed activity triggers a check right away`() {
    givenState(notificationsEnabled = true, requested = true, subscriberId = "sub-123")
    checker.start()

    // e.g. the user revoked the permission in the system settings and comes back to the app
    givenState(notificationsEnabled = false, requested = true, subscriberId = "sub-123")
    Robolectric.buildActivity(Activity::class.java).setup()

    assertCalls(register = 0, unregister = 1)
  }

  @Test
  fun `stopped checker ignores resumed activities`() {
    givenState(notificationsEnabled = true, requested = true, subscriberId = "sub-123")
    checker.start()
    checker.stop()

    givenState(notificationsEnabled = false, requested = true, subscriberId = "sub-123")
    Robolectric.buildActivity(Activity::class.java).setup()

    assertCalls(register = 0, unregister = 0)
  }

  @Test
  fun `concurrent checks run once`() =
    runBlocking {
      givenState(notificationsEnabled = true, requested = true, subscriberId = null)
      val finishRegistration = CompletableDeferred<Unit>()
      registerAction = { finishRegistration.await() }

      val first = launch(start = CoroutineStart.UNDISPATCHED) { checker.checkNow() }
      val second = launch(start = CoroutineStart.UNDISPATCHED) { checker.checkNow() }
      finishRegistration.complete(Unit)
      joinAll(first, second)

      assertCalls(register = 1, unregister = 0)
    }

  @Test
  fun `failed checks are reported once per series and every check publishes the status`() =
    runBlocking {
      givenState(notificationsEnabled = true, requested = true, subscriberId = null)
      registerAction = { throw IOException("offline") }

      repeat(3) { checker.checkNow() }

      verify(exactly = 1) { logError("Notification status check failed", any()) }
      verify(exactly = 2) { logWarning(any()) }

      // A successful check ends the series, so the next failure is reported again
      registerAction = {}
      checker.checkNow()
      registerAction = { throw IOException("offline") }
      checker.checkNow()

      verify(exactly = 2) { logError("Notification status check failed", any()) }
      assertEquals("onChecked calls", 5, checkedCalls)
    }

  private fun givenState(
    notificationsEnabled: Boolean,
    requested: Boolean,
    subscriberId: String?,
  ) {
    every { areNotificationsEnabled(any()) } returns notificationsEnabled
    preferences.subscriptionRequested = requested
    preferences.subscriberId = subscriberId
  }

  private fun assertCalls(
    register: Int,
    unregister: Int,
  ) {
    assertEquals("register calls", register, registerCalls)
    assertEquals("unregister calls", unregister, unregisterCalls)
  }

  private companion object {
    const val NOTIFICATION_UTILS = "com.pushpushgo.sdk.push.push.NotificationUtilsKt"
    const val LOG_UTILS = "com.pushpushgo.sdk.push.utils.LogUtilsKt"
  }
}
