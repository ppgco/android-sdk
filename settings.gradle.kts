pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}
include ':library', ":library-no-op", ":library-inappmessages"
if (!System.env.JITPACK) include ':sample', ':samplehms', ':samplejava', ':sample-inapp'