-allowaccessmodification
-dontwarn javax.annotation.**

#Config for app
-keep class com.pushpushgo.sample.R$raw

#Config for anallitycs
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

#Config for Moshi
-keepclasseswithmembers class * { @com.squareup.moshi.* <methods>; }
-keep @com.squareup.moshi.JsonQualifier @interface *
-keepclassmembers @com.squareup.moshi.JsonClass class * extends java.lang.Enum {
    <fields>;
    **[] values();
}

#Config for okhttp
-dontwarn org.conscrypt.ConscryptHostnameVerifier
