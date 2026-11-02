# Changelog

## [4.0.0] - Breaking release

### Breaking changes

#### Distribution
- The SDK has migrated from JitPack to Maven Central.
- The JitPack Maven repository (`https://jitpack.io`) can be removed from your Gradle configuration.
- The package changed from `com.pushpushgo.inappmessages` to `com.pushpushgo.sdk.inapp`.

#### SDK entry point & initialization
- **Replaced `InAppMessagesSDK` with `InAppMessages`** as the main public API.
- `InAppMessages` is a singleton object, like `PushNotifications` in the push SDK:
  there is no `getInstance()`; call `InAppMessages.showMessagesOnRoute(...)`,
  `InAppMessages.showMessagesOnTrigger(...)` and `InAppMessages.switchProject(...)`
  directly. Use `InAppMessages.isInitialized()` to check whether it is initialized.
- Initialization takes a `Config`, or reads it from `AndroidManifest.xml`, instead of
  separate `projectId`, `apiKey`, `debug` and `baseUrl` parameters. The push
  subscription provider and the custom code handler are optional:
  - `InAppMessages.initialize(Application, PushSubscriptionProvider?, CustomCodeHandler?)`
  - `InAppMessages.initialize(Application, Config, PushSubscriptionProvider?, CustomCodeHandler?)`
- Initializing an initialized SDK with a different configuration throws
  `IllegalStateException`.

#### Project switching
- Added `InAppMessages.switchProject(config)` to move an initialized SDK to another project.

#### Core dependency
- The SDK now depends on a shared internal **core** module.

#### Push notifications integration
- Replaced `PushNotificationSubscriber` with `PushSubscriptionProvider`.
- The SDK no longer finds the PushPushGo Push Notifications SDK automatically. Pass
  `PushNotifications.getPushSubscriptionProvider()`, or your own implementation, to
  `initialize(...)`.
- The push subscription provider can no longer be changed after initialization.
- Removed legacy push integration APIs:
  - `setPushNotificationSubscriber(...)`

#### Message display API
- Removed:
  - `showActiveMessages(...)`
- Added `showMessagesOnRoute(route: String)`.
- `showMessagesOnTrigger(key, value)` now takes a `Trigger`:
  - `Trigger.key(String)`
  - `Trigger.keyValue(String, String)`

#### Custom action handling
- Removed:
  - `setJsActionHandler(...)`
- Introduced:
  - `CustomCodeHandler`
- Optional custom action handling can now be provided during SDK initialization.
- Custom action handlers can no longer be changed after initialization.

---

### Migration guide

This release **requires code changes** and is not source-compatible with `3.x`.

| 3.x | 4.0.0 |
|---|---|
| `com.pushpushgo.inappmessages.*` | `com.pushpushgo.sdk.inapp.*` |
| `InAppMessagesSDK.getInstance().method()` | `InAppMessages.method()` |
| `projectId`, `apiKey`, `debug`, `baseUrl` parameters | `Config` or `AndroidManifest.xml` |
| `PushNotificationSubscriber`, `setPushNotificationSubscriber(...)` | `PushSubscriptionProvider` passed to `initialize(...)` |
| `showActiveMessages(route)` | `showMessagesOnRoute(route)` |
| `showMessagesOnTrigger(key, value)` | `showMessagesOnTrigger(Trigger.keyValue(key, value))` |
| `setJsActionHandler(...)` | `CustomCodeHandler` passed to `initialize(...)` |
