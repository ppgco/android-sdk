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
  `PushPushGo.getInstance().method()` with `PushNotifications.method()`.
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
- Introduced a unified subscription API: `subscribe()` / `unsubscribe()`. Both are
  synchronous and only store the user's
  choice. The SDK registers or removes the subscriber in the background and
  retries once the network is available. Failures are reported to the error
  callback.
- `subscribe()` throws `IllegalStateException` when notifications are disabled,
  without changing the user's choice.
- Added `getSubscriptionStatus()` returning the sealed `SubscriptionStatus`:
  `Unsubscribed` or `Subscribed(subscriberId, projectId)`. It represents only the backend state
  last confirmed by the SDK, independently of user intent and notification permission.
- Added `isSubscriptionRequested()` for the user's persisted explicit subscribe or
  unsubscribe choice.
- Added `subscriptionStatusFlow` (Kotlin `StateFlow`), which emits the current
  status and every distinct confirmed-state change, including subscriber ID changes.
- Added `setSubscriptionStatusCallback(...)` (Java callback), which receives the current
  status and every distinct confirmed-state change, including subscriber ID changes.
- `isSubscribed()` now reports only confirmed backend state. It remains `true` while
  a failed or in-progress removal retains the subscriber ID.
- As in 3.x, when notifications are disabled, the SDK unsubscribes the device but keeps
  the user's subscription intent (`isSubscriptionRequested()`), and subscribes the device
  again once they are enabled back; only `unsubscribe()` clears the intent. Notification
  settings are now checked whenever an activity of the app is resumed, instead of every
  10 seconds while the app is in the foreground.
- `unsubscribe()` stores the user's choice right away, also without network, and
  regular notifications stop being displayed immediately.
- Updating from SDK 3.x keeps devices that are registered but have no stored
  subscription (registered on push token refresh, or counted as subscribed through
  `defaultIsSubscribed`) subscribed. A stored opt-out stays an opt-out.

#### Async API changes
- **Removed Guava `ListenableFuture` from the public API**.
- The remaining async operations (`switchProject` and the Live Activity
  subscription) use:
  - `suspend` functions for Kotlin consumers
  - `CompletableFuture` (`*Async` methods) for Java consumers

#### Notification model
- Replaced `PPGoNotification` with **`PushPushGoNotification`**.
- All **`PushPushGoNotification`** fields are now non-nullable.

#### Handlers
- Renamed handlers:
  - `setNotificationHandler(...)` → `setNotificationClickHandler(...)`
  - `setOnInvalidProjectIdHandler(...)` → `setInvalidProjectIdHandler(...)`
- Handlers now use named SAM interfaces instead of Kotlin function types:
  - `NotificationClickHandler`
  - `InvalidProjectIdHandler`
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
- The SDK sends beacons in the background to the current subscriber, in order, and
  retries them while offline.
- Send beacons once the device is subscribed (on `SubscriptionStatus.Subscribed` or
  when `isSubscribed()` is `true`).
- Beacons sent after `unsubscribe()` are discarded, and `unsubscribe()` or a project switch
  drops beacons that are still queued.

#### Project migration API
- Removed `migrateToNewProject(...)`.
- Added `PushNotifications.switchProject(config)` (Kotlin) and
  `PushNotifications.switchProjectAsync(config)` (Java) to move an initialized SDK
  to another project. The switch happens locally and does not call the API, so it
  works offline; it only waits for a registration request that is already in
  progress. The previous project's subscriber and Live Activity subscriptions are
  removed in the background. A failed subscription is reported to the error
  callback.
- Initializing an initialized SDK with a different configuration throws
  `IllegalStateException`.
- The SDK remembers which project its persisted subscription belongs to. When it
  is initialized with a configuration of another project (e.g. after an app
  restart), it no longer adopts the previous project's subscriber: that subscriber
  and its Live Activity subscriptions are removed in the background with the
  previous project's credentials (with bounded retries for transient failures),
  local state is cleared, and the user's subscription is kept, so the device
  registers in the new project once notifications are enabled.
- Live Activity pushes of a project other than the initialized one are reported to
  `InvalidProjectIdHandler` instead of being displayed, like regular pushes.

#### Live Activities
- Live Activity APIs moved from `PushPushGo` to the
  `PushNotifications.liveActivities` facade.
- Subscribing to a Live Activity no longer requires a push subscription.

#### Public configuration API
- The following configuration properties are no longer publicly mutable and must be set via explicit setter methods:
  - `notificationHandler`
  - `onInvalidProjectIdHandler`
- `defaultIsSubscribed` and `setDefaultIsSubscribed(...)` were removed from the public API.

#### Core dependency
- The SDK now depends on a shared internal **core** module.
- The SDK provides an implementation of the core module’s `PushSubscriptionProvider` interface, which can be reused by other PushPushGo SDKs to interact with push functionality.

---

### Migration guide

- This release **requires code changes** and is not source-compatible with `3.x`.
- Migrate:
  - `PushPushGo` → `PushNotifications`
  - `PushPushGo.getInstance().method()` → `PushNotifications.method()`
  - `ListenableFuture` → `CompletableFuture`
  - Legacy subscription calls → new unified subscription API
  - `migrateToNewProject(...)` → `switchProject(...)`
  - Direct Live Activity methods → `PushNotifications.liveActivities`
  - `createBeacon()...send()` → `BeaconBuilder()...build()` followed by `sendBeacon(...)`
  - String-based tag strategies (e.g. `"append"`, `"rewrite"`) → `BeaconTagStrategy.APPEND` / `BeaconTagStrategy.REWRITE`
  - Integer-based custom IDs → string-based custom IDs
