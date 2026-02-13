import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.binary.validator) apply false
  alias(libs.plugins.ktlint) apply false
  alias(libs.plugins.google.services) apply false
  alias(libs.plugins.maven.publish) apply false
  id("com.huawei.agconnect") apply false
}

buildscript {
  dependencies {
    classpath("com.android.tools.build:gradle:8.13.2")
    classpath("com.huawei.agconnect:agcp:1.9.1.304")
  }
}

allprojects {
  repositories {
    google()
    mavenCentral()
    maven(url = "https://developer.huawei.com/repo/")
  }
}

subprojects {
  plugins.withId("com.vanniktech.maven.publish") {
    extensions.configure<MavenPublishBaseExtension>("mavenPublishing") {
      pom {
        url.set("https://github.com/ppgco/android-sdk")
        description.set("PushPushGo Android SDK")
        inceptionYear.set("2019")

        licenses {
          license {
            name.set("MIT")
            url.set("https://github.com/ppgco/android-sdk/blob/master/LICENSE")
          }
        }

        developers {
          developer {
            name.set("PushPushGo")
            email.set("mobile-dev@pushpushgo.com")
          }
        }

        scm {
          url.set("https://github.com/ppgco/android-sdk")
          connection.set("scm:git:git://github.com/ppgco/android-sdk.git")
          developerConnection.set("scm:git:ssh://github.com/ppgco/android-sdk.git")
        }
      }

      if (hasProperty("RELEASE")) {
        publishToMavenCentral()
        signAllPublications()
      }
    }
  }
}
