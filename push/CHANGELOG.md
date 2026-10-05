# Changelog

## [4.0.0] – Breaking release

### Breaking changes

#### Distribution
- The SDK has migrated from JitPack to Maven Central.
- The JitPack Maven repository (`https://jitpack.io`) can be removed from your Gradle configuration.

#### Min SDK Version
- Minimum Android SDK version increased from 23 to 26.

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
- `isSubscribed()` now returns `true` only while the device is registered in the
  current project. When the notification permission is revoked, the SDK unregisters
  the device but keeps the user's subscription and registers the device again once
  the permission is granted back; only `unsubscribe()` cancels it for good.

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
- Callbacks can be configured before initialization and survive deinitialization
  and project switches.
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
- Added `PushNotifications.switchProject(config)` (Kotlin) and
  `PushNotifications.switchProjectAsync(config)` (Java) to move an initialized SDK
  to another project. The switch happens locally and returns right away, even
  offline: the previous project's subscriber and Live Activity subscriptions are
  removed in the background, and a user who was subscribed is subscribed to the
  new project in the background. A failed subscription is reported to the error
  callback and retried while the app is in the foreground.
- Initializing an initialized SDK with a different configuration still throws
  `IllegalStateException`; its message now points to `switchProject(...)`.
- `PushNotifications.deinitialize()` turns the SDK off completely. It
  removes Live Activities first, then unsubscribes the current
  subscriber and clears its persisted project data. If any step fails, the SDK
  remains initialized and the operation fails. Live Activities already removed
  stay removed.
- The SDK remembers which project its persisted subscription belongs to. When it
  is initialized with a configuration of another project (e.g. after an app
  restart), it no longer adopts the previous project's subscriber: that subscriber
  and its Live Activity subscriptions are removed in the background with the
  previous project's credentials (retried until the API is reachable), local state
  is cleared, and the user's subscription is kept, so the device registers in the
  new project once notifications are enabled.
- Live Activity pushes of a project other than the initialized one are reported to
  `InvalidProjectIdHandler` instead of being displayed, like regular pushes.

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
  - `migrateToNewProject(...)` → `deinitialize()`, then `initialize(...)`, followed by an
    explicit `subscribe()`
  - Direct Live Activity methods → `PushNotifications.liveActivities`
  - `createBeacon()...send()` → `BeaconBuilder()...build()` followed by `sendBeacon(...)`
  - String-based tag strategies (e.g. `"append"`, `"rewrite"`) → `BeaconTagStrategy.APPEND` / `BeaconTagStrategy.REWRITE`
  - Integer-based custom IDs → string-based custom IDs
