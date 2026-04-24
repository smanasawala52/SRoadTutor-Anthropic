# -----------------------------------------------------------------------------
#  SRoadTutor — ProGuard / R8 rules for release builds
# -----------------------------------------------------------------------------
#  R8 shrinks/obfuscates the APK in `buildTypes.release { minifyEnabled true }`.
#  These rules tell it what NOT to strip. We only add the minimum needed for
#  our dependencies; app code has no reflection concerns.
# -----------------------------------------------------------------------------

# Flutter core
-keep class io.flutter.embedding.** { *; }
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.plugins.** { *; }
-keep class io.flutter.util.** { *; }
-keep class io.flutter.view.** { *; }
-keep class io.flutter.** { *; }

# Google Sign-In (reflection in the OAuth flow)
-keep class com.google.android.gms.auth.** { *; }
-keep class com.google.android.gms.common.** { *; }

# Facebook Login SDK
-keep class com.facebook.** { *; }
-keepattributes Signature, *Annotation*

# SQLite native bindings used by Drift
-keep class org.sqlite.** { *; }
-keep class com.tekartik.sqflite.** { *; }

# Kotlin metadata / coroutines
-keepclassmembers class kotlin.Metadata { *; }
-dontwarn kotlin.coroutines.**

# Gson / Jackson — in case we add them later
-keepattributes Signature
-keepattributes *Annotation*

# Strip android.util.Log in release to avoid leaking sensitive info.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
