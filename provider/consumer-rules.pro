# ========================
# Provider AIDL — root service IPC
# ========================
-keep class tiiehenry.android.snapshot.provider.service.ISnapShotRootService { *; }
-keep class tiiehenry.android.snapshot.provider.service.ISnapShotRootService$Stub { *; }
-keep class tiiehenry.android.snapshot.provider.service.bean.StatFsResult { *; }

# ========================
# Root service classes — bound via Intent, must not be renamed
# ========================
-keep class tiiehenry.android.snapshot.provider.service.SnapshotRootService { *; }
-keep class tiiehenry.android.snapshot.provider.service.SnapShotRootServiceClient { *; }
-keep class tiiehenry.android.snapshot.provider.filesystem.root.fsm.FileSystemManagerRootService { *; }

# ========================
# AppManagerImpl / PermissionManagementHandler use reflection on
# framework classes (android.content.Intent, android.content.pm.*),
# which are never obfuscated by R8 — no extra keep rules needed.
# ========================
