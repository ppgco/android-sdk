# PushPushGo PushNotifications SDK

Android SDK for integrating push notifications into your application. Supports both **FCM (Firebase Cloud Messaging)** and **HMS (Huawei Push Kit)**.

## Table of Contents

- [Preparation](#preparation)
- [Installation](#installation)
  - [FCM (Firebase Cloud Messaging)](#fcm-firebase-cloud-messaging)
  - [HMS (Huawei Push Kit)](#hms-huawei-push-kit)
- [Configuration](#configuration)
  - [AndroidManifest.xml](#androidmanifestxml)
  - [Application initialization](#application-initialization)
  - [Notification UI customization](#notification-ui-customization)
- [Handling notification clicks](#handling-notification-clicks)
- [Basic usage](#basic-usage)
  - [Push subscription](#push-subscription)
  - [Beacons, tags, and dynamic groups](#beacons-tags-and-dynamic-groups)

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

Choose installation path depending on your provider.

## FCM (Firebase Cloud Messaging)

### Provider credentials

1. Open **Firebase Console**.
2. Navigate to **Project settings** → **Cloud Messaging**.
3. Click **Manage service accounts**.
4. Select your service account email.
5. Open the **Keys** tab.
6. Click **Add key** → **Create new key**.
7. Choose **JSON** format and download the file.
8. Upload the JSON file in the PushPushGo **FCM** integration section.

### FCM configuration

Place `google-services.json` in the app module root:

```
app/google-services.json
```

### Gradle setup

```toml
# libs.versions.toml

[versions]
firebase-bom = "34.1.0"
firebase-messaging = "25.0.0"
google-gms-google-services = "4.4.3"
pushpushgo-sdk-push = "4.0.0"

[libraries]
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebase-bom" }
firebase-messaging = { module = "com.google.firebase:firebase-messaging", version.ref = "firebase-messaging" }
pushpushgo-sdk-push = { module = "com.pushpushgo:sdk-push", version.ref = "pushpushgo-sdk-push" }

[plugins]
google-gms-google-services = { id = "com.google.gms.google-services", version.ref = "google-gms-google-services" }
```

```kotlin
// app/build.gradle.kts
plugins {
  alias(libs.plugins.google.gms.google.services)
}

dependencies {
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.messaging)
  implementation(libs.pushpushgo.sdk.push)
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

```toml
# libs.versions.toml

[versions]
hms-agconnect = "1.9.1.304"
hms-push = "6.13.0.300"
hms-update = "5.0.2.300"
pushpushgo-sdk-push = "4.0.0"

[libraries]
hms-agconnect = { module = "com.huawei.agconnect:agconnect-core", version.ref = "hms-agconnect" }
hms-push = { module = "com.huawei.hms:push", version.ref = "hms-push" }
hms-update = { module = "com.huawei.hms:update", version.ref = "hms-update" }

pushpushgo-sdk-push = { module = "com.pushpushgo:sdk-push", version.ref = "pushpushgo-sdk-push" }

```

```kotlin
// app/build.gradle.kts
plugins {
  id("com.huawei.agconnect")
}

dependencies {
  implementation(libs.hms.agconnect)
  implementation(libs.hms.push)
  implementation(libs.hms.update)
  implementation(libs.pushpushgo.sdk.push)
}
```

```kotlin
// build.gradle.kts
plugins {
  id("com.huawei.agconnect") apply false
}
```

```kotlin
// settings.gradle.kts
pluginManagement {
  repositories {
    maven(url = "https://developer.huawei.com/repo/")
  }

  resolutionStrategy {
    eachPlugin {
      if (requested.id.id == "com.huawei.agconnect") {
        useModule("com.huawei.agconnect:agcp:1.9.1.304")
      }
    }
  }
}
```

## Configuration

### AndroidManifest.xml

Add your Project ID and API Key inside `<application>`:

```xml
<meta-data
  android:name="com.pushpushgo.projectId"
  android:value="{projectId}" />

<meta-data
  android:name="com.pushpushgo.apiKey"
  android:value="{apiKey}" />
```

### Application initialization

Initialize the SDK in your `Application` class.

#### Automatic (from AndroidManifest.xml)

```kotlin
class MyApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    PushNotifications.initialize(this)
  }
}
```

#### Manual

```kotlin
class MyApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    PushNotifications.initialize(
      application = this,
      config = Config.create(
        projectId = "your-project-id",
        apiKey = "your-api-key",
        isDebug = true
      )
    )
  }
}
```

### Notification UI customization

You may override the following resources in your app:

- Default notification color  
  `@color/pushpushgo_notification_color_default`
- Default notification channel ID  
  `@string/pushpushgo_notification_default_channel_id`
- Default notification channel name  
  `@string/pushpushgo_notification_default_channel_name`
- Small notification icon (per density):
  - `res/drawable-mdpi/ic_stat_pushpushgo_default`
  - `res/drawable-xhdpi/ic_stat_pushpushgo_default`
  - `res/drawable-xxhdpi/ic_stat_pushpushgo_default`

## Handling notification clicks

To ensure correct handling of notification taps:

1. Set your launcher activity to `singleTop`.
2. Add the following `intent-filter`.

   ```xml
   <activity android:launchMode="singleTop">
     <intent-filter>
       <action android:name="APP_PUSH_CLICK" />
       <category android:name="android.intent.category.DEFAULT" />
     </intent-filter>
   </activity>
   ```

3. Forward the intent to the SDK in `onCreate` and `onNewIntent`.

   ```kotlin
   override fun onCreate(savedInstanceState: Bundle?) {
     super.onCreate(savedInstanceState)

     if (savedInstanceState == null) {
       PushNotifications.getInstance().handleBackgroundNotificationClick(intent)
     }
   }

   override fun onNewIntent(intent: Intent) {
   super.onNewIntent(intent)

   PushNotifications.getInstance().handleBackgroundNotificationClick(intent)
   }
   ```

This ensures notification data is processed both when the app is cold-started and when it is already running.

## Additional integration info

- https://pushpushgo.productfruits.help/en/article/web-mobile-push-integration

## Basic usage

### Push subscription

```kotlin
PushNotifications.getInstance().isSubscribed()

PushNotifications.getInstance().subscribe()
PushNotifications.getInstance().unsubscribe()

PushNotifications.getInstance().subscribeNow()
PushNotifications.getInstance().unsubscribeNow()
```

#### Notification permission required

On Android 13 (API 33) and newer, push subscription requires the
`POST_NOTIFICATIONS` permission to be granted by the user.

If the permission is not granted:

- asynchronous methods (`subscribe`, `unsubscribe`) **log an error and fail**
- synchronous methods (`subscribeNow`, `unsubscribeNow`) **throw an exception**

The application is responsible for requesting the permission before calling
any subscription methods.

#### Permission monitoring

The SDK periodically checks whether the notification permission is still granted.
If the permission is revoked while the user is subscribed, the SDK automatically
unsubscribes the user.

### Beacons, tags, and dynamic groups

```kotlin
PushNotifications.getInstance().createBeacon()
  .set("see_invoice", true)
  .setCustomId("CID")
  .appendTag("demo")
  .appendTag("mobile", "platform")
  .send()

PushNotifications.getInstance().createBeacon()
  .assignToGroup("my-group-name")
  .send()

PushNotifications.getInstance().createBeacon()
  .unassignFromGroup("my-group-name")
  .send()
```
