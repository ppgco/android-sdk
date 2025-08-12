# PushPushGo In-App Messages SDK

Android SDK for integrating in-app messages into your applications. Provides advanced message management, display functionality, and user interaction based on configuration parameters provided from the backend.

## Table of Contents
- [Installation](#installation)
- [Initialization](#initialization)
- [Basic Usage](#basic-usage)
- [Navigation Integration](#navigation-integration)
- [Triggering Messages](#triggering-messages)
- [Action Handling](#action-handling)
- [Advanced Features](#advanced-features)

## Installation

To enable Pop-ups or In-app messages in your PushPushGo project, contact our support or you account manager. 

### Gradle

Add the Jitpack repository to your project's `settings.gradle` file:

```groovy
dependencyResolutionManagement {
    repositories {
        // ...
        maven { url 'https://jitpack.io' }
    }
}
```

Then add the dependency in your app module's `build.gradle` file:

```groovy
dependencies {
    implementation 'com.github.ppgco:android-sdk:3.0.0-beta.1'
}
```

### Requirements
- Android API 23+ (Android 6.0+)
- Kotlin 1.6+
- Jetpack Compose (the library uses Compose for UI rendering)

## Initialization

Initialize the SDK in your `Application` class:

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize the SDK
        InAppMessagesSDK.initialize(
            application = this,
            projectId = "your-project-id",
            apiKey = "your-api-key",
            debug = BuildConfig.DEBUG // Optional: enable diagnostic logging
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

### Displaying Automatic Messages

Messages with the `APP_OPEN` trigger type will be displayed automatically when the app starts.

### Displaying Messages on Screens/Routes

To show messages configured for specific app screens:

```kotlin
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
    
    override fun onResume() {
        super.onResume()
        // Display messages for main screen, example: screen nav route name is "main_screen"
        InAppMessagesSDK.getInstance().showActiveMessages("main_screen")
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
    InAppMessagesSDK.getInstance().showMessagesOnTrigger(
        key = "purchase_completed",
        value = productId // value has to be a string
    )
}

// Trigger review request after 3 feature uses
fun checkAndShowReviewRequest(usageCount: Int) {
    if (usageCount >= 3) {
        InAppMessagesSDK.getInstance().showMessagesOnTrigger("review_request")
    }
}
```

## Action Handling

### URL Redirections

By default, redirection actions open the URL in an external browser. No additional configuration is required.

### JavaScript Actions

To handle JavaScript actions from messages:

```kotlin
InAppMessagesSDK.getInstance().setJsActionHandler { jsCode ->
    // Process JavaScript code
    when {
        jsCode.contains("addToCart") -> {
            // Handle add to cart
            val productId = parseProductId(jsCode)
            addToCart(productId)
        }
        jsCode.contains("applyDiscount") -> {
            // Handle discount application
            val discountCode = parseDiscountCode(jsCode)
            applyDiscount(discountCode)
        }
        // Other JS actions
    }
}
```

## Advanced Features

### Resource Cleanup

When logging out users or handling other state changes, you can clean up SDK resources:

```kotlin
fun onUserLogout() {
    InAppMessagesSDK.getInstance().cleanup()
    // Re-initialize SDK if needed after cleanup
}
```

### Debugging

To facilitate debugging, enable debug mode during initialization:

```kotlin
InAppMessagesSDK.initialize(
    application = this,
    projectId = "your-project-id",
    apiKey = "your-api-key",
    debug = true
)
```

Diagnostic messages will be visible in Logcat with these tags:
- `InAppMessageManager`
- `InAppUIController`
- `InAppMessageDisplayer`

## Trigger Examples

Below are examples of typical message triggers that you can implement in your application:

### E-commerce
```kotlin
// Abandoned cart
InAppMessagesSDK.getInstance().showMessagesOnTrigger("cart_abandoned")

// Product added to cart
InAppMessagesSDK.getInstance().showMessagesOnTrigger("product_added", productId)

// Order completed
InAppMessagesSDK.getInstance().showMessagesOnTrigger("order_completed", orderId)
```

### Content Applications
```kotlin
// Article read
InAppMessagesSDK.getInstance().showMessagesOnTrigger("article_read", articleId)

// Subscription expiring
InAppMessagesSDK.getInstance().showMessagesOnTrigger("subscription_expiring")

// Free content limit reached
InAppMessagesSDK.getInstance().showMessagesOnTrigger("free_limit_reached")
```

### Games
```kotlin
// Level completed
InAppMessagesSDK.getInstance().showMessagesOnTrigger("level_completed", levelId)

// Achievement unlocked
InAppMessagesSDK.getInstance().showMessagesOnTrigger("achievement_unlocked", achievementId)

// Game session ended
InAppMessagesSDK.getInstance().showMessagesOnTrigger("game_session_ended")
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
