package com.pushpushgo.sdk

import com.pushpushgo.sdk.exception.PushPushException
import com.pushpushgo.sdk.work.UploadDelegate
import io.mockk.*
import io.mockk.impl.annotations.MockK
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

internal class BeaconBuilderTest {

    @MockK
    lateinit var uploadDelegate: UploadDelegate

    private lateinit var beaconBuilder: BeaconBuilder

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Test
    fun `set string selector`() {
        every { uploadDelegate.sendBeacon(any()) } just Runs
        beaconBuilder = BeaconBuilder(uploadDelegate)

        beaconBuilder.set("Selector", "Value")

        beaconBuilder.send()

        verify {
            uploadDelegate.sendBeacon(match {
                it["Selector"] == "Value"
            })
        }
    }

    @Test
    fun `set boolean selector`() {
        every { uploadDelegate.sendBeacon(any()) } just Runs
        beaconBuilder = BeaconBuilder(uploadDelegate)

        beaconBuilder.set("Selector", true)

        beaconBuilder.send()

        verify {
            uploadDelegate.sendBeacon(match {
                it["Selector"] == true
            })
        }
    }

    @Test
    fun `set char selector`() {
        every { uploadDelegate.sendBeacon(any()) } just Runs
        beaconBuilder = BeaconBuilder(uploadDelegate)

        beaconBuilder.set("Selector", 'A')

        beaconBuilder.send()

        verify {
            uploadDelegate.sendBeacon(match {
                it["Selector"] == 'A'
            })
        }
    }

    @Test
    fun `set number selector`() {
        every { uploadDelegate.sendBeacon(any()) } just Runs
        beaconBuilder = BeaconBuilder(uploadDelegate)

        beaconBuilder.set("Selector", 421)

        beaconBuilder.send()

        verify {
            uploadDelegate.sendBeacon(match {
                it["Selector"] == 421
            })
        }
    }

    @Test
    fun `set unsupported selector`() {
        every { uploadDelegate.sendBeacon(any()) } just Runs
        beaconBuilder = BeaconBuilder(uploadDelegate)

        beaconBuilder.set("Selector", JSONObject())

        val exception = requireNotNull(runCatching { beaconBuilder.send() }.exceptionOrNull())
        assertEquals(PushPushException::class.java, exception::class.java)
        assertEquals("Invalid type of beacon selector value. Supported types: boolean, string, char, number", exception.message)

        verify { uploadDelegate wasNot Called }
    }

    @Test
    fun `append tag with label`() {
        beaconBuilder = BeaconBuilder(uploadDelegate)

        beaconBuilder.appendTag("tag1", "label1")

        val tags = beaconBuilder.getTags()

        assertEquals(1, tags.size)
        assertEquals(tags[0].first, "tag1")
        assertEquals(tags[0].second, "label1")
    }

    @Test
    fun `append tag without label`() {
        beaconBuilder = BeaconBuilder(uploadDelegate)

        beaconBuilder.appendTag("tag1")

        val tags = beaconBuilder.getTags()

        assertEquals(1, tags.size)
        assertEquals(tags[0].first, "tag1")
        assertEquals(tags[0].second, "default")
    }

    @Test
    fun `append many tags with label`() {
        every { uploadDelegate.sendBeacon(any()) } just Runs
        beaconBuilder = BeaconBuilder(uploadDelegate)

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
            uploadDelegate.sendBeacon(withArg {
                val tag = (it["tags"] as JSONArray).getJSONObject(0)

                assertEquals("tag1", tag["tag"])
                assertEquals("label1", tag["label"])
                assertEquals("append", tag["strategy"])
                assertEquals(0, tag["ttl"])
            })
        }
    }

    @Test
    fun `remove tag`() {
        every { uploadDelegate.sendBeacon(any()) } just Runs
        beaconBuilder = BeaconBuilder(uploadDelegate)

        beaconBuilder.removeTag("tag1", "tag2")
        beaconBuilder.send()

        val tags = beaconBuilder.getTagsToDelete()

        assertEquals(2, tags.size)
        assertEquals(tags[0], "tag1")
        assertEquals(tags[1], "tag2")

        verify {
            uploadDelegate.sendBeacon(match {
                it["tagsToDelete"].toString() == """["tag1","tag2"]"""
            })
        }
    }

    @Test
    fun `set custom id`() {
        every { uploadDelegate.sendBeacon(any()) } just Runs

        beaconBuilder = BeaconBuilder(uploadDelegate)

        beaconBuilder.setCustomId("id1")

        beaconBuilder.send()

        verify {
            uploadDelegate.sendBeacon(match {
                it["customId"] == "id1"
            })
        }
    }

    @Test
    fun `send empty beacon`() {
        every { uploadDelegate.sendBeacon(any()) } just Runs

        beaconBuilder = BeaconBuilder(uploadDelegate)

        beaconBuilder.send()

        verify {
            uploadDelegate.sendBeacon(match {
                it.toString() == "{}"
            })
        }
    }
}
