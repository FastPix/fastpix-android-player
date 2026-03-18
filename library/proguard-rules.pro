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

# Seek Preview Module
-keep class io.fastpix.player.seekpreview.** { *; }
-keep interface io.fastpix.player.seekpreview.** { *; }
-keepclassmembers class io.fastpix.player.seekpreview.** { *; }

# Keep data models for serialization
-keep class io.fastpix.player.seekpreview.models.** { *; }

# Keep listeners
-keep interface io.fastpix.player.seekpreview.listeners.** { *; }
-keep class io.fastpix.player.seekpreview.listeners.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.** { *; }
-keep class com.google.gson.stream.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.** { *; }

# WorkManager
-keep class androidx.work.** { *; }
-keep interface androidx.work.** { *; }