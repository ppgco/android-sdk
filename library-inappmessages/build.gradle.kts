plugins {
  id("com.android.library")
  id("org.jetbrains.kotlin.android")
  id("com.google.devtools.ksp") version "2.1.21-2.0.1"
  id("org.jetbrains.kotlin.plugin.serialization") version "2.1.21"
  id("org.jetbrains.kotlin.plugin.compose") version "2.1.21"
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
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
    languageVersion = "2.1"
    apiVersion = "2.1"
  }
  buildFeatures {
    compose = true
  }
}

dependencies {
  // Core & Appcompat
  implementation("androidx.core:core-ktx:1.16.0")
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("com.google.android.material:material:1.12.0")
  implementation("androidx.preference:preference-ktx:1.2.1")

  // Lifecycle & Navigation
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
  implementation("androidx.navigation:navigation-runtime-ktx:2.9.3")
  implementation("androidx.navigation:navigation-common-ktx:2.9.3")

  // Compose
  implementation(platform("androidx.compose:compose-bom:2025.07.00"))
  implementation("androidx.activity:activity-compose:1.10.1")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.runtime:runtime")
  implementation("androidx.compose.ui:ui-text-google-fonts:1.8.3") // For Google Fonts

  // Image Loading
  implementation("io.coil-kt:coil-compose:2.6.0")

  // Serialization
  implementation("com.squareup.moshi:moshi-kotlin:1.15.2")
  ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

  // Networking
  implementation("com.squareup.retrofit2:retrofit:2.11.0")
  implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
  implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
  implementation("com.squareup.okhttp3:logging-interceptor")

  // Testing
  testImplementation("junit:junit:4.13.2")
  testImplementation("io.mockk:mockk:1.13.10")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
