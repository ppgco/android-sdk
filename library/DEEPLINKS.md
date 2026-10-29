# Deep links in PushPushGo Android SDK

This document explains how push notifications sent via PushPushGo handle URLs
(web links and custom-scheme deep links) on Android, and what the host
application must do to route the user to a specific screen after a
notification click.

> Applies to the `com.pushpushgo:sdk` (module `library/`).

---

## Table of contents

- [How the SDK attaches a URL to a notification](#how-the-sdk-attaches-a-url-to-a-notification)
- [Payload reference](#payload-reference)
- [Required app-side integration](#required-app-side-integration)
- [Default behavior (opening the URL)](#default-behavior-opening-the-url)
- [Custom-scheme deep links (e.g. `myapp://product/123`)](#custom-scheme-deep-links-eg-myappproduct123)
- [App Links (verified `https://` deep links)](#app-links-verified-https-deep-links)
- [Overriding the click handler](#overriding-the-click-handler)
- [Custom intent flags](#custom-intent-flags)
- [Action buttons](#action-buttons)
- [Reading notification data without opening the URL](#reading-notification-data-without-opening-the-url)
- [Troubleshooting](#troubleshooting)

---

## How the SDK attaches a URL to a notification

Every PushPushGo push payload can carry a URL. When a notification is
displayed, the SDK builds a `PendingIntent` that:

1. Targets the host app's **launcher activity**
   (`packageManager.getLaunchIntentForPackage(...)`).
2. Stores metadata (campaign, subscriber, project, button, notification id)
   plus the URL as extras.

Key extra keys (declared in
`com.pushpushgo.sdk.push.PushNotificationDelegate`):

| Key                | Constant                 | Description                                |
|--------------------|--------------------------|--------------------------------------------|
| `link`             | `LINK_EXTRA`             | URL to open (from `redirectLink` or action)|
| `notification_id`  | `NOTIFICATION_ID_EXTRA`  | Local id of the posted notification        |
| `campaign`         | `CAMPAIGN_ID_EXTRA`      | Campaign id (for analytics events)         |
| `button`           | `BUTTON_ID_EXTRA`        | `0` = body click, `1..N` = action buttons  |
| `project`          | `PROJECT_ID_EXTRA`       | Project id, used to validate the click     |
| `subscriber`       | `SUBSCRIBER_ID_EXTRA`    | Subscriber id                              |

The full raw payload (`notification`, `actions`, `redirectLink`, `image`,
`icon`, ...) is also passed as an extra named `notification` so that the SDK
can reconstruct the `PushPushNotification` object via
`deserializeNotificationData(intent.extras)`.

---

## Payload reference

The URL is driven by two fields in the PushPushGo payload:

- `redirectLink` — URL opened when the user taps the notification **body**.
- `actions[i].link` — URL opened when the user taps action **button `i+1`**.

Both fields support any string that `android.content.Intent.parseUri(uri, 0)`
can understand, including:

- `https://example.com/product/42`
- `myapp://product/42`
- `intent://...#Intent;...;end`

---

## Required app-side integration

For deep links delivered from the background to work, the host app must:

### 1. Declare the launcher activity as `singleTop`

Otherwise Android creates a new instance of your activity on every click and
your existing navigation stack is lost.

```xml
<!-- AndroidManifest.xml -->
<activity
    android:name=".MainActivity"
    android:launchMode="singleTop"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

### 2. Forward click intents to the SDK

Call `PushPushGo.getInstance().handleBackgroundNotificationClick(intent)` in
both `onCreate()` and `onNewIntent()` of your launcher activity:

```kotlin
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Handle the case when the app was launched from a notification
        PushPushGo.getInstance().handleBackgroundNotificationClick(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle clicks that arrive while the activity is already running
        setIntent(intent)
        PushPushGo.getInstance().handleBackgroundNotificationClick(intent)
    }
}
```

`handleBackgroundNotificationClick` will:

1. Validate the `project` extra against the configured project id. If they do
   not match, `onInvalidProjectIdHandler` is invoked and nothing else happens.
2. Cancel the local system notification.
3. Dispatch the URL to `notificationHandler(context, url, overrideFlags)`.
4. Send a `CLICKED` analytics event (body click or action-button click
   depending on the `button` extra).
5. Clear the `project` extra so the same click is not processed twice.

---

## Default behavior (opening the URL)

The SDK's default `notificationHandler` is:

```kotlin
// com.pushpushgo.sdk.PushPushGo
var notificationHandler: NotificationHandler = { context, url, overrideFlags ->
    handleNotificationLinkClick(context, url, overrideFlags)
}
```

which boils down to:

```kotlin
// com.pushpushgo.sdk.push.NotificationUtils
internal fun handleNotificationLinkClick(context, uri, overrideFlags) {
    Intent.parseUri(uri, 0).let {
        it.addFlags(overrideFlags)
        try {
            context.startActivity(it)
        } catch (e: ActivityNotFoundException) {
            // Fallback: show the URL as a toast
        }
    }
}
```

The default `overrideFlags` passed by `handleBackgroundNotificationClick` is
`Intent.FLAG_ACTIVITY_NEW_TASK`.

Because the URL is parsed with `Intent.parseUri(...)`, **any** scheme/host
combination can be routed as long as some activity on the device declares a
matching `<intent-filter>`. This is how both `https://` links and custom
scheme deep links are supported out of the box.

---

## Custom-scheme deep links (e.g. `myapp://product/123`)

1. The backend sends a payload with `redirectLink` (or `actions[i].link`) set
   to your custom URI, e.g. `myapp://product/123`.
2. Declare an `<intent-filter>` on the target activity:

```xml
<activity
    android:name=".ProductActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="myapp"
            android:host="product" />
    </intent-filter>
</activity>
```

3. Read the URI from the activity:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val data: Uri? = intent.data
    val productId = data?.lastPathSegment
    // ...
}
```

When the user taps the notification, the SDK calls `startActivity(Intent)`,
Android resolves the `VIEW` intent to `ProductActivity`, and you receive the
URI in `intent.data`.

> Your app's **launcher activity** still needs `singleTop` and the
> `handleBackgroundNotificationClick(intent)` call from the previous section.
> The launcher activity is the entry point — the custom-scheme activity is
> launched by the default click handler via `startActivity`.

---

## App Links (verified `https://` deep links)

If you want `https://example.com/product/42` delivered from a push to open
**your app** (rather than the browser), configure standard Android
[App Links](https://developer.android.com/training/app-links):

1. Add an `<intent-filter>` with `android:autoVerify="true"` to the target
   activity:

```xml
<intent-filter android:autoVerify="true">
    <action android:name="android.intent.action.VIEW" />
    <category android:name="android.intent.category.DEFAULT" />
    <category android:name="android.intent.category.BROWSABLE" />
    <data android:scheme="https" android:host="example.com" />
</intent-filter>
```

2. Host a `/.well-known/assetlinks.json` file on `https://example.com`
   containing your app's SHA-256 signing fingerprint.
3. Send `https://example.com/...` in `redirectLink`.

Note: this is a standard Android platform feature and is orthogonal to the
PushPushGo SDK — the SDK simply calls `startActivity(Intent.parseUri(...))`
and Android does the rest.

---

## Overriding the click handler

You can fully replace the default behavior (e.g. to route through your own
navigation component, or to suppress URL opening):

```kotlin
PushPushGo.getInstance().notificationHandler = { context, url, overrideFlags ->
    // Example: route through your own deep-link dispatcher
    MyDeepLinkRouter.handle(context, url)
}
```

The `NotificationHandler` type is:

```kotlin
typealias NotificationHandler = (context: Context, url: String, overrideFlags: Int) -> Unit
```

This handler is called both by `handleBackgroundNotificationClick` and by the
foreground click dispatcher used by the SDK.

---

## Custom intent flags

If you need a different set of `Intent.FLAG_*` than the default
`FLAG_ACTIVITY_NEW_TASK`, persist them through the SDK:

```kotlin
PushPushGo.getInstance().setCustomClickIntentFlags(
    Intent.FLAG_ACTIVITY_NEW_TASK or
    Intent.FLAG_ACTIVITY_CLEAR_TOP or
    Intent.FLAG_ACTIVITY_SINGLE_TOP,
)
```

These flags are applied when the SDK builds the launcher `PendingIntent`
and can be read back via `getCustomClickIntentFlags()`.

---

## Action buttons

When the payload contains an `actions` array:

```json
{
  "redirectLink": "https://example.com/offer",
  "actions": [
    { "title": "View offer", "link": "https://example.com/offer" },
    { "title": "Dismiss",    "link": "" }
  ]
}
```

the SDK renders one `NotificationCompat.Action` per entry. Clicking a button
produces the same flow as a body click, with the following differences:

- The `button` extra is set to the 1-based index (`1` for the first button,
  `2` for the second, ...).
- The URL dispatched to `notificationHandler` comes from `actions[i].link`
  (via the `link` extra), not from `redirectLink`.

The `button` id is reported in the `CLICKED` analytics event.

---

## Reading notification data without opening the URL

If you only need to inspect the payload (e.g. to implement your own routing),
use `getNotificationDetails`:

```kotlin
val ppgoNotification: PPGoNotification? =
    PushPushGo.getInstance().getNotificationDetails(notificationDataMap)
```

or, inside an activity, reconstruct the full payload directly from the
intent extras — the SDK stores the raw JSON under the `notification` key.

---

## Troubleshooting

- **My activity is recreated on every click.** Missing
  `android:launchMode="singleTop"` on the launcher activity.
- **`onNewIntent` is never called.** Same reason, or you forgot to
  override `onNewIntent()` in the launcher activity.
- **Deep link opens the browser instead of my app.** No activity with a
  matching `<intent-filter>` was found. For `https://` links this usually
  means App Links verification has not succeeded — check
  `adb shell pm get-app-links <package>`.
- **Click is ignored entirely.** `project` extra did not match the project
  id the SDK was initialized with. Check
  `PushPushGo.getInstance().getProjectId()` and the `project` field in the
  payload.
- **Toast with the URL appears instead of the app opening.** The default
  handler caught an `ActivityNotFoundException`. Either no activity handles
  the scheme, or the URI string is malformed for `Intent.parseUri`.
