package com.pushpushgo.sdk.inapp

import android.app.Application
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pushpushgo.sdk.core.api.Config
import com.pushpushgo.sdk.inapp.model.network.InAppMessagesResponse
import com.pushpushgo.sdk.inapp.model.network.Metadata
import com.pushpushgo.sdk.inapp.network.InAppEventApi
import com.pushpushgo.sdk.inapp.network.InAppListGetApi
import com.pushpushgo.sdk.inapp.network.RetrofitProvider
import io.mockk.MockKMatcherScope
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import retrofit2.Retrofit

@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.Config(sdk = [33])
class InAppMessagesTest {
  private val application: Application = getApplicationContext()
  private val projectA = Config.create("8kp60aqdi49eioqzp0ihiytn", "00000000-0000-0000-0000-000000000001")
  private val projectB = Config.create("j15m43rl9l3owwuonfnvxm84", "00000000-0000-0000-0000-000000000002")

  private val api = mockk<InAppListGetApi>()

  @Before
  fun setUp() {
    val retrofit = mockk<Retrofit>()
    every { retrofit.create(InAppListGetApi::class.java) } returns api
    every { retrofit.create(InAppEventApi::class.java) } returns mockk(relaxed = true)
    mockkObject(RetrofitProvider)
    every { RetrofitProvider.buildRetrofit(any()) } returns retrofit
    coEvery { api.getInAppMessages(any(), any(), any(), any(), any(), any(), any()) } returns
      Response.success(InAppMessagesResponse(data = emptyList(), metadata = Metadata(total = 0)))
  }

  @After
  fun tearDown() {
    InAppMessages.reset()
    unmockkObject(RetrofitProvider)
  }

  @Test
  fun `initialize with the same configuration has no effect`() {
    InAppMessages.initialize(application, projectA)
    coVerify(timeout = 5_000) { fetchMessages(projectA) }

    InAppMessages.initialize(application, projectA)
    Thread.sleep(300)

    assertTrue(InAppMessages.isInitialized())
    coVerify(exactly = 1) { fetchMessages(projectA) }
  }

  @Test
  fun `calls before initialization fail`() {
    assertFalse(InAppMessages.isInitialized())

    val failure =
      assertThrows(IllegalStateException::class.java) {
        InAppMessages.showMessagesOnRoute("home")
      }

    assertEquals("InAppMessages SDK is not initialized", failure.message)
    assertThrows(IllegalStateException::class.java) { InAppMessages.switchProject(projectB) }
  }

  @Test
  fun `initialize with a different configuration points to switchProject`() {
    InAppMessages.initialize(application, projectA)

    val failure =
      assertThrows(IllegalStateException::class.java) {
        InAppMessages.initialize(application, projectB)
      }

    assertEquals(
      "InAppMessages SDK is already initialized with a different configuration. " +
        "Use InAppMessages.switchProject() to move it to another project.",
      failure.message,
    )
  }

  @Test
  fun `switchProject fetches messages of the new project`() {
    InAppMessages.initialize(application, projectA)
    coVerify(timeout = 5_000) { fetchMessages(projectA) }

    InAppMessages.switchProject(projectB)

    coVerify(timeout = 5_000) { fetchMessages(projectB) }
    // The new configuration is the current one now, so initializing with it has no effect
    InAppMessages.initialize(application, projectB)
    assertThrows(IllegalStateException::class.java) { InAppMessages.initialize(application, projectA) }
  }

  @Test
  fun `switchProject to the current configuration does nothing`() {
    InAppMessages.initialize(application, projectA)
    coVerify(timeout = 5_000) { fetchMessages(projectA) }

    InAppMessages.switchProject(projectA)
    Thread.sleep(300)

    coVerify(exactly = 1) { fetchMessages(projectA) }
  }

  private suspend fun MockKMatcherScope.fetchMessages(project: Config) =
    api.getInAppMessages(project.projectId, project.apiKey, any(), any(), any(), any(), any())
}
