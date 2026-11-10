# Changelog

## [4.0.0] - Breaking release

### Breaking changes

#### Distribution
- The SDK has migrated from JitPack to Maven Central.
- The JitPack Maven repository (`https://jitpack.io`) can be removed from your Gradle configuration.
- The package changed from `com.pushpushgo.sdk` to `com.pushpushgo.sdk.push`.

#### Min SDK Version
- Minimum Android SDK version increased from 23 to 26.

#### SDK entry point & initialization
- **Replaced `PushPushGo` with `PushNotifications`** as the main public API.
- `PushNotifications` is now a process-wide object; replace
  `PushPushGo.getInstance().method()` with `PushNotifications.method()`.
- Initialization is now explicit:
  - `PushNotifications.initialize(Application)`
  - `PushNotifications.initialize(Application, Config)`
- Initializing an initialized SDK with a different configuration throws
  `IllegalStateException`.
- Requires WorkManager, which AndroidX Startup initializes automatically unless
  the application disables its initializer.

#### Subscription API redesign
- Removed legacy subscription methods:
  - `createSubscriber()`
  - `registerSubscriber()`
  - `unregisterSubscriber(...)`
- Introduced a unified subscription API: `subscribe()` / `unsubscribe()`. Both are
  synchronous; registration happens in the background and failures are reported
  to the error callback.
- `subscribe()` throws `IllegalStateException` when notifications are disabled.
- Added `getSubscriptionStatus()` returning the sealed `SubscriptionStatus`:
  `Unsubscribed` or `Subscribed(subscriberId, projectId)`.
- Added `isSubscriptionRequested()` for the user's persisted subscribe or
  unsubscribe choice.
- Observe subscription status changes with `subscriptionStatusFlow` (Kotlin) or
  `setSubscriptionStatusCallback(...)` (Java).
- `isSubscribed()` now reports only the subscription confirmed by the backend.
- `defaultIsSubscribed` and `setDefaultIsSubscribed(...)` were removed.
- Existing 3.x subscriptions are preserved on upgrade.

#### Async API changes
- **Removed Guava `ListenableFuture` from the public API**.
- `getPushToken()` is now synchronous and returns `String?`: the token the SDK has
  stored, or `null` if none yet.
- The remaining async operations (`switchProject` and the Live Activity
  subscription) use:
  - `suspend` functions for Kotlin consumers
  - `CompletableFuture` (`*Async` methods) for Java consumers

#### Notification model
- Replaced `PPGoNotification` with **`PushPushGoNotification`**.
- All **`PushPushGoNotification`** fields are now non-nullable.
- Renamed `isPPGoPush(...)` to `isPushPushGoNotification(...)`.

#### Handlers
- Renamed handlers:
  - `setNotificationHandler(...)` → `setNotificationClickHandler(...)`
  - `setOnInvalidProjectIdHandler(...)` → `setInvalidProjectIdHandler(...)`
- Handlers now use named SAM interfaces instead of Kotlin function types:
  - `NotificationClickHandler`
  - `InvalidProjectIdHandler`
- The `notificationHandler` and `onInvalidProjectIdHandler` properties are no
  longer public; use the setters.
- Added `setErrorCallback(PushNotificationsErrorCallback)` to observe errors of the
  work the SDK does in the background.
- Callbacks can be configured before initialization and survive project switches.
- Passing `null` to a callback setter restores its default behavior.

#### Beacon
- Selector assignment is now explicit and type-specific:
  - `set(key, String)`
  - `set(key, Number)`
  - `set(key, Boolean)`
  - `set(key, Char)`
- Introduced `BeaconTagStrategy` enum (`APPEND`, `REWRITE`); string-based strategies are no longer supported.
- Removed `setCustomId(Int?)` method; custom IDs must now be provided using `setCustomId(String?)`.
- Replace `PushPushGo.getInstance().createBeacon()...send()` with
  `BeaconBuilder()...build()` followed by `PushNotifications.sendBeacon(...)`.
- Send beacons once the device is subscribed (on `SubscriptionStatus.Subscribed` or
  when `isSubscribed()` is `true`).

#### Project migration API
- Removed `migrateToNewProject(...)`.
- Added `PushNotifications.switchProject(config)` (Kotlin) and
  `PushNotifications.switchProjectAsync(config)` (Java) to move an initialized SDK
  to another project. The switch does not need network access: the device
  registers in the new project and the previous project's subscriber is removed
  once the network is available.
