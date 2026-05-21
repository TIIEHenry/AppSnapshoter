# ========================
# JNI native method signatures must not be obfuscated
# ========================
-keepclasseswithmembers class tiiehenry.android.compress.zstd.TarJNI {
    native <methods>;
}
