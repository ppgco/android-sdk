import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.ktlint)
}

android {
  namespace = "com.pushpushgo.sdk.core"
  compileSdk = 36

  defaultConfig {
    minSdk = 23
  }

  buildTypes {
    release {
      isMinifyEnabled = false
    }
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
  implementation(libs.androidx.core.ktx)

  testImplementation(libs.junit)
}
