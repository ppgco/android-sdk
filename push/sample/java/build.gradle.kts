plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.google.services)
  alias(libs.plugins.ktlint)
}

android {
  namespace = "com.pushpushgo.sdk.sample.push.java"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.pushpushgo.sdk.sample.push.java"
    minSdk = 23
    targetSdk = 36
    versionCode = 10
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      isDebuggable = false

      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
    }
  }
}

dependencies {
  implementation(project(":push"))
  implementation(libs.timber)

  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.lifecycle.runtime)

  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.messaging)
}
