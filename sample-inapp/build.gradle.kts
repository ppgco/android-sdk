plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.android")
  id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
  alias(libs.plugins.ktlint)
}

android {
  namespace = "com.pushpushgo.sample"
  compileSdk = 35

  buildFeatures {
    buildConfig = true
  }

  defaultConfig {
    applicationId = "com.pushpushgo.sample"
    minSdk = 26
    targetSdk = 35
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("String", "PPG_PROJECT_ID", "\"<project_id>\"")
    buildConfigField("String", "PPG_API_KEY", "\"<api_key>\"")
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
  }
  buildFeatures {
    compose = true
  }
}

dependencies {
  implementation("androidx.core:core-ktx:1.16.0")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
  implementation("androidx.activity:activity-compose:1.10.1")
  implementation(platform("androidx.compose:compose-bom:2024.09.00"))
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-graphics")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.navigation:navigation-runtime-android:2.9.3")
  implementation("androidx.navigation:navigation-compose:2.9.3")
  implementation(project(":library-inappmessages"))
  testImplementation("junit:junit:4.13.2")
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
  androidTestImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
  androidTestImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-tooling")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
}
