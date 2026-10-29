# PushPushGo PushNotifications SDK

Android SDK for integrating push notifications into your application. Supports both **FCM (Firebase Cloud Messaging)** and **HMS (Huawei Push Kit)**.

## Table of Contents

- [Preparation](#preparation)
- [Installation](#installation)
  - [Requirements](#requirements)
  - [FCM (Firebase Cloud Messaging)](#fcm-firebase-cloud-messaging)
  - [HMS (Huawei Push Kit)](#hms-huawei-push-kit)
  - [SDK dependency](#sdk-dependency)
- [Configuration](#configuration)
  - [AndroidManifest.xml](#androidmanifestxml)
  - [Application initialization](#application-initialization)
  - [Notification UI customization](#notification-ui-customization)
- [Handling notification clicks](#handling-notification-clicks)
- [Basic usage](#basic-usage)
  - [Push subscription](#push-subscription)
  - [Beacons, tags, and dynamic groups](#beacons-tags-and-dynamic-groups)
- [Live Activities](#live-activities)
- [Development](#development)

## Preparation

1. Remove other push SDKs or custom FCM/HMS implementations.
2. Connect your app to a push provider.
3. Prepare provider configuration files:
   - FCM: `google-services.json`
   - HMS: `agconnect-services.json`
4. Integrate the provider in the PushPushGo app:
   - Project → Settings → Integration
   - See [FCM](#fcm-firebase-cloud-messaging) or [HMS](#hms-huawei-push-kit) for details
5. Collect your PushPushGo Project ID and API Key.

## Installation

### Requirements

- Android API 23+
- FCM or HMS configured in your app

Choose installation path depending on your provider, then add the [SDK dependency](#sdk-dependency).

## FCM (Firebase Cloud Messaging)

### Provider credentials

1. Open **Firebase Console**.
2. Navigate to **Project settings** → **Cloud Messaging**.
3. Click **Manage service accounts**.
4. Select your service account email.
5. Open the **Keys** tab.
6. Click **Add key** → **Create new key**.
7. Choose **JSON** format and download the file.
8. Upload the JSON file in the PushPushGo **FCM v1** integration section.

### FCM configuration

Place `google-services.json` in the app module root:

```
app/google-services.json
```

### Gradle setup

Groovy DSL

```groovy
// app/build.gradle
dependencies {
    implementation platform('com.google.firebase:firebase-bom:34.1.0')
    implementation 'com.google.firebase:firebase-messaging'
}
```

Kotlin DSL

```toml
# libs.versions.toml

[versions]
firebaseBom = "34.1.0"
firebaseMessaging = "25.0.0"
googleGmsGoogleServices = "4.4.3"

[libraries]
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebaseBom" }
firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging", version.ref = "firebaseMessaging" }

[plugins]
google-gms-google-services = { id = "com.google.gms.google-services", version.ref = "googleGmsGoogleServices" }
```

```kotlin
// app/build.gradle.kts
plugins {
    alias(libs.plugins.google.gms.google.services)
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
}
```

```kotlin
// build.gradle.kts
plugins {
    alias(libs.plugins.google.gms.google.services) apply false
}
```

## HMS (Huawei Push Kit)

### Provider credentials

1. Open **Huawei Developers Console**.
2. Navigate to your project.
3. Open **Project settings**.
4. Collect the required values:
   - `appId`
   - `authUrl`
   - `pushUrl`
   - `appSecret`
5. Provide these credentials in the PushPushGo **HMS** integration section.

### HMS configuration

Place `agconnect-services.json` in the app module root:

```
app/agconnect-services.json
```

### Gradle setup

Groovy DSL

```groovy
// app/build.gradle
dependencies {
    implementation 'com.huawei.agconnect:agconnect-core:1.9.1.303'
    implementation 'com.huawei.hms:push:6.11.0.300'
}
```

Kotlin DSL

```toml
# libs.versions.toml

[versions]
agconnectCore = "1.9.1.303"
hmsPush = "6.11.0.300"

[libraries]
agconnect-core = { module = "com.huawei.agconnect:agconnect-core", version.ref = "agconnectCore" }
hms-push = { module = "com.huawei.hms:push", version.ref = "hmsPush" }
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(libs.agconnect.core)
    implementation(libs.hms.push)
}
```

## SDK dependency

Groovy DSL

```groovy
// build.gradle
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

```groovy
// app/build.gradle
dependencies {
    implementation "com.github.ppgco.android-sdk:sdk:<version>"
}
```

Kotlin DSL

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}
```

```toml
# libs.versions.toml

[versions]
ppgSdk = "3.2.3"

[libraries]
ppg-sdk = { module = "com.github.ppgco.android-sdk:sdk", version.ref = "ppgSdk" }
```

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(libs.ppg.sdk)
}
```

## Configuration

### AndroidManifest.xml

Add your PushPushGo Project ID and API Key inside the `<application>` tag:

```xml
<meta-data
    android:name="com.pushpushgo.apikey"
    android:value="{apiKey}" />
<meta-data
    android:name="com.pushpushgo.projectId"
    android:value="{projectId}" />
```

Set your main activity to `singleTop` and add the notification click intent filter:

```xml
<activity
    android:launchMode="singleTop">

    <intent-filter>
        <action android:name="APP_PUSH_CLICK" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>
</activity>
```

### Application initialization

In `Application.onCreate()`:

```java
PushPushGo.getInstance(this);
```

### Notification UI customization

- Default notification color: override `@color/pushpushgo_notification_color_default`
- Default notification channel id: override `@string/pushpushgo_notification_default_channel_id`
- Default notification channel name: override `@string/pushpushgo_notification_default_channel_name`
- Default notification icon: override
  - `res/drawable-hdpi/ic_stat_pushpushgo_default`
  - `res/drawable-mdpi/ic_stat_pushpushgo_default`
  - `res/drawable-xhdpi/ic_stat_pushpushgo_default`
  - `res/drawable-xxhdpi/ic_stat_pushpushgo_default`

## Handling notification clicks

Forward click intents to the SDK in your main activity.

In `onCreate()`:

```java
if (savedInstanceState == null) {
    PushPushGo.getInstance().handleBackgroundNotificationClick(intent);
}
```

In `onNewIntent()`:

```java
PushPushGo.getInstance().handleBackgroundNotificationClick(intent);
```

For custom-scheme deep links, App Links, overriding the click handler and
action buttons see [DEEPLINKS.md](DEEPLINKS.md).

## Basic usage

### Push subscription

```java
PushPushGo.getInstance().registerSubscriber();
PushPushGo.getInstance().unregisterSubscriber();
```

### Beacons, tags, and dynamic groups

```java
PushPushGo.getInstance().createBeacon()
    .set("see_invoice", true)
    .setCustomId("SEEI")
    .appendTag("demo")
    .appendTag("mobile", "platform")
    .send();
```

Assign subscriber to a dynamic group:

```java
PushPushGo.getInstance().createBeacon()
    .assignToGroup("my-group-name")
    .send();
```

Unassign subscriber from a dynamic group:

```java
PushPushGo.getInstance().createBeacon()
    .unassignFromGroup("my-group-name")
    .send();
```

## Live Activities

Real-time, continuously updated notifications (Android 16+ Live Updates), e.g.
live football match tracking:

```java
PushPushGo.getInstance().subscribeToLiveActivity("liveNotificationId");
// ...
PushPushGo.getInstance().unsubscribeFromLiveActivity("liveNotificationId");
```

For the full integration guide (clicks, deep links, analytics, rendering
features) see [LIVE_ACTIVITIES.md](LIVE_ACTIVITIES.md).

## Development

Publish to the local Maven repository:

```sh
./gradlew :library:publishToMavenLocal
```

Run tests:

```sh
./gradlew :library:testDebug
```

Generate coverage report:

```sh
./gradlew :library:jacocoTestReport
```

HTML coverage report path: `library/build/reports/jacocoTestReport/html/`
