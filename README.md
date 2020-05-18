# PushPushGo Android SDK

## Instalation

1. Add SDK dependency to Your project
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
