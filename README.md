# PushPushGo Android SDK


[![JitPack](https://img.shields.io/jitpack/v/github/ppgco/android-sdk?style=flat-square)](https://jitpack.io/#ppgco/android-sdk)
![GitHub Workflow Status (master)](https://img.shields.io/github/actions/workflow/status/ppgco/android-sdk/test.yml?branch=master&style=flat-square)
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
    implementation 'com.pushpushgo:sdk:2.0.3'

    // or

    // jitpack
    implementation "com.github.ppgco.android-sdk:sdk:2.0.3"

    // GMS
    implementation platform('com.google.firebase:firebase-bom:31.0.1')
    implementation 'com.google.firebase:firebase-messaging'

    // HMS
    implementation 'com.huawei.agconnect:agconnect-core:1.7.0.300'
    implementation 'com.huawei.hms:push:6.5.0.300'
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

and in your main activity:
```xml
<intent-filter>
  <action android:name="APP_PUSH_CLICK" />
  <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```

3. Add to your MainActivity:

in onCreate():
```java
if (savedInstanceState == null) {
    PushPushGo.getInstance().handleBackgroundNotificationClick(intent);
}
```
in onNewIntent():
```java
PushPushGo.getInstance().handleBackgroundNotificationClick(intent);
```
and in AndroidManifest.xml
```xml
<activity
        android:launchMode="singleTop"
```

4. Add to Your Application.onCreate():
```java
PushPushGo.getInstance(this);
```
5. Configuration
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
