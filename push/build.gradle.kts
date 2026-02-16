import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.ksp)
  alias(libs.plugins.binary.validator)
  alias(libs.plugins.ktlint)
  alias(libs.plugins.maven.publish)
}

group = "com.pushpushgo"
version =
  requireNotNull(property("VERSION")) {
    "VERSION property must be defined"
  }.toString()

android {
  namespace = "com.pushpushgo.sdk.push"
  compileSdk = 36

  defaultConfig {
    minSdk = 28

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
  }

  buildFeatures {
    buildConfig = true
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

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

dependencies {
  api(project(":core"))

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.preference)

  implementation(libs.coroutines.core)
  implementation(libs.coroutines.android)

  implementation(libs.retrofit)
  implementation(libs.retrofit.moshi)

  implementation(platform(libs.okhttp.bom))
  implementation(libs.okhttp.logging)

  ksp(libs.moshi.codegen)
  implementation(libs.moshi.kotlin)
  implementation(libs.moshi.adapters)

  implementation(libs.androidx.work.runtime)
  implementation(libs.androidx.work.gcm)

  compileOnly(platform(libs.firebase.bom))
  compileOnly(libs.firebase.messaging)

  compileOnly(libs.hms.push)

  testImplementation(libs.junit)
  testImplementation(libs.mockk)
  testImplementation(libs.json)
  testImplementation(libs.androidx.test.junit)
  testImplementation(libs.robolectric)

  testImplementation(platform(libs.firebase.bom))
  testImplementation(libs.firebase.messaging)
}

apiValidation {
  ignoredProjects.addAll(listOf("firebase", "hms", "java"))
}

mavenPublishing {
  coordinates(group.toString(), "sdk-push", version.toString())

  pom {
    name.set("PushPushGo PushNotifications SDK")
  }

  configure(
    AndroidSingleVariantLibrary(
      javadocJar = JavadocJar.Empty(),
      sourcesJar = SourcesJar.Sources(),
      variant = "release",
    ),
  )
}
