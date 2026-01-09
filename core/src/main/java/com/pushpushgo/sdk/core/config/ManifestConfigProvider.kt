package com.pushpushgo.sdk.core.config

import android.content.Context
import android.content.pm.PackageManager

class ManifestConfigProvider(
  private val context: Context,
) : ConfigProvider {
  override fun provide(): Config {
    val packageManager = context.packageManager
    val metadata =
      packageManager
        .getApplicationInfo(
          context.packageName,
          PackageManager.GET_META_DATA,
        ).metaData

    requireNotNull(metadata) {
      "Missing metadata"
    }

    return Config(
      projectId =
        metadata.getString(
          "com.pushpushgo.projectId",
        ) ?: throw IllegalStateException("Missing metadata: com.pushpushgo.projectId"),
      apiKey =
        metadata.getString("com.pushpushgo.apikey") ?: metadata.getString("com.pushpushgo.apiKey")
          ?: throw IllegalStateException("Missing metadata: com.pushpushgo.apikey"),
      apiUrl = metadata.getString("com.pushpushgo.apiUrl"),
      isDebug = metadata.getBoolean("com.pushpushgo.isDebug"),
    )
  }
}
