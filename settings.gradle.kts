pluginManagement {
  repositories {
    gradlePluginPortal()
    google()
    mavenCentral()
    maven(url = "https://developer.huawei.com/repo/")
  }

  resolutionStrategy {
    eachPlugin {
      if (requested.id.id == "com.huawei.agconnect") {
        useModule("com.huawei.agconnect:agcp:1.9.1.304")
      }
    }
  }
}

include(
  ":core",
  ":push",
  ":push:sample:firebase",
  ":push:sample:hms",
  ":push:sample:java",
  ":inapp",
  ":inapp:sample",
)
