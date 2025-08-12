import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("com.google.devtools.ksp") version "2.2.0-2.0.2"
  id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
  id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
  id("org.jlleitschuh.gradle.ktlint") version "13.0.0"
}

android {
  namespace = "com.pushpushgo.inappmessages"
  compileSdk = 35

  defaultConfig {
    minSdk = 26

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin {
    compilerOptions {
      jvmTarget = JvmTarget.fromTarget("17")
      languageVersion = KotlinVersion.fromVersion("2.1")
      apiVersion = KotlinVersion.fromVersion("2.1")
    }
  }
  buildFeatures {
    compose = true
  }
}

dependencies {
  // Core & Appcompat
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.androidx.preference)

  // Lifecycle & Navigation
  implementation(libs.androidx.lifecycle.runtime)
  implementation(libs.androidx.navigation.runtime)
  implementation(libs.androidx.navigation.common)

  // Compose
  implementation(platform(libs.compose.bom))
  implementation(libs.compose.activity)
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.material3)
  implementation(libs.compose.runtime)
  implementation(libs.compose.ui.text.fonts) // Google Fonts

  // Image Loading
  implementation(libs.coil.compose)

  // Serialization
  implementation(libs.moshi.kotlin)
  ksp(libs.moshi.codegen)
  implementation(libs.kotlinx.serialization)

  // Networking
  implementation(libs.retrofit)
  implementation(libs.retrofit.moshi)
  implementation(platform(libs.okhttp.bom))
  implementation("com.squareup.okhttp3:logging-interceptor")

  // Testing
  testImplementation("junit:junit:4.13.2")
  testImplementation("io.mockk:mockk:1.13.16")
  testImplementation(libs.coroutines.test)
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
