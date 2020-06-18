package com.pushpushgo.sdk

import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.work.UploadManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class BeaconBuilderTest {

    @MockK
    lateinit var uploadManager: UploadManager

    private lateinit var beaconBuilder: BeaconBuilder

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `set string selector`() {
        every { uploadManager.sendBeacon(any()) } just Runs
        beaconBuilder = BeaconBuilder(uploadManager)

        beaconBuilder.set("Selector", "Value")

        beaconBuilder.send()

        verify {
            uploadManager.sendBeacon(match {
                it["Selector"] == "Value"
            })
        }
    }

    @Test
    fun `set boolean selector`() {
        every { uploadManager.sendBeacon(any()) } just Runs
        beaconBuilder = BeaconBuilder(uploadManager)

        beaconBuilder.set("Selector", true)

        beaconBuilder.send()

        verify {
            uploadManager.sendBeacon(match {
                it["Selector"] == true
            })
        }
    }

    @Test
    fun `set char selector`() {
        every { uploadManager.sendBeacon(any()) } just Runs
        beaconBuilder = BeaconBuilder(uploadManager)

        beaconBuilder.set("Selector", 'A')

        beaconBuilder.send()

        verify {
            uploadManager.sendBeacon(match {
                it["Selector"] == 'A'
            })
        }
    }

    @Test
    fun `set number selector`() {
        every { uploadManager.sendBeacon(any()) } just Runs
        beaconBuilder = BeaconBuilder(uploadManager)

        beaconBuilder.set("Selector", 421)

        beaconBuilder.send()

        verify {
            uploadManager.sendBeacon(match {
                it["Selector"] == 421
            })
        }
    }

    @Test
    fun `set unsupported selector`() {
        every { uploadManager.sendBeacon(any()) } just Runs
        beaconBuilder = BeaconBuilder(uploadManager)

        beaconBuilder.set("Selector", JSONObject())

        val exception = requireNotNull(runCatching { beaconBuilder.send() }.exceptionOrNull())
        assertEquals(PushPushException::class.java, exception::class.java)
        assertEquals("Invalid type of beacon selector value. Supported types: boolean, string, char, number", exception.message)

        verify { uploadManager wasNot Called }
    }
}
