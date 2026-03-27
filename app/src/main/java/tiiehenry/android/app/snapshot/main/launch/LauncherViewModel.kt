package tiiehenry.android.app.snapshot.main.launch

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tencent.mmkv.MMKV
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.archieve.restore.ArchiveRestorer

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    /**
     * 高级恢复：只恢复选中的数据类型
     */
    fun onAdvancedRestoreClicked(
        context: Context,
        archivedApp: ArchivedApp,
        archiveItem: ArchiveItem,
        selectedTypes: Set<String>,
        updateCurrent: () -> Unit
    ) {
        ArchiveRestorer.restoreAdvanced(
            context,
            archivedApp,
            archiveItem,
            selectedTypes,
            updateCurrent,
            viewModelScope
        )
    }

    fun onGroupItemClicked(
        context: Context,
        groupId: String,
        mmkv: MMKV,
        packageName: String,
        item: ArchivedApp,
        updateCurrent: () -> Unit
    ) {
        ArchiveRestorer.restoreLatest(item, context, updateCurrent, viewModelScope)
    }

}