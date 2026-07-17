package com.pushpushgo.sdk.push

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

internal class BeaconBuilderTest {
  private val beaconBuilder = BeaconBuilder()

  @Test
  fun `set string selector`() {
    beaconBuilder.set("Selector", "Value")

    assertEquals("Value", sendBeacon()["Selector"])
  }

  @Test
  fun `set boolean selector`() {
    beaconBuilder.set("Selector", true)

    assertEquals(true, sendBeacon()["Selector"])
  }

  @Test
  fun `set char selector`() {
    beaconBuilder.set("Selector", 'A')

    assertEquals("A", sendBeacon()["Selector"])
  }

  @Test
  fun `set number selector`() {
    beaconBuilder.set("Selector", 421)

    assertEquals(421, sendBeacon()["Selector"])
  }

  @Test
  fun `append tag with label`() {
    beaconBuilder.appendTag("tag1", "label1")

    assertEquals(listOf("tag1" to "label1"), beaconBuilder.getTags())
  }

  @Test
  fun `append tag without label`() {
    beaconBuilder.appendTag("tag1")

    assertEquals(listOf("tag1" to "default"), beaconBuilder.getTags())
  }

  @Test
  fun `append many tags with label`() {
    beaconBuilder
      .appendTag("tag1", "label1")
      .appendTag("tag2", "label2")
      .appendTag("tag3", "label3")

    assertEquals(
      listOf("tag1" to "label1", "tag2" to "label2", "tag3" to "label3"),
      beaconBuilder.getTags(),
    )

    val tag = (sendBeacon()["tags"] as JSONArray).getJSONObject(0)
    assertEquals("tag1", tag["tag"])
    assertEquals("label1", tag["label"])
    assertEquals("append", tag["strategy"])
    assertEquals(0, tag["ttl"])
  }

  @Test
  fun `remove tag`() {
    beaconBuilder.removeTag("tag1", "tag2")

    assertEquals(listOf("tag1", "tag2"), beaconBuilder.getTagsToDelete())
    assertEquals("""["tag1","tag2"]""", sendBeacon()["tagsToDelete"].toString())
  }

  @Test
  fun `remove tag with a custom label`() {
    beaconBuilder.removeTags(mapOf("tag_name" to "test_label"))

    assertEquals(listOf("tag_name"), beaconBuilder.getTagsToDelete())
    assertEquals(
      """[{"tag":"tag_name","label":"test_label"}]""",
      sendBeacon()["tagsToDelete"].toString(),
    )
  }

  @Test
  fun `remove same tag with different labels`() {
    beaconBuilder.removeTags(
      listOf(
        "value1" to "test_label",
        "value3" to "test_label",
        "value1" to "other_label",
      ),
    )

    assertEquals(listOf("value1", "value3", "value1"), beaconBuilder.getTagsToDelete())
    assertEquals(
      """[{"tag":"value1","label":"test_label"},{"tag":"value3","label":"test_label"},{"tag":"value1","label":"other_label"}]""",
      sendBeacon()["tagsToDelete"].toString(),
    )
  }

  @Test
  fun `set custom id`() {
    beaconBuilder.setCustomId("id1")

    assertEquals("id1", sendBeacon()["customId"])
  }

  @Test
  fun `send empty beacon`() {
    assertEquals("{}", sendBeacon().toString())
  }

  @Test
  fun `built beacon is an immutable snapshot`() {
    val beacon = beaconBuilder.setCustomId("first").build()

    beaconBuilder.setCustomId("second")

    assertEquals("first", JSONObject(beacon.payload)["customId"])
  }

  @Test
  fun `assign to group`() {
    beaconBuilder.assignToGroup("my-segment-123")

    assertEquals("my-segment-123", sendBeacon()["assignToGroup"])
  }

  @Test
  fun `unassign from group`() {
    beaconBuilder.unassignFromGroup("my-segment-333")

    assertEquals("my-segment-333", sendBeacon()["unassignFromGroup"])
  }

  @Test
  fun `assign and unassign from groups simultaneously`() {
    beaconBuilder
      .assignToGroup("my-segment-123")
      .unassignFromGroup("my-segment-333")

    val beacon = sendBeacon()

    assertEquals("my-segment-123", beacon["assignToGroup"])
    assertEquals("my-segment-333", beacon["unassignFromGroup"])
  }

  @Test
  fun `assign to group with other beacon data`() {
    beaconBuilder
      .setCustomId("xxsampleId")
      .assignToGroup("my-segment-123")
      .appendTag("tag1", "label1")

    val beacon = sendBeacon()

    assertEquals("xxsampleId", beacon["customId"])
    assertEquals("my-segment-123", beacon["assignToGroup"])
    assertTrue(beacon.has("tags"))
  }

  private fun sendBeacon(): JSONObject = JSONObject(beaconBuilder.build().payload)
}
