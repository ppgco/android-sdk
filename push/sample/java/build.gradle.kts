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

  signingConfigs {
    create("release") {
      storeFile = file("../../../keystore.jks")
      storePassword = "demo123"
      keyAlias = "key0"
      keyPassword = "demo123"
    }

    getByName("debug") {
      storeFile = file("../../../keystore.jks")
      storePassword = "demo123"
      keyAlias = "key0"
      keyPassword = "demo123"
    }
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

      signingConfig = signingConfigs.getByName("release")
    }

    debug {
      signingConfig = signingConfigs.getByName("debug")
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
