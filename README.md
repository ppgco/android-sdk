# PushPushGo Android SDK

## Requirements

- minSdkVersion: 21
- configured GMS or HMS in project

## Instalation

1. Add SDK dependency to Your project
```groovy
// /build.gradle
allprojects {
    repositories {
        // local repo
        mavenLocal()

        // or

        // remote repo
        maven {
            url 'https://gitlab.goodylabs.com/api/v4/projects/297/packages/maven'
            name "PPGo"
            credentials(HttpHeaderCredentials) {
                name = 'Deploy-Token'
                value = GITLAB_PPGO_REPO_TOKEN
            }
            authentication {
                header(HttpHeaderAuthentication)
            }
        }
    }
}

// /app/build.gradle
dependencies {
    implementation 'com.pushpushgo:sdk:0.2.0'
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
$ ./gradlew :library:publishDebugPublicationToMavenLocal      // debug
$ ./gradlew :library:publishReleasePublicationToMavenLocal    // release
$ ./gradlew :library:publishToMavenLocal                      // both
```

To maven remote repository:

```sh
$ export GITLAB_PRIVATE_TOKEN=<token>
$ ./gradlew :library:publishDebugPublicationToPPGoRepository    // debug
$ ./gradlew :library:publishReleasePublicationToPPGoRepository  // release
$ ./gradlew :library:publishAllPublicationsToPPGoRepository     // both
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

HTML coverage report path: `library/build/reports/jacocoTestReport/html/`
