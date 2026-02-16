# Changelog

## [4.0.0] – Breaking release

### Breaking changes

#### Min SDK Version
- Minimum Android SDK version increased from 23 to 28.

#### SDK entry point & initialization
- **Replaced `PushPushGo` with `PushNotifications`** as the main public API.
- Initialization is now explicit:
  - `PushNotifications.initialize(Application)`
  - `PushNotifications.initialize(Application, Config)`

#### Subscription API redesign
- Removed legacy subscription methods:
  - `createSubscriber()`
  - `registerSubscriber()`
  - `unregisterSubscriber(...)`
- Introduced a unified subscription API:
  - `subscribe()` / `unsubscribe()`
  - `subscribeNow()` / `unsubscribeNow()` (Kotlin coroutines)
  - `subscribeNowFuture()` / `unsubscribeNowFuture()` (Java-friendly `CompletableFuture`)

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

#### Project migration API
- Replaced `migrateToNewProject(...)` with `migrateToNewProjectNow(...)`
- Migration now happens in-place and no longer returns a new SDK instance.

#### Beacon
- Selector assignment is now explicit and type-specific:
  - `set(key, String)`
  - `set(key, Number)`
  - `set(key, Boolean)`
  - `set(key, Char)`
- Introduced `BeaconTagStrategy` enum (`APPEND`, `REWRITE`); string-based strategies are no longer supported.
- Removed `setCustomId(Int?)` method; custom IDs must now be provided using `setCustomId(String?)`.

#### Public configuration API
- The following configuration properties are no longer publicly mutable and must be set via explicit setter methods:
  - `notificationClickHandler`
  - `invalidProjectIdHandler`
  - `defaultIsSubscribed`
  - `customClickIntentFlags`

#### Core dependency
- The SDK now depends on a shared internal **core** module.
- The SDK provides an implementation of the core module’s `PushSubscriptionProvider` interface, which can be reused by other PushPushGo SDKs to interact with push functionality.

---

### Migration guide

- This release **requires code changes** and is not source-compatible with `3.x`.
- Migrate:
  - `PushPushGo` → `PushNotifications`
  - `ListenableFuture` → `CompletableFuture`
  - Legacy subscription calls → new unified subscription API
  - String-based tag strategies (e.g. `"append"`, `"rewrite"`) → `BeaconTagStrategy.APPEND` / `BeaconTagStrategy.REWRITE`
  - Integer-based custom IDs → string-based custom IDs
