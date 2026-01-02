package tiieherny.android.app.snapshotor.app

import com.tencent.mmkv.MMKV
import tiieherny.android.app.snapshotor.app.AppInfo

data class SnapApp(
    val mmkv: MMKV,
    val appInfo: AppInfo
)
