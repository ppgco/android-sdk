# Changelog

## [4.0.0] – Breaking release

### Breaking changes

#### SDK entry point & initialization
- **Replaced `InAppMessagesSDK` with `InAppMessages`** as the main public API.
- Initialization is now explicit and standardized:
  - `InAppMessages.initialize(Application, PushSubscriptionProvider)`
  - `InAppMessages.initialize(Application, Config, PushSubscriptionProvider)`

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

- This release **requires code changes** and is not source-compatible with `3.0.3`.
- Migrate:
  - `InAppMessagesSDK` → `InAppMessages`
  - Legacy push subscription handling → `PushSubscriptionProvider`
  - `showActiveMessages(...)` → route- or trigger-based APIs
  - String-based triggers → `Trigger`
  - JS action handling → `CustomCodeHandler` passed during initialization

