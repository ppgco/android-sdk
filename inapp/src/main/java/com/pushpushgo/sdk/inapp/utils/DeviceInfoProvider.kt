package com.pushpushgo.sdk.inapp.utils

import android.content.Context
import com.pushpushgo.sdk.inapp.model.DeviceType
import com.pushpushgo.sdk.inapp.model.OSType

internal object DeviceInfoProvider {
  /** Smallest width, in dp, at which the system treats a display as a large screen. */
  private const val TABLET_MIN_WIDTH_DP = 600

  fun getCurrentDeviceType(context: Context): DeviceType = deviceTypeFor(context.resources.configuration.smallestScreenWidthDp)

  /**
   * Classifies the device by its current smallest width rather than by the screenLayout size
   * buckets, which are unreliable once the window can be resized freely - Android 17 (API 37)
   * ignores resizability restrictions on displays wider than 600dp.
   */
  fun deviceTypeFor(smallestScreenWidthDp: Int): DeviceType =
    if (smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP) DeviceType.TABLET else DeviceType.MOBILE

  fun getCurrentOSType(): OSType = OSType.ANDROID
}
