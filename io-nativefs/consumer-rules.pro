# ========================
# JNI native method signatures must not be obfuscated
# ========================
-keepclasseswithmembers class nota.android.io.NativeFileSystem {
    native <methods>;
}
