# Live Activities (Android Live Updates)

PushPushGo Live Activities bring real-time, continuously updated notifications to
Android — the equivalent of iOS Live Activities. The SDK renders a live
notification using the Android 16 `ProgressStyle` (Live Updates) template and
keeps it up to date from backend pushes, with no polling and no foreground
service in your app.

The first available template is **`FOOTBALL_MATCH_TRACKING`**: a football match
tracker with team crests, live score, match phase, a per-second game clock and a
progress bar with break indicators.

## Requirements

| Requirement | Notes |
|---|---|
| Android 16 (API 36) device | On older devices Live Activity pushes are ignored — check `liveActivities.isSupported()` |
| PushNotifications SDK integrated | Push notifications must already work (see the [README](README.md)) |
| Registered subscriber | Call `subscribe()` / `subscribeNow()` before subscribing to a Live Activity |
| `POST_NOTIFICATIONS` granted | Standard runtime notification permission |
| FCM | Live Activity pushes are delivered as FCM data messages |

The SDK's manifest already declares `android.permission.POST_PROMOTED_NOTIFICATIONS`
(required for promoted Live Updates) — you don't need to add anything.

## How it works

```
PPG dashboard / API          your backend                     mobile SDK
─────────────────────        ─────────────────────────        ───────────────────────────────
create & submit live    ──►  update score/phase        ──►    FCM data push (start/update/end)
notification campaign        (PUT /live-data),                 │
                             publish hot messages              ▼
                                                              parses payload, renders & updates
              ▲                                               ProgressStyle notification,
              │                                               reports analytics events
   device subscribes to the live notification  ◄──────────────┘
   (`liveActivities.subscribe` — done by the SDK)
```

1. A live notification campaign is created and submitted on the PushPushGo side.
2. The device **subscribes** to that campaign with `liveActivities.subscribe(id)`.
3. The backend sends `start` / `update` / `end` data pushes; the SDK renders and
   updates the notification. Score and phase changes are **server-side** — the
   app never drives the match state.
4. If the device subscribes **after** the activity already started, the SDK
   automatically fetches the current state and renders it immediately
   (late-join catch-up; applies only while the campaign is `ONGOING`).

## Integration

### 1. Subscribe / unsubscribe

```kotlin
val ppg = PushNotifications.getInstance()
val liveActivities = ppg.liveActivities

// The device must already be a registered push subscriber.
if (liveActivities.isSupported()) {
    val laSubscriberId = liveActivities.subscribe("<liveNotificationId>")
    // subscribed; laSubscriberId is also persisted by the SDK
}

// Later:
liveActivities.unsubscribe("<liveNotificationId>")
```

- Both methods are suspending (`subscribe` returns the backend LA subscriber id;
  the SDK persists it, so `unsubscribe` only needs the live notification id).
  Java callers can use `subscribeAsync` and `unsubscribeAsync`, which return a
  `CompletableFuture`.
- Subscribing to an already running activity renders its current state at once.

### 2. Handle clicks

Add this to your launcher (main) activity, next to the existing
`handleBackgroundNotificationClick` calls:

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // ...
    PushNotifications.getInstance().handleBackgroundNotificationClick(intent)
    PushNotifications.getInstance().liveActivities.handleClick(intent)
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    PushNotifications.getInstance().handleBackgroundNotificationClick(intent)
    PushNotifications.getInstance().liveActivities.handleClick(intent)
}
```

`liveActivities.handleClick`:
- reports the click analytics event (notification body and action buttons are
  distinguished automatically),
- opens the deep link carried by the notification through the SDK's
  notification click handler — the same routing used for regular push clicks,
- returns the deep link (or `null` if the intent was not a Live Activity click).

To handle the link yourself, pass `openDeepLink = false`:

```kotlin
val deepLink = PushNotifications.getInstance().liveActivities.handleClick(intent, openDeepLink = false)
if (deepLink != null) {
    // custom navigation
}
```

### 3. Deep link routing (optional)

Action buttons and the notification body can carry web URLs (`https://...`) or
app deep links (`app://...`). By default the SDK resolves them with a standard
`ACTION_VIEW` intent: web links open the browser, app links open the activity
with a matching `intent-filter`.

