package tiieherny.android.app.snapshotor.main.launch

import androidx.lifecycle.ViewModel
import com.tencent.mmkv.MMKV
import tiieherny.android.app.snapshotor.archive.ArchieveItem

class LauncherViewModel : ViewModel() {

    fun onGroupItemClicked(groupId: String, mmkv: MMKV, packageName: String, item: ArchieveItem) {
        // TODO: 处理点击事件，可能是恢复存档
    }
}
