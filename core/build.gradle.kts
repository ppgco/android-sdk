import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.android)
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
  namespace = "com.pushpushgo.sdk.core"
  compileSdk = 36

  defaultConfig {
    minSdk = 28
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

apiValidation {
  ignoredPackages.add("com.pushpushgo.sdk.core.internal")
}

dependencies {
  implementation(libs.androidx.core.ktx)

  testImplementation(libs.junit)
}

mavenPublishing {
  coordinates(group.toString(), "sdk-core", version.toString())

  pom {
    name.set("PushPushGo SDK Core")
  }

  configure(
    AndroidSingleVariantLibrary(
      javadocJar = JavadocJar.Empty(),
      sourcesJar = SourcesJar.Sources(),
      variant = "release",
    ),
  )
}