For custom routing override the click handler once — it then applies to both
regular pushes and Live Activities:

```kotlin
PushNotifications.getInstance().setNotificationClickHandler { context, url, overrideFlags ->
    // e.g. route app://<host>/beacons to a specific screen
}
```

## API reference

| Method | Description |
|---|---|
| `liveActivities.isSupported(): Boolean` | `true` on API 36+ |
| `liveActivities.subscribe(id): String` | Suspends while subscribing the device and returns the LA subscriber id |
| `liveActivities.subscribeAsync(id): CompletableFuture<String>` | Java-friendly asynchronous subscription wrapper |
| `liveActivities.unsubscribe(id): Unit` | Suspends while unsubscribing the device |
| `liveActivities.unsubscribeAsync(id): CompletableFuture<Void?>` | Java-friendly asynchronous unsubscription wrapper |
| `liveActivities.getSubscriberId(id): String` | Persisted LA subscriber id (empty if not subscribed) |
| `liveActivities.getActiveActivities(): List<LiveActivity>` | Currently tracked (rendered) activities |
| `liveActivities.isActive(id): Boolean` | Whether a given activity is currently active |
| `liveActivities.handleClick(intent, openDeepLink = true): String?` | Click analytics + deep link handling (see above) |

All Live Activity APIs are safe to call on any API level; on devices below
API 36 they degrade gracefully (no rendering).

## What the notification shows

Everything below is driven by the campaign configuration — no app code needed.

- **Title**: `Home Team - Away Team`; the large icon alternates between the two
  team crests every few seconds.
- **Second line**: `score · phase label · clock`, e.g. `1 : 0 · First half · 23:17'`.
  The clock ticks every second. Phase labels come from the campaign's
  `statusLabels`.
- **Pre-match countdown**: when the campaign has a countdown configured, the
  notification counts down to kick-off (`1:59 · Match will start soon`).
- **Progress bar**: first half, break and second half sections with a moving
  tracker. Colors come from the campaign design (`progressBarColor`,
  `breakTimeBarColor`). When the break bar color is not set, the bar is one
  continuous run. Once the match enters extra time the bar resets and is reused
  for the two extra-time halves.
- **Tracker icon**: a football (⚽) when `hasTrackerIcon` is enabled, otherwise
  a plain dot in the bar color.
- **Hot messages**: transient messages (e.g. `GOAL!`) temporarily take over the
  score/clock line (the team names stay visible), then the notification reverts
  automatically.
- **Action buttons**: up to 3 buttons (Android limit) of types `OPEN_APP`,
  `REDIRECT` (web URL or app deep link) and `CLOSE` (dismisses the activity).
- **End state**: the final score is shown briefly after the `end` event, then
  the notification removes itself.

## Match phases

The `FOOTBALL_MATCH_TRACKING` template understands the following `status`
values (each can have its own label via `statusLabels`):

`PRE_MATCH`, `FIRST_HALF`, `FIRST_HALF_ADDED_TIME`, `HALF_TIME_BREAK`,
`SECOND_HALF`, `SECOND_HALF_ADDED_TIME`, `FULL_TIME`, `EXTRA_TIME_BREAK`,
`EXTRA_TIME_FIRST_HALF`, `EXTRA_TIME_FIRST_HALF_ADDED_TIME`,
`EXTRA_TIME_HALF_TIME_BREAK`, `EXTRA_TIME_SECOND_HALF`,
`EXTRA_TIME_SECOND_HALF_ADDED_TIME`, `PENALTY_SHOOTOUT`, `MATCH_ENDED`, `OTHER`
(generic fallback with a running clock).

## Analytics

The SDK reports Live Activity statistics automatically — no integration needed:

| Event | When |
|---|---|
| `started` | The activity is rendered on the device (including late-join catch-up) |
| `clicked` | Tap on the notification body |
| `clicked_1` / `clicked_2` | Tap on the first / second action button |
| `closed` | The user dismissed the notification (swipe or `CLOSE` button) |
