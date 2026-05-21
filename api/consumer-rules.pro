# ========================
# AIDL interfaces — cannot be obfuscated across IPC
# ========================
-keep class tiiehenry.android.snapshot.file.ICompressCallback { *; }
-keep class tiiehenry.android.snapshot.file.ICompressCallback$Stub { *; }
-keep class tiiehenry.android.snapshot.file.IFileCompressor { *; }
-keep class tiiehenry.android.snapshot.file.IFileCompressor$Stub { *; }
-keep class tiiehenry.android.snapshot.task.ITaskHandler { *; }
-keep class tiiehenry.android.snapshot.task.ITaskHandler$Stub { *; }

# ========================
# Parcelable data classes — CREATOR and field names must survive
# ========================
-keep class tiiehenry.android.snapshot.app.AppDetail { *; }
-keep class tiiehenry.android.snapshot.app.AppInfo { *; }
-keep class tiiehenry.android.snapshot.app.AppStorage { *; }
-keep class tiiehenry.android.snapshot.app.AppStorageDetail { *; }
-keep class tiiehenry.android.snapshot.app.UserInfoHide { *; }
-keep class tiiehenry.android.snapshot.app.AppPermission { *; }

# ========================
# IServiceClient — base class for root service binding,
# field accessed cross-module from provider
# ========================
-keep class tiiehenry.android.snapshot.app.IServiceClient { *; }
