package com.pushpushgo.sdk.push

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.pushpushgo.sdk.push.network.SharedPreferencesHelper
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class InitializeTest {
  private val application by lazy { getApplicationContext<Application>() }
  private val config = testConfig()

  @Before
  fun setUp() {
    WorkManagerTestInitHelper.initializeTestWorkManager(application)
  }

  @After
  fun tearDown() {
    PushNotifications.setSubscriptionStatusCallback(null)
    runTest { PushNotifications.reset() }
    WorkManagerTestInitHelper.closeWorkDatabase()
  }

  @Test
  fun `initialize with the same config is a no-op`() {
    val initialized = PushNotifications.initialize(application, config)

    val initializedAgain = PushNotifications.initialize(application, testConfig())

    assertSame(initialized, initializedAgain)
  }

  @Test
  fun `initialize with a different config points to switchProject`() {
    PushNotifications.initialize(application, config)

    val exception =
      assertFailsWith<IllegalStateException> {
        PushNotifications.initialize(application, otherProjectTestConfig())
      }

    assertEquals(
      "PushNotifications SDK is already initialized with a different configuration. " +
        "Use PushNotifications.switchProject() to move it to another project.",
      exception.message,
    )
  }

  @Test
  fun `callback set before initialization receives unsubscribed then initialized runtime status`() {
    SharedPreferencesHelper(application).apply {
      projectOwner = config
      subscriberId = "sub-before-init"
      subscriptionRequested = true
    }
    shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val received = mutableListOf<SubscriptionStatus>()
    PushNotifications.setSubscriptionStatusCallback(received::add)

    PushNotifications.initialize(application, config)

    assertEquals(
      listOf(SubscriptionStatus.Unsubscribed, SubscriptionStatus.Subscribed("sub-before-init", config.projectId)),
      received,
    )
  }
}
