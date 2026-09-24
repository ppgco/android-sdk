package com.pushpushgo.sdk.inapp.utils

import android.content.Context
import android.content.res.Configuration
import com.pushpushgo.sdk.inapp.model.DeviceType
import com.pushpushgo.sdk.inapp.model.OSType

internal object DeviceInfoProvider {
  fun getCurrentDeviceType(context: Context): DeviceType {
    val screenLayout = context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
    return when (screenLayout) {
      Configuration.SCREENLAYOUT_SIZE_LARGE, Configuration.SCREENLAYOUT_SIZE_XLARGE -> DeviceType.TABLET
      else -> DeviceType.MOBILE
    }
  }

  fun getCurrentOSType(): OSType = OSType.ANDROID
}
