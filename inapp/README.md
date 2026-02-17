# PushPushGo InAppMessages SDK

Android SDK for integrating in-app messages into your applications. Provides advanced message management, display functionality, and user interaction based on configuration parameters provided from the backend.

## Table of Contents
- [Installation](#installation)
- [Initialization](#initialization)
- [Basic Usage](#basic-usage)
- [Navigation Integration](#navigation-integration)
- [Triggering Messages](#triggering-messages)
- [Action Handling](#action-handling)

## Installation

To enable in-app messages in your PushPushGo project, contact our support or your account manager. 

### Gradle setup

```toml
# libs.versions.toml

[versions]
pushpushgo-sdk-inapp = "4.0.0"

[libraries]
pushpushgo-sdk-inapp = { module = "com.pushpushgo:sdk-inapp", version.ref = "pushpushgo-sdk-inapp" }
```

```kotlin
// app/build.gradle.kts
dependencies {
  implementation(libs.pushpushgo.sdk.inapp)
}
```

### Requirements
- Android API 28+
- Kotlin 1.6+
- Jetpack Compose (the library uses Compose for UI rendering)

## Initialization

Initialize the SDK in your `Application` class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize the SDK using configuration from AndroidManifest.xml
        InAppMessages.initialize(
          application = this,
          // pushSubscriptionProvider = ...,  optional, see [Action Handling] section
          // customCodeHandler = ..., optional, see [Action Handling] section
        )
      
        // or
      
        // Initialize manually
        InAppMessages.initialize(
          application = this,
          config = Config.create(
            projectId = "your-project-id",
            apiKey = "your-api-key",
          ),
          // pushSubscriptionProvider = ...,  optional, see [Action Handling] section
          // customCodeHandler = ..., optional, see [Action Handling] section
        )
    }
}
```

Don't forget to add your application class to your AndroidManifest.xml:

```xml
<application
    android:name=".MyApplication"
    ...>
    <!-- Rest of manifest content -->
</application>
```

## Basic Usage

### Message Evaluation

In-app messages are not displayed automatically.

Messages are evaluated and displayed only after the application explicitly sets the current route. When a route is set, the SDK displays messages that:
- Are configured to display on all routes, or 
- Have a route filter matching the provided route

### Displaying messages on the current route

Call `showMessagesOnRoute` whenever the active screen or route changes.

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
    
    override fun onResume() {
        super.onResume()
        // Display messages for main screen, example: screen nav route name is "main_screen"
        InAppMessages.getInstance().showMessagesOnRoute("main_screen")
    }
}
```

## Navigation Integration

The SDK provides convenient helper methods in the `InAppMessageHelper` class for integration with various navigation systems.

### Jetpack Navigation Integration

For Jetpack Navigation, you can use the `ObserveNavBackStack` Composable function to automatically show messages when the navigation destination changes:

```kotlin
@Composable
fun AppNavHost(navController: NavHostController) {
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    
    // Observe navigation changes and trigger appropriate in-app messages
    InAppMessageHelper.ObserveNavBackStack(
        navBackStackEntry = currentBackStackEntry
    )
    
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen() }
        composable("profile") { ProfileScreen() }
        // Other destinations...
    }
}
```

### Custom Route Extraction

You can customize how routes are extracted from the navigation back stack:

```kotlin
InAppMessageHelper.ObserveNavBackStack(
    navBackStackEntry = currentBackStackEntry,
    routeProvider = { entry ->
        // Custom logic to extract route name from NavBackStackEntry
        when (entry?.destination?.route) {
            "product/{productId}" -> "product_detail"
            else -> entry?.destination?.route
        }
    }
)
```

### Manual Navigation Observation

For custom navigation systems or more manual control:

```kotlin
@Composable
fun MyScreen(currentRoute: String) {
    // Observe current route and show relevant in-app messages
    InAppMessageHelper.ObserveNavigation(currentRoute)
    
    // Rest of your UI
}
```

### Activity-based Integration

If you're using a more traditional Activity-based navigation:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onResume() {
        super.onResume()
        // Use this helper to bind messages to the current Activity
        InAppMessageHelper.setCurrentActivity(this, "main_activity_route")
    }
    
    override fun onPause() {
        super.onPause()
        InAppMessageHelper.clearCurrentActivity()
    }
}
```

## Triggering Messages

Messages can be triggered based on custom events in your application:

```kotlin
// Trigger messages after purchase completion
fun onPurchaseCompleted(productId: String) {
    InAppMessages.getInstance().showMessagesOnTrigger(Trigger.keyValue(
        key = "purchase_completed",
        value = productId // value has to be a string
    ))
}

// Trigger review request after 3 feature uses
fun checkAndShowReviewRequest(usageCount: Int) {
    if (usageCount >= 3) {
        InAppMessages.getInstance().showMessagesOnTrigger(Trigger.key("review_request"))
    }
}
```

## Action Handling

### URL Redirections

By default, URL redirection actions open the target address in an external browser.  
No additional configuration is required.

---

### Push Subscription Actions

In-app message buttons can be configured to **subscribe users to push notifications**.

The InAppMessages SDK does not handle push subscription logic on its own. Instead, these actions are delegated to a `PushSubscriptionProvider`, if supplied during SDK initialization.

A default implementation is provided by the PushPushGo PushNotifications SDK:

```kotlin
PushNotifications.getInstance().getPushSubscriptionProvider()
```

A custom implementation may also be supplied.

---

### Custom Code Actions

In-app message buttons can be configured to contain **custom code**.

When such a button is clicked, the configured code is passed to a `CustomCodeHandler`, if supplied during SDK initialization.

```kotlin
interface CustomCodeHandler {
  fun handle(code: String)
}
```

---


## Debugging

To facilitate debugging, enable debug mode during initialization:

```kotlin
InAppMessages.initialize(
    application = this,
    config = Config.create(
      projectId = "your-project-id",
      apiKey = "your-api-key",
      isDebug = true
    )
)
```

Diagnostic messages will be visible in Logcat with the `[PushPushGo:InAppMessages]` tag.

## Trigger Examples

Below are examples of typical message triggers that you can implement in your application:

### E-commerce
```kotlin
// Abandoned cart
InAppMessages.getInstance().showMessagesOnTrigger(Trigger.key("cart_abandoned"))

// Product added to cart
InAppMessages.getInstance().showMessagesOnTrigger(Trigger.keyValue("product_added", productId))

// Order completed
InAppMessages.getInstance().showMessagesOnTrigger(Trigger.keyValue("order_completed", orderId))
```

### Content Applications
```kotlin
// Article read
InAppMessages.getInstance().showMessagesOnTrigger(Trigger.keyValue("article_read", articleId))

// Subscription expiring
InAppMessages.getInstance().showMessagesOnTrigger(Trigger.key("subscription_expiring"))

// Free content limit reached
InAppMessages.getInstance().showMessagesOnTrigger(Trigger.key("free_limit_reached"))
```

### Games
```kotlin
// Level completed
InAppMessages.getInstance().showMessagesOnTrigger(Trigger.keyValue("level_completed", levelId))

// Achievement unlocked
InAppMessages.getInstance().showMessagesOnTrigger(Trigger.keyValue("achievement_unlocked", achievementId))

// Game session ended
InAppMessages.getInstance().showMessagesOnTrigger(Trigger.key("game_session_ended"))
```

## Troubleshooting

### Messages Not Displaying
1. Verify the SDK is properly initialized
2. Check if trigger keys and values match the configuration in the panel
3. Enable debug mode and analyze logs

### UI Issues
1. Ensure you have the appropriate Jetpack Compose dependencies
2. Verify that routes/screens are correctly set
3. Check if messages aren't already marked as dismissed
