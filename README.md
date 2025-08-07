

# PushPushGo Android SDK

[![JitPack](https://img.shields.io/jitpack/v/github/ppgco/android-sdk?style=flat-square)](https://jitpack.io/#ppgco/android-sdk)    
![GitHub Workflow Status (master)](https://img.shields.io/github/actions/workflow/status/ppgco/android-sdk/test.yml?branch=master&style=flat-square)    
![GitHub tag (latest)](https://img.shields.io/github/v/tag/ppgco/android-sdk?style=flat-square)

## Requirements

- minSdkVersion: 21
- configured GMS or HMS in project  app

## Preparation
**Before proceeding the installlation make sure you have completed the steps listed below:**

1. **Remove all previous implementations from other providers or custom Firebase / Huawei implementation**
2. **Connect App with Firebase / Huawei project**
3. **(GMS only) From Firebase console download google-services.json and place it in app root folder**
4. **Add dependencies based on configuration build you use - Groovy/Kotlin**

Groovy DSL
```groovy
// /app/build.gradle
dependencies {
    // GMS
    implementation platform('com.google.firebase:firebase-bom:33.1.2')
    implementation 'com.google.firebase:firebase-messaging'
    // HMS
    implementation 'com.huawei.agconnect:agconnect-core:1.9.1.303'
    implementation 'com.huawei.hms:push:6.11.0.300'
}
```

Kotlin DSL
```kotlin    
// /libs.versions.toml
[versions]
// GMS
firebaseBom = "33.1.2"
firebaseMessaging = "24.0.0"
googleGmsGoogleServices = "4.4.2"

//HMS
agconnectCore = "1.9.1.303"
hmsPush = "6.11.0.300"

[libraries]
// GMS
firebase-bom = { module = "com.google.firebase:firebase-bom", version.ref = "firebaseBom" }
firebase-messaging = { group = "com.google.firebase", name = "firebase-messaging", version.ref = "firebaseMessaging" }

// HMS
agconnect-core = { module = "com.huawei.agconnect:agconnect-core", version.ref = "agconnectCore" }
hms-push = { module = "com.huawei.hms:push", version.ref = "hmsPush" }

[plugins]
// GMS
google-gms-google-services = { id = "com.google.gms.google-services", version.ref = "googleGmsGoogleServices" }



// /app/build.gradle.kts
plugins { 
    alias(libs.plugins.google.gms.google.services)
}  

dependencies {
    // GMS
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    // HMS
    implementation(libs.agconnect.core)
    implementation(libs.hms.push)
}



// /build.gradle.kts
plugins {
    alias(libs.plugins.google.gms.google.services) apply false
}
```  
4. **Provide GMS / HMS credentials in PushPushGo application (/project/providers)**

**GMS**
> * Go to your Firebase console and navigate to project settings
> * Open Cloud Messaging tab
> * Click Manage Service Accounts
> * Click on your service account email
> * Navigate to KEYS tab
> * Click ADD KEY
> * Click CREATE NEW KEY
> * Pick JSON type and click create
> * Download file and upload it in PushPushGo Application (/project/providers) in FCM v1 credentials section

**HMS**
> * Go to your Huawei developers console
> * Navigate to your project
> * Open project settings
> * Collect required info (appId, authUrl, pushUrl, appSecret)
> * Provide credentials in PushPushGo Application (/project/providers) in HMS Provider section

5. **In PushPushGo application collect your project ID and generate API KEY in access manager (/user/access-manager/keys) as u will need them later**


## Instalation

1. **Add SDK dependency to Your project**

Groovy DSL
```groovy
// /build.gradle
allprojects {
    repositories {
    // local repo
    mavenLocal()
    // or
    // jitpack
    maven { url 'https://jitpack.io' }
    }
}


// /app/build.gradle
dependencies {
    // local repo
    implementation 'com.pushpushgo:sdk:<version>'
    // or
    // jitpack
    implementation "com.github.ppgco.android-sdk:sdk:<version>"
}
```
Kotlin DSL
```kotlin
// /settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven ( "https://jitpack.io" )
    }
}



// /libs.versions.toml
[versions]
ppgSdk = "2.2.2"

[libraries]
ppg-sdk = { module = "com.github.ppgco.android-sdk:sdk", version.ref = "ppgSdk" }



// /app/build.gradle.kts
dependencies {
    implementation(libs.ppg.sdk)
}

```

2. **Add to Your AndroidManifest.xml:**

In application tag:
*Here you should pass your PPG project id and api key you have generated for that project*
```xml
<meta-data
	android:name="com.pushpushgo.apikey"
	android:value="{apiKey}" />
<meta-data
	android:name="com.pushpushgo.projectId"
	android:value="{projectId}" />
```

In your main activity tag:
```xml
<activity
    android:launchMode="singleTop"
```
```xml
<intent-filter>
    <action android:name="APP_PUSH_CLICK" />
    <category android:name="android.intent.category.DEFAULT" />
</intent-filter>
```
3. **Add to your MainActivity**:

in onCreate():
```java
if (savedInstanceState == null) {
    PushPushGo.getInstance().handleBackgroundNotificationClick(intent);
}
```
in onNewIntent():
```java
PushPushGo.getInstance().handleBackgroundNotificationClick(intent);
```
4. **Add to Your Application.onCreate():**
```java
PushPushGo.getInstance(this);
```

5. **Configuration**
- Change default notification color: override `@color/pushpushgo_notification_color_default`
- Change default notification channel id: override `@string/pushpushgo_notification_default_channel_id`
- Change default notification channel name: override `@string/pushpushgo_notification_default_channel_name`
- Change default notification icon: override
    - `res/drawable-hdpi/ic_stat_pushpushgo_default`
    - `res/drawable-mdpi/ic_stat_pushpushgo_default`
    - `res/drawable-xhdpi/ic_stat_pushpushgo_default`
    - `res/drawable-xxhdpi/ic_stat_pushpushgo_default`

## Usage

- Register subscriber:
```java
PushPushGo.getInstance().registerSubscriber();
```

- Unregister:
```java
PushPushGo.getInstance().unregisterSubscriber();
```

- Send beacon:
```java
PushPushGo.getInstance().createBeacon()
.set("see_invoice", true)
.setCustomId("SEEI")
.appendTag("demo")
.appendTag("mobile", "platform")
.send();
```

## Publishing

To maven local repository:
```sh
$ ./gradlew :library:publishToMavenLocal
```

## Tests
Run tests in `library` module:
```sh
$ ./gradlew :library:testDebug
```

Generate coverage report:    
```sh
$ ./gradlew :library:jacocoTestReport
```

HTML coverage report path:
`library/build/reports/jacocoTestReport/html/`
