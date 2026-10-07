package com.pushpushgo.sdk.inapp.utils

import com.pushpushgo.sdk.inapp.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceInfoProviderTest {
  @Test
  fun `phone sized windows are reported as mobile`() {
    assertEquals(DeviceType.MOBILE, DeviceInfoProvider.deviceTypeFor(320))
    assertEquals(DeviceType.MOBILE, DeviceInfoProvider.deviceTypeFor(599))
  }

  @Test
  fun `large screens are reported as tablet`() {
    assertEquals(DeviceType.TABLET, DeviceInfoProvider.deviceTypeFor(600))
    assertEquals(DeviceType.TABLET, DeviceInfoProvider.deviceTypeFor(1280))
  }
}
