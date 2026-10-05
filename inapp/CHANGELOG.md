# Changelog

## [4.0.0] – Breaking release

### Breaking changes

#### Distribution
- The SDK has migrated from JitPack to Maven Central.
- The JitPack Maven repository (`https://jitpack.io`) can be removed from your Gradle configuration.

#### SDK entry point & initialization
- **Replaced `InAppMessagesSDK` with `InAppMessages`** as the main public API.
- Initialization is now explicit and standardized:
  - `InAppMessages.initialize(Application, PushSubscriptionProvider)`
  - `InAppMessages.initialize(Application, Config, PushSubscriptionProvider)`
- Initializing an initialized SDK with a different configuration throws
  `IllegalStateException` instead of silently returning the instance configured
  for the previous project.

#### Project switching
- Added `InAppMessages.getInstance().switchProject(config)` to move an initialized
  SDK to another project: the displayed message of the previous project is hidden,
  messages of the new project are fetched and shown on the current route, and
  dismissed messages stay dismissed. The instance returned by `getInstance()` stays
  the same.
- Cached messages are stored per project, so the SDK never falls back to messages
  of another project, even offline. The message cache of previous SDK versions is
  dropped once.

#### Core dependency
- The SDK now depends on a shared internal **core** module.

#### Push notifications integration
- Push subscription handling is no longer embedded in the InAppMessages SDK.
- Optional integration with push notifications is now performed via the provided `PushSubscriptionProvider` interface.
  - A default implementation is provided by the PushPushGo Push Notifications SDK.
  - A custom implementation may be provided by the consumer.
- The push subscription provider can no longer be changed after initialization.
- Removed legacy push integration APIs:
  - `setPushNotificationSubscriber(...)`

#### Message display API
- Removed:
  - `showActiveMessages(...)`
- Introduced explicit message display APIs:
  - `showMessagesOnRoute(route: String)`
  - `showMessagesOnTrigger(trigger: Trigger)`
- Replaced string-based trigger handling with a strongly typed model:
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

- This release **requires code changes** and is not source-compatible with `3.x`.
- Migrate:
  - `InAppMessagesSDK` → `InAppMessages`
  - Legacy push subscription handling → `PushSubscriptionProvider`
  - `showActiveMessages(...)` → route- or trigger-based APIs
  - String-based triggers → `Trigger`
  - JS action handling → `CustomCodeHandler` passed during initialization
