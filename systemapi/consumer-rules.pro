# ========================
# systemapi — all classes must survive (reflection targets & hidden API stubs)
# ========================
-keep class tiiehenry.android.app.snapshot.systemapi.** { *; }

# SystemProperties native methods
-keepclasseswithmembers class android.os.SystemProperties {
    native <methods>;
}

# Hidden API stub classes — must match framework signatures exactly
-keep class android.os.SystemProperties { *; }
-keep class com.android.server.display.DisplayControl { *; }
