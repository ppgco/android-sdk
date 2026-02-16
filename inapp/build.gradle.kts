import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.ksp)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.binary.validator)
  alias(libs.plugins.maven.publish)
}

group = "com.pushpushgo"
version =
  requireNotNull(property("VERSION")) {
    "VERSION property must be defined"
  }.toString()

android {
  namespace = "com.pushpushgo.sdk.inapp"
  compileSdk = 36

  defaultConfig {
    minSdk = 26

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }

  buildFeatures {
    buildConfig = true
    compose = true
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  kotlin {
    compilerOptions {
      jvmTarget.set(JvmTarget.JVM_17)
      languageVersion.set(KotlinVersion.KOTLIN_2_1)
      apiVersion.set(KotlinVersion.KOTLIN_2_1)
    }
  }
}

dependencies {
  api(project(":core"))

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
  implementation(libs.compose.material.icons)
  implementation(libs.compose.runtime)
  implementation(libs.compose.ui.text.fonts) // Google Fonts
  implementation(libs.androidx.emoji2)
  implementation(libs.androidx.emoji2.bundled)

  // Image Loading
  implementation(libs.coil.compose)

  // Serialization
  ksp(libs.moshi.codegen)
  implementation(libs.moshi.kotlin)
  implementation(libs.kotlinx.serialization)

  // Networking
  implementation(libs.retrofit)
  implementation(libs.retrofit.moshi)
  implementation(platform(libs.okhttp.bom))
  implementation(libs.okhttp.logging)

  // Testing
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.coroutines.test)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.espresso.core)
}

apiValidation {
  ignoredProjects.add("sample")
}

mavenPublishing {
  coordinates(group.toString(), "sdk-inapp", version.toString())

  pom {
    name.set("PushPushGo InAppMessages SDK")
  }

  configure(
    AndroidSingleVariantLibrary(
      javadocJar = JavadocJar.Empty(),
      sourcesJar = SourcesJar.Sources(),
      variant = "release",
    ),
  )
}
