package tiiehenry.android.app.snapshot.app

import com.tencent.mmkv.MMKV

data class SnapApp(
    val mmkv: MMKV,
    val appInfo: AppInfo
)
