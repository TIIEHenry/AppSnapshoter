# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ========================
# FastJSON2 serialization
# ========================
-keep class tiiehenry.android.app.snapshot.config.** { *; }
-keep class tiiehenry.android.app.snapshot.archive.bean.** { *; }
-keepclassmembers class * {
    @com.alibaba.fastjson2.annotation.JSONField <fields>;
}
# FastJSON2 TypeReference generic deserialization
-keep class * extends com.alibaba.fastjson2.TypeReference { *; }

# ========================
# App-level reflection
# ========================
# BuildConfigUtil reflectively accesses BuildConfig fields
-keep class tiiehenry.android.app.snapshot.BuildConfig { *; }
-keep class tiiehenry.android.app.snapshot.systemapi.BuildConfigUtil { *; }
