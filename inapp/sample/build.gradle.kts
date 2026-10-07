import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val localProperties =
  Properties().apply {
    rootProject
      .file("local.properties")
      .takeIf { it.exists() }
      ?.inputStream()
      ?.use { load(it) }
  }

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ktlint)
}

android {
  namespace = "com.pushpushgo.sdk.sample.inapp"
  compileSdk = 36

  buildFeatures {
    buildConfig = true
  }

  defaultConfig {
    applicationId = "com.pushpushgo.sdk.sample.inapp"
    minSdk = 28
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    manifestPlaceholders["PPG_PROJECT_ID"] = localProperties.getProperty("ppg.projectId", "")
    manifestPlaceholders["PPG_API_KEY"] = localProperties.getProperty("ppg.apiKey", "")
    manifestPlaceholders["PPG_API_URL"] = localProperties.getProperty("ppg.apiUrl", "https://api.pushpushgo.com")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlin {
    compilerOptions {
      jvmTarget = JvmTarget.fromTarget("11")
    }
  }
  buildFeatures {
    compose = true
  }
}

dependencies {
  implementation(project(":inapp"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime)
  implementation(libs.compose.activity)

  implementation(platform(libs.compose.bom))
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.graphics)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.material3)

  implementation(libs.androidx.navigation.runtime)
  implementation(libs.androidx.navigation.compose)

  testImplementation(libs.junit)

  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.espresso.core)
  androidTestImplementation(platform(libs.compose.bom))
  androidTestImplementation(libs.compose.ui.test.junit4)

  debugImplementation(libs.compose.ui.tooling)
  debugImplementation(libs.compose.ui.test.manifest)
}
