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
- [Live Activities](#live-activities)

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

- Android API 26+

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
    maven(url = "https://developer.huawei.com/repo")
  }

  resolutionStrategy {
    eachPlugin {
      if (requested.id.id == "com.huawei.agconnect") {
        useModule("com.huawei.agconnect:agcp:1.9.1.304")
      }
    }
  }
}

dependencyResolutionManagement {
  repositories {
    maven(url = "https://developer.huawei.com/repo")
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

The SDK requires WorkManager to be initialized first. The standard WorkManager setup does this
automatically through AndroidX Startup. If your application disables WorkManager's automatic
initializer, initialize WorkManager manually before calling `PushNotifications.initialize(...)`.

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

#### Switching to another project

If your app works with several PushPushGo projects (e.g. one per country or
brand), move the initialized SDK to another project with `switchProject`:

```kotlin
PushNotifications.switchProject(
  Config.create(
    projectId = "other-project-id",
    apiKey = "other-api-key",
  ),
)
```

Java callers use the asynchronous wrapper:

```java
PushNotifications.switchProjectAsync(Config.create("other-project-id", "other-api-key"))
    .thenRun(() -> { /* switched */ });
```

The switch happens locally right away and does not depend on the network:

- The subscriber of the previous project and its Live Activity subscriptions are
  removed in the background with the previous project's credentials, retried
  until the API is reachable, also after an app restart.
- `switchProject` returns right after the local switch. If the user requested a
  subscription and notifications are enabled, the device is then subscribed to
  the new project in the background: the status is `Unsubscribed` until that
  completes and then `Subscribed(newSubscriberId, newProjectId)` - collect `subscriptionStatusFlow` to know
  when. A failed subscription is reported to the error callback
  (`setErrorCallback`).
- Handlers and the error callback stay in place.
- Pushes the previous project sends until its subscriber is removed go to
  `InvalidProjectIdHandler` instead of being displayed. So do clicks on
  notifications of the previous project that were displayed before the switch.

Switching to the configuration the SDK already uses has no effect. Switch calls
run one after another, so switching twice in a row is safe. If the previous
project is still registering the device, the next switch waits for that request
to finish.

If you use the InAppMessages SDK too, switch it as well with
`InAppMessages.switchProject(config)`.

The SDK does not remember the selected configuration across app restarts. Store
the selected project in your app and initialize the SDK with it on the next
start:

```kotlin
class MyApplication : Application() {
  override fun onCreate() {
    super.onCreate()

    val selectedProject = loadSelectedProjectConfig() // your own storage

    if (selectedProject != null) {
      PushNotifications.initialize(this, selectedProject)
    } else {
      PushNotifications.initialize(this)
    }
  }
}
```

If the SDK is initialized with a different project than the one its persisted
subscription belongs to - e.g. the app initializes from `AndroidManifest.xml`
after a switch - it releases that subscription the same way `switchProject`
does and registers the device in the initialized project once notifications are
enabled. The state stays consistent, but the user ends up back in the
initialized project.

#### Turning the SDK off

`PushNotifications.deinitialize()` (Java: `deinitializeAsync()`) unsubscribes
the device and releases the SDK completely. It removes Live Activities first,
then unsubscribes any confirmed current subscriber and clears persisted project
data, including the subscription request. If any step fails, the SDK remains
initialized and the operation throws an exception. Live Activities already
removed stay removed. Call `initialize(...)` to start the SDK again; it will not
automatically restore the cleared subscription request.

#### Persisted state

The SDK keeps its state (subscriber, push token, the user's subscription and the
project they belong to) in the app's default `SharedPreferences`, under keys
prefixed with `_PushPushGoSDK_`. Do not remove these keys, e.g. by clearing the
default preferences on logout: the SDK would consider the user unsubscribed,
while the device would stay registered in PushPushGo. To turn notifications off,
call `unsubscribe()`. If your app uses
Auto Backup, restoring
the default preferences on another device carries over the subscriber of the
original device.

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
       PushNotifications.handleBackgroundNotificationClick(intent)
     }
   }

   override fun onNewIntent(intent: Intent) {
     super.onNewIntent(intent)

     PushNotifications.handleBackgroundNotificationClick(intent)
   }
   ```

This ensures notification data is processed both when the app is cold-started and when it is already running.

## Additional integration info

- https://pushpushgo.productfruits.help/en/article/web-mobile-push-integration

## Basic usage

### Push subscription

```kotlin
PushNotifications.subscribe()
PushNotifications.unsubscribe()

