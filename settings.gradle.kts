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

val isJitPack = System.getenv("JITPACK") != null

include(
  ":core",
  ":push",
  ":inapp",
)

if (!isJitPack) {
  include(
    ":push:sample:firebase",
    ":push:sample:hms",
    ":push:sample:java",
    ":inapp:sample"
  )
}
