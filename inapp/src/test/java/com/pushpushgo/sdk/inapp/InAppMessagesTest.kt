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
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
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
    InAppMessages.resetInstance()
    unmockkObject(RetrofitProvider)
  }

  @Test
  fun `initialize with the same configuration returns the same instance`() {
    val instance = InAppMessages.initialize(application, projectA)

    assertSame(instance, InAppMessages.initialize(application, projectA))
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
        "Use InAppMessages.getInstance().switchProject() to move it to another project.",
      failure.message,
    )
  }

  @Test
  fun `switchProject fetches messages of the new project and keeps the instance`() {
    val instance = InAppMessages.initialize(application, projectA)
    coVerify(timeout = 5_000) { fetchMessages(projectA) }

    instance.switchProject(projectB)

    coVerify(timeout = 5_000) { fetchMessages(projectB) }
    assertSame(instance, InAppMessages.getInstance())
    // The same configuration is accepted again after the switch
    assertSame(instance, InAppMessages.initialize(application, projectB))
  }

  @Test
  fun `switchProject to the current configuration does nothing`() {
    val instance = InAppMessages.initialize(application, projectA)
    coVerify(timeout = 5_000) { fetchMessages(projectA) }

    instance.switchProject(projectA)
    Thread.sleep(300)

    coVerify(exactly = 1) { fetchMessages(projectA) }
  }

  private suspend fun MockKMatcherScope.fetchMessages(project: Config) =
    api.getInAppMessages(project.projectId, project.apiKey, any(), any(), any(), any(), any())
}
