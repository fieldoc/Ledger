# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Google API and Google Play Services rely on reflective access in parts of the auth/tasks stack.
-keep class com.google.api.client.** { *; }
-keep class com.google.apis.** { *; }
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.api.** { *; }

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Optional Apache transport classes are referenced but not bundled in this app path.
-dontwarn org.apache.http.**

# Gemini and ML Kit scanner APIs.
-keep class com.google.ai.client.generativeai.** { *; }
-keep class com.google.mlkit.vision.documentscanner.** { *; }
-dontwarn com.google.mlkit.**

# Firebase Auth and Realtime Database
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
