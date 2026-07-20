# Changelog

## [4.0.0] – Breaking release

### Breaking changes

#### Min SDK Version
- Minimum Android SDK version increased from 23 to 28.

#### SDK entry point & initialization
- **Replaced `PushPushGo` with `PushNotifications`** as the main public API.
- `PushNotifications` is now a process-wide object; replace
  `PushNotifications.getInstance().method()` with `PushNotifications.method()`.
- Initialization is now explicit:
  - `PushNotifications.initialize(Application)`
  - `PushNotifications.initialize(Application, Config)`
- WorkManager must be initialized before the SDK. AndroidX Startup handles this automatically unless
  the application disables WorkManager's automatic initializer.

#### Subscription API redesign
- Removed legacy subscription methods:
  - `createSubscriber()`
  - `registerSubscriber()`
  - `unregisterSubscriber(...)`
- Introduced a unified subscription API:
  - `subscribe()` / `unsubscribe()` (Kotlin coroutines)
  - `subscribeAsync()` / `unsubscribeAsync()` (Java-friendly `CompletableFuture`)
- Removed `subscribeNow()`, `unsubscribeNow()`, `subscribeNowFuture()`, and
  `unsubscribeNowFuture()`; use the unified methods above.

#### Async API changes
- **Removed Guava `ListenableFuture` from the public API**.
- All async operations now use:
  - `suspend` functions for Kotlin consumers
  - `CompletableFuture` for Java consumers

#### Notification model
- Replaced `PPGoNotification` with **`PushPushGoNotification`**.
- All **`PushPushGoNotification`** fields are now non-nullable.

#### Handlers
- Renamed handlers:
  - `setNotificationHandler(...)` → `setNotificationClickHandler(...)`
  - `setOnInvalidProjectIdHandler(...)` → `setInvalidProjectIdHandler(...)`
- Handler and error callback APIs now use named SAM interfaces:
  - `NotificationClickHandler`
  - `InvalidProjectIdHandler`
  - `PushNotificationsErrorCallback`
- Callbacks can be configured before initialization and survive deinitialization.
- Passing `null` to a callback setter restores its default behavior.

#### Beacon
- Selector assignment is now explicit and type-specific:
  - `set(key, String)`
  - `set(key, Number)`
  - `set(key, Boolean)`
  - `set(key, Char)`
- Introduced `BeaconTagStrategy` enum (`APPEND`, `REWRITE`); string-based strategies are no longer supported.
- Removed `setCustomId(Int?)` method; custom IDs must now be provided using `setCustomId(String?)`.
- Replace `PushNotifications.getInstance().createBeacon()...send()` with
  `BeaconBuilder()...build()` followed by `PushNotifications.sendBeacon(...)` or
  `PushNotifications.sendBeaconAsync(...)`.

#### Project migration API
- Removed `migrateToNewProject(...)`.
- To switch an initialized SDK to another explicit configuration, first call the
  suspending `PushNotifications.deinitialize()` method and, after it completes,
  call `PushNotifications.initialize(application, newConfig)`.
- Java callers should wait for `PushNotifications.deinitializeAsync()` before
  calling `initialize(...)`.
- Deinitialization removes Live Activities first, then unsubscribes the current
  subscriber and clears its persisted project data. If any step fails, the SDK
  remains initialized and the operation fails. Live Activities already removed
  stay removed.

#### Live Activities
- Live Activity APIs moved from `PushNotifications` to the
  `PushNotifications.liveActivities` facade.

#### Public configuration API
- The following configuration properties are no longer publicly mutable and must be set via explicit setter methods:
  - `notificationClickHandler`
  - `invalidProjectIdHandler`
  - `customClickIntentFlags`
- `defaultIsSubscribed` and `setDefaultIsSubscribed(...)` were removed from the public API.

#### Core dependency
- The SDK now depends on a shared internal **core** module.
- The SDK provides an implementation of the core module’s `PushSubscriptionProvider` interface, which can be reused by other PushPushGo SDKs to interact with push functionality.

---

### Migration guide

- This release **requires code changes** and is not source-compatible with `3.x`.
- Migrate:
  - `PushPushGo` → `PushNotifications`
  - `PushNotifications.getInstance().method()` → `PushNotifications.method()`
  - `ListenableFuture` → `CompletableFuture`
  - Legacy subscription calls → new unified subscription API
  - `migrateToNewProject(...)` → `deinitialize()` followed by `initialize(...)`
  - Direct Live Activity methods → `PushNotifications.liveActivities`
  - `createBeacon()...send()` → `BeaconBuilder()...build()` followed by `sendBeacon(...)`
  - String-based tag strategies (e.g. `"append"`, `"rewrite"`) → `BeaconTagStrategy.APPEND` / `BeaconTagStrategy.REWRITE`
  - Integer-based custom IDs → string-based custom IDs