- Initializing the SDK with another project's configuration (e.g. after an app
  update) moves the device to the new project instead of reusing the previous
  project's subscriber.
- Live Activity pushes of a project other than the initialized one are reported to
  `InvalidProjectIdHandler` instead of being displayed, like regular pushes.

#### Live Activities
- Live Activity APIs moved from `PushPushGo` to the
  `PushNotifications.liveActivities` facade.
- Subscribing to a Live Activity requires notification permission instead of a
  registered push subscriber.
- `LiveActivity` exposes only `id` and `template`. It is no longer a data class.
  Template-specific models (`FootballMatchConfiguration`, `FootballMatchContent`,
  `FootballMatchLiveData`, `FootballMatchAndroidDesign`, `HotMessage`,
  `LiveActivityAction`, `LiveActivityActionType`, `LiveActivityColor`,
  `LiveActivityColorSet`, `MatchPhase`, `LiveActivityEvent`), `LiveActivityStatus`
  and `LiveActivityDismissReceiver` are no longer public.
- `LiveActivity` and `LiveActivityTemplate` moved from
  `com.pushpushgo.sdk.push.liveactivity.data` to `com.pushpushgo.sdk.push.liveactivity`.
- `LiveActivityTemplate` is a class with constants instead of an enum.
- Live Activity clicks are handled by
  `PushNotifications.handleBackgroundNotificationClick(...)`. For custom routing use
  `setNotificationClickHandler(...)`.
- Removed `isLiveActivityActive(...)`, `getLiveActivitySubscriberId(...)` and
  `simulateLiveActivityPush(...)`. `liveActivities.subscribe(...)` no longer returns
  the Live Activity subscriber ID; the SDK keeps it internally.
- `liveActivities.unsubscribe(...)` does nothing if the device is not subscribed.
- Pushes with a missing template, or a template not supported by the SDK version,
  are ignored instead of being rendered as a football match.

#### InAppMessages integration
- Removed `PushSubscriptionBridgeManager` and `PushPushGoSubscriptionBridgeManager`.
- Added `PushNotifications.getPushSubscriptionProvider()`; pass it to the
  InAppMessages SDK to connect both SDKs.

#### Core dependency
- The SDK now depends on a shared internal **core** module.

---

### Migration guide

This release **requires code changes** and is not source-compatible with `3.x`.

| 3.x | 4.0.0 |
|---|---|
| `com.pushpushgo.sdk.*` | `com.pushpushgo.sdk.push.*` |
| `PushPushGo.getInstance().method()` | `PushNotifications.method()` |
| `createSubscriber()` / `registerSubscriber()` | `subscribe()` |
| `unregisterSubscriber(...)` | `unsubscribe()` |
| `defaultIsSubscribed` | removed |
| `getPushToken()` (`ListenableFuture<String>`) | `getPushToken()` (`String?`) |
| `migrateToNewProject(...)` | `switchProject(...)` / `switchProjectAsync(...)` |
| `isPPGoPush(...)` | `isPushPushGoNotification(...)` |
| `PushPushGoSubscriptionBridgeManager` | `getPushSubscriptionProvider()` |
| `isLiveActivitiesSupported()` | `liveActivities.isSupported()` |
| `subscribeToLiveActivity(id)` | `liveActivities.subscribe(id)` / `subscribeAsync(id)`, without a return value |
| `unsubscribeFromLiveActivity(id)` | `liveActivities.unsubscribe(id)` / `unsubscribeAsync(id)` |
| `getActiveLiveActivities()` | `liveActivities.getActiveLiveActivities()` |
| `handleLiveActivityClick(...)` | `handleBackgroundNotificationClick(...)` |
| `isLiveActivityActive(id)` | `liveActivities.getActiveLiveActivities()` |
| `getLiveActivitySubscriberId(id)` | removed |
| `LiveActivity.configuration`, `liveData` and other template data | removed; use `id` and `template` |
| `PPGoNotification` | `PushPushGoNotification` |
| `setNotificationHandler(...)` | `setNotificationClickHandler(...)` |
| `setOnInvalidProjectIdHandler(...)` | `setInvalidProjectIdHandler(...)` |
| `createBeacon()...send()` | `BeaconBuilder()...build()` + `sendBeacon(...)` |
| `appendTag(..., strategy = "append" / "rewrite")` | `BeaconTagStrategy.APPEND` / `BeaconTagStrategy.REWRITE` |
| `setCustomId(Int?)` | `setCustomId(String?)` |
