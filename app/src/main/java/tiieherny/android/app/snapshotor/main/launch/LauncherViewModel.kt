package tiieherny.android.app.snapshotor.main.launch

import androidx.lifecycle.ViewModel
import com.tencent.mmkv.MMKV
import tiieherny.android.app.snapshotor.SnapShotApp
import tiieherny.android.app.snapshotor.group.SnapedApp

class LauncherViewModel : ViewModel() {

    fun onGroupItemClicked(groupId: String, mmkv: MMKV, packageName: String, item: SnapedApp) {
        // 处理点击事件，可能是恢复存档
        // 这里需要根据实际需求实现恢复存档的逻辑
        // 例如，可以启动恢复过程或打开存档详情页面
        val snapShotApp = SnapShotApp.getInstance()
        val fs = snapShotApp.fileSystem
        val appManager = snapShotApp.appManager
        
        // 实现恢复存档的逻辑
        // 暂时打印信息作为示例
        android.util.Log.d("LauncherViewModel", "点击了存档: $packageName, Group: $groupId")
    }
}