plugins {
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.binary.validator) apply false
  alias(libs.plugins.ktlint) apply false
  alias(libs.plugins.google.services) apply false
  id("com.huawei.agconnect") apply false
}

buildscript {
  dependencies {
    classpath("com.android.tools.build:gradle:8.13.2")
    classpath("com.huawei.agconnect:agcp:1.9.1.304")
  }
}

allprojects {
  repositories {
    google()
    mavenCentral()
    maven(url = "https://jitpack.io")
    maven(url = "https://oss.sonatype.org/content/repositories/snapshots")
    maven(url = "https://developer.huawei.com/repo/")
  }
}
