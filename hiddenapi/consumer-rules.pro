# ========================
# Rikka Refine @RefineAs classes — must not be renamed
# Refine rewrites bytecode at compile time; renaming breaks the mapping
# ========================
-keep class nota.android.hiddenapi.android.app.ActivityManagerHidden { *; }
-keep class nota.android.hiddenapi.android.app.AppOpsManagerHidden { *; }
-keep class nota.android.hiddenapi.android.content.pm.PackageManagerHidden { *; }
-keep class nota.android.hiddenapi.android.os.UserHandleHidden { *; }
-keep class nota.android.hiddenapi.android.os.UserManagerHidden { *; }
-keep class nota.android.hiddenapi.android.net.wifi.WifiConfigurationHidden { *; }
-keep class nota.android.hiddenapi.android.net.wifi.WifiManagerHidden { *; }

# Stub classes for hidden framework APIs
-keep class android.app.ActivityThread { *; }
-keep class android.app.ActivityThread$ApplicationThread { *; }
-keep class android.app.ContextImpl { *; }
-keep class android.content.pm.UserInfo { *; }

# Refine utility
-keep class nota.android.hiddenapi.RefineKt { *; }
