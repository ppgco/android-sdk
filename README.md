# PushPushGo Android SDK

## Instalation

1. Add SDK dependency to Your project
```groovy
// /build.gradle
allprojects {
    repositories {
        mavenLocal()
        ...
}

// /app/build.gradle
dependencies {
    implementation 'com.pushpushgo:sdk:0.1.0'
}
```
2. Add to Your AndroidManifest.xml:
```xml
<meta-data
    android:name="com.pushpushgo.apikey"
    android:value="{apiKey}" />
<meta-data
    android:name="com.pushpushgo.projectId"
    android:value="{projectId}" />
```
3. Add to Your Application.onCreate():
```java
PushPushGo.getInstance(applicationContext);
```
4. Configuration
- Change default notification icon: override `@drawable/ic_stat_pushpushgo_default`
- Change default notification color: override `@color/pushpushgo_notification_color_default`
- Change default notification channel id: override `@string/pushpushgo_notification_default_channel_id`
- Change default notification channel name: override `@string/pushpushgo_notification_default_channel_name`

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
$ ./gradlew :library:install
```