PushNotifications.getSubscriptionStatus()
PushNotifications.isSubscriptionRequested() // The user's latest explicit choice
PushNotifications.isSubscribed() // Whether a backend subscription is confirmed
```

From Java, use `subscribeAsync()` and `unsubscribeAsync()`. Both return a
`CompletableFuture`.

Call `unsubscribe()` whenever the user turns notifications off in your app,
whatever the current status. The choice is stored right away, also without
network: if the subscriber cannot be removed from PushPushGo at that moment, the
SDK retains its ID as the last confirmed backend state and removes it in the
background.

#### Subscription status

`getSubscriptionStatus()` represents only the backend state last confirmed by
the SDK:

| Status | Meaning |
|---|---|
| `SubscriptionStatus.Unsubscribed` | The SDK has no confirmed backend subscription. |
| `SubscriptionStatus.Subscribed(subscriberId, projectId)` | The SDK has a confirmed backend subscription with these subscriber and project IDs. |

The status is independent of the user's intent and notification permission. For
example, after an offline `unsubscribe()`, `isSubscriptionRequested()` is
`false` while `getSubscriptionStatus()` remains `Subscribed(existingId, projectId)` until
backend removal is confirmed. `getSubscriberId()` agrees with the ID carried by
`Subscribed`.

To follow changes the SDK makes in the background, collect
`subscriptionStatusFlow` (Kotlin). It emits the current status and then every
distinct confirmed-state change, including subscriber ID changes:

```kotlin
// Compose
val status by PushNotifications.subscriptionStatusFlow.collectAsStateWithLifecycle()

// Wait until the device is registered, e.g. before sending a beacon
PushNotifications.subscriptionStatusFlow.first { it is SubscriptionStatus.Subscribed }
```

Java callers can observe future status changes with a callback. Use
`getSubscriptionStatus()` separately when the current status is needed:

```java
PushNotifications.setSubscriptionStatusCallback(status -> {
    if (status instanceof SubscriptionStatus.Subscribed) {
        SubscriptionStatus.Subscribed subscribed = (SubscriptionStatus.Subscribed) status;
        String subscriberId = subscribed.getSubscriberId();
        String projectId = subscribed.getProjectId();
    }
});
```

Pass `null` to remove the callback. For a notifications toggle in your app, use
`isSubscriptionRequested()` for the user's choice and
`areNotificationsEnabled()` for notification permission; do not derive intent
from `isSubscribed()`.

#### Notification permission required

On Android 13 (API 33) and newer, push subscription requires the
`POST_NOTIFICATIONS` permission to be granted by the user.

If the permission is not granted, `subscribe()` (and its Java wrapper,
`subscribeAsync()`) throws an exception without changing
`isSubscriptionRequested()`.

The application is responsible for requesting the permission before calling
`subscribe()`.

#### Permission monitoring

The SDK checks the notification settings whenever an activity of your app is
resumed, so a change made in the system settings is noticed as soon as the user
comes back to the app.

If notifications are disabled while the user requested a subscription, the SDK
unregisters the device but keeps `isSubscriptionRequested()` true. The status
remains `Subscribed(id, projectId)` until backend removal succeeds and then becomes
`Unsubscribed`. Once notifications are enabled again, the device is registered
automatically and the new `Subscribed(id, projectId)` is published. Only `unsubscribe()`
changes the user's intent to false.

### Beacons, tags, and dynamic groups

```kotlin
val beacon =
  BeaconBuilder()
    .set("see_invoice", true)
    .setCustomId("CID")
    .appendTag("demo")
    .appendTag("mobile", "platform")
    .assignToGroup("my-group-name")
    .build()

PushNotifications.sendBeacon(beacon)
```

## Live Activities

Real-time, continuously updated notifications (Android 16+ Live Updates), e.g.
live football match tracking:

```kotlin
val liveActivities = PushNotifications.liveActivities
liveActivities.subscribe("liveNotificationId")
// ...
liveActivities.unsubscribe("liveNotificationId")
```

For the full integration guide (clicks, deep links, analytics, rendering
features) see [LIVE_ACTIVITIES.md](LIVE_ACTIVITIES.md).
