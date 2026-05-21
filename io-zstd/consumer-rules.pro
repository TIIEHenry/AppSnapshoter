# ========================
# zstd-jni — vendored JNI classes, native method signatures must survive
# ========================
-keepclasseswithmembers class com.github.luben.zstd.** {
    native <methods>;
}
-keep class com.github.luben.zstd.util.Native { *; }

# Zstd finalizer classes referenced by native code
-keep class com.github.luben.zstd.ZstdOutputStreamNoFinalizer { *; }
-keep class com.github.luben.zstd.ZstdInputStreamNoFinalizer { *; }
-keep class com.github.luben.zstd.ZstdDirectBufferCompressingStreamNoFinalizer { *; }
-keep class com.github.luben.zstd.ZstdDirectBufferDecompressingStreamNoFinalizer { *; }
-keep class com.github.luben.zstd.ZstdBufferDecompressingStreamNoFinalizer { *; }
