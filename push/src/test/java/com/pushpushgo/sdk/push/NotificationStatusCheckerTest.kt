package com.pushpushgo.sdk.push

import android.content.Context
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import com.pushpushgo.sdk.push.push.areNotificationsEnabled
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class NotificationStatusCheckerTest {
  private val context: Context = getApplicationContext()

  private lateinit var preferences: SharedPreferencesHelper
  private lateinit var checker: NotificationStatusChecker
  private var registerCalls = 0
  private var unregisterCalls = 0

  @Before
  fun setUp() {
    mockkStatic(NOTIFICATION_UTILS)
    preferences = SharedPreferencesHelper(context, prefsName = "status_checker_test_prefs")
    checker =
      NotificationStatusChecker(
        context = context,
        sdkScope = CoroutineScope(Job()),
        sharedPreferencesHelper = preferences,
        register = { registerCalls++ },
        unregister = { unregisterCalls++ },
      )
  }

  @After
  fun tearDown() {
    preferences.clearProjectData()
    unmockkStatic(NOTIFICATION_UTILS)
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
  }
}
