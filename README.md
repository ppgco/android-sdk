# PushPushGo Android SDK

[![JitPack](https://img.shields.io/jitpack/v/github/ppgco/android-sdk?style=flat-square)](https://jitpack.io/#ppgco/android-sdk)
![GitHub Workflow Status (master)](https://img.shields.io/github/workflow/status/ppgco/android-sdk/Tests/master?style=flat-square)
![GitHub tag (latest)](https://img.shields.io/github/v/tag/ppgco/android-sdk?style=flat-square)

## Requirements

- minSdkVersion: 21
- configured GMS or HMS in project

## Instalation

1. Add SDK dependency to Your project
```groovy
// /build.gradle
allprojects {
    repositories {
        // local repo
        mavenLocal()

        // or

        // jitpack
        maven { url 'https://jitpack.io' }
    }
}

// /app/build.gradle
dependencies {
    // local repo
    implementation 'com.pushpushgo:sdk:1.2.0-SNAPSHOT'

    // or

    // jitpack
    implementation "com.github.ppgco.android-sdk:sdk:master-SNAPSHOT"

    // GMS
    implementation platform('com.google.firebase:firebase-bom:28.3.1')
    implementation 'com.google.firebase:firebase-messaging'

    // HMS
    implementation 'com.huawei.agconnect:agconnect-core:1.5.3.200'
    implementation 'com.huawei.hms:push:5.3.0.304'
}
```

2. Add to Your AndroidManifest.xml:
```xml
<meta-data
    android:name="com.pushpushgo.apikey"
    android:value="{apiKey}" />
<meta-data
    android:name="com.pushpushgo.projectId"
    android:value="{projectId}" />
```
3. Add to Your Application.onCreate():
```java
PushPushGo.getInstance(applicationContext);
```
4. Configuration
- Change default notification color: override `@color/pushpushgo_notification_color_default`
- Change default notification channel id: override `@string/pushpushgo_notification_default_channel_id`
- Change default notification channel name: override `@string/pushpushgo_notification_default_channel_name`
- Change default notification icon: override
  - `res/drawable-hdpi/ic_stat_pushpushgo_default`
  - `res/drawable-mdpi/ic_stat_pushpushgo_default`
  - `res/drawable-xhdpi/ic_stat_pushpushgo_default`
  - `res/drawable-xxhdpi/ic_stat_pushpushgo_default`

## Usage

- Register subscriber:
```java
PushPushGo.getInstance().registerSubscriber();
```

- Unregister:
```java
PushPushGo.getInstance().unregisterSubscriber();
```

- Send beacon:
```java
PushPushGo.getInstance().createBeacon()
    .set("see_invoice", true)
    .setCustomId("SEEI")
    .appendTag("demo")
    .appendTag("mobile", "platform")
    .send();
```

## Publishing

To maven local repository:

```sh
$ ./gradlew :library:publishDebugPublicationToMavenLocal      // debug
$ ./gradlew :library:publishReleasePublicationToMavenLocal    // release
$ ./gradlew :library:publishToMavenLocal                      // both
```

## Tests

Run tests in `library` module:

```sh
$ ./gradlew :library:testDebug
```

Generate coverage report:

```sh
$ ./gradlew :library:jacocoTestReport
```

HTML coverage report path: `library/build/reports/jacocoTestReport/html/`
