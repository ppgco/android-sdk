package com.pushpushgo.sdk

import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.work.UploadManager
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.json.JSONArray
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

    @Test
    fun `append tag with label`() {
        beaconBuilder = BeaconBuilder(uploadManager)

        beaconBuilder.appendTag("tag1", "label1")

        val tags = beaconBuilder.getTags()

        assertEquals(1, tags.size)
        assertEquals(tags[0].first, "tag1")
        assertEquals(tags[0].second, "label1")
    }

    @Test
    fun `append tag without label`() {
        beaconBuilder = BeaconBuilder(uploadManager)

        beaconBuilder.appendTag("tag1")

        val tags = beaconBuilder.getTags()

        assertEquals(1, tags.size)
        assertEquals(tags[0].first, "tag1")
        assertEquals(tags[0].second, "default")
    }

    @Test
    fun `append many tags with label`() {
        every { uploadManager.sendBeacon(any()) } just Runs
        beaconBuilder = BeaconBuilder(uploadManager)

        beaconBuilder.appendTag("tag1", "label1")
            .appendTag("tag2", "label2")
            .appendTag("tag3", "label3")

        beaconBuilder.send()
        val tags = beaconBuilder.getTags()

        assertEquals(3, tags.size)
        assertEquals(tags[0].first, "tag1")
        assertEquals(tags[0].second, "label1")
        assertEquals(tags[2].first, "tag3")
        assertEquals(tags[2].second, "label3")

        verify {
            uploadManager.sendBeacon(match {
                (it["tags"] as JSONArray).get(0).toString() == """{"tag":"tag1","label":"label1"}"""
            })
        }
    }

    @Test
    fun `remove tag`() {
        every { uploadManager.sendBeacon(any()) } just Runs
        beaconBuilder = BeaconBuilder(uploadManager)

        beaconBuilder.removeTag("tag1", "tag2")
        beaconBuilder.send()

        val tags = beaconBuilder.getTagsToDelete()

        assertEquals(2, tags.size)
        assertEquals(tags[0], "tag1")
        assertEquals(tags[1], "tag2")

        verify {
            uploadManager.sendBeacon(match {
                it["tagsToDelete"].toString() == """["tag1","tag2"]"""
            })
        }
    }

    @Test
    fun `set custom id`() {
        every { uploadManager.sendBeacon(any()) } just Runs

        beaconBuilder = BeaconBuilder(uploadManager)

        beaconBuilder.setCustomId("id1")

        beaconBuilder.send()

        verify {
            uploadManager.sendBeacon(match {
                it["customId"] == "id1"
            })
        }
    }

    @Test
    fun `send empty beacon`() {
        every { uploadManager.sendBeacon(any()) } just Runs

        beaconBuilder = BeaconBuilder(uploadManager)

        beaconBuilder.send()

        verify {
            uploadManager.sendBeacon(match {
                it.toString() == "{}"
            })
        }
    }
}
