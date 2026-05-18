package tiiehenry.android.app.snapshot.main.launch

import android.content.Context
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.viewModelScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.config.AppConfigManager
import tiiehenry.android.app.snapshot.databinding.ItemErrorAppBinding
import tiiehenry.android.app.snapshot.databinding.ItemSuccessAppBinding
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.main.launch.makearchive.SnapshotCreator
import tiiehenry.android.app.snapshot.main.launch.makearchive.SuccessSnapshotInfo
import tiiehenry.android.app.snapshot.main.launch.makearchive.progress.GroupItemsProgressDialog
import tiiehenry.android.app.snapshot.utils.AppStatusHelper
import java.util.concurrent.atomic.AtomicBoolean

class GroupBatchArchiver(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val onRefresh: (SnapGroup) -> Unit
) {

    fun archiveAllApps(group: SnapGroup) {
        val installedApps = group.apps.filter { AppStatusHelper.isAppInstalled(it) }.filter {
            val appConfig = AppConfigManager.getInstance().getConfig(it.appInfo.packageName)
            val actionConfig = if (appConfig.actionConfig.enabled) {
                appConfig.actionConfig
            } else {
                group.config.actionConfig
            }
            actionConfig.isAutoSnapshot
        }
        if (installedApps.isEmpty()) {
            Toast.makeText(context, "没有已安装的应用可归档", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("全部归档")
            .setMessage("确定为 ${group.name} 中的 ${installedApps.size}/${group.apps.size} 个应用创建快照？")
            .setPositiveButton("确认") { _, _ ->
                val loadingDialog = GroupItemsProgressDialog(context)
                loadingDialog.setTotalProgress(installedApps.size)
                val erroredList = mutableMapOf<ArchivedApp, Exception>()
                val succeedList = mutableListOf<SuccessSnapshotInfo>()
                val isCancelled = AtomicBoolean(false)
                val isForceCancelled = AtomicBoolean(false)
                val startTime = System.currentTimeMillis()
                loadingDialog.setOnCancelListener {
                    isCancelled.set(true)
                    loadingDialog.setFinishButtonAsForceCancel { isForceCancelled.set(true) }
                    loadingDialog.setLabel("正在停止")
                }
                loadingDialog.setOnFailListener {
                    if (erroredList.isNotEmpty()) showErroredAppsDialog(erroredList)
                    else Toast.makeText(context, "暂无错误", Toast.LENGTH_SHORT).show()
                }
                loadingDialog.setOnSuccessListener {
                    if (succeedList.isNotEmpty()) showSuccessAppsDialog(succeedList)
                    else Toast.makeText(context, "暂无成功", Toast.LENGTH_SHORT).show()
                }
                loadingDialog.setOnSuccessLongClickListener {
                    if (succeedList.isNotEmpty()) showSuccessStatistics(succeedList)
                    else Toast.makeText(context, "暂无成功", Toast.LENGTH_SHORT).show()
                }
                createSnapshotsSequentially(
                    loadingDialog, installedApps, group, erroredList, succeedList,
                    isCancelled, isForceCancelled, 0, startTime
                )
                loadingDialog.show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun createSnapshotsSequentially(
        loadingDialog: GroupItemsProgressDialog,
        apps: List<ArchivedApp>,
        group: SnapGroup,
        erroredList: MutableMap<ArchivedApp, Exception>,
        succeedList: MutableList<SuccessSnapshotInfo>,
        isCancelled: AtomicBoolean,
        isForceCancelled: AtomicBoolean,
        currentIndex: Int,
        totalStartTime: Long
    ) {
        if (isCancelled.get()) return
        if (currentIndex >= apps.size) {
            onRefresh(group)
            SnapshotApp.getViewModel().loadGroups()
            Toast.makeText(context, "全部归档完成", Toast.LENGTH_SHORT).show()
            updateDialogFinishState(
                loadingDialog, System.currentTimeMillis() - totalStartTime,
                succeedList.size, erroredList.size, false
            )
            return
        }
        val item = apps[currentIndex]
        loadingDialog.setProgress(currentIndex + 1)
        loadingDialog.setLabel(item.appInfo.label)
        loadingDialog.setPackageName(item.appInfo.packageName)
        val startTime = System.currentTimeMillis()
        val snapshotCreator = SnapshotCreator(context, coroutineScope)
        snapshotCreator.createSnapshot(
            loadingDialog, item, group, isForceCancelled,
            object : SnapshotCreator.Callback {
                override fun onSuccess() {
                    val timeMillis = System.currentTimeMillis() - startTime
                    val archiveSize = calculateArchiveSize(item)
                    synchronized(succeedList) {
                        succeedList.add(SuccessSnapshotInfo(item, timeMillis, archiveSize))
                    }
                }

                override fun onError(e: Exception) {
                    erroredList[item] = e
                }

                override fun onFinish() {
                    if (isCancelled.get() && currentIndex < apps.size) {
                        updateDialogFinishState(
                            loadingDialog, System.currentTimeMillis() - startTime,
                            succeedList.size, erroredList.size, true
                        )
                        Toast.makeText(context, "已中止归档", Toast.LENGTH_SHORT).show()
                    } else {
                        createSnapshotsSequentially(
                            loadingDialog, apps, group, erroredList, succeedList,
                            isCancelled, isForceCancelled, currentIndex + 1, totalStartTime
                        )
                    }
                }
            })
    }

    private fun calculateArchiveSize(item: ArchivedApp): Long {
        return try {
            item.latestArchive?.let { archive ->
                archive.dataItems.sumOf { it.targetSize } + archive.extraItems.keys.sumOf { it.targetSize }
            } ?: 0L
        } catch (e: Exception) { 0L }
    }

    private fun showErroredAppsDialog(erroredList: Map<ArchivedApp, Exception>) {
        val items = erroredList.entries.toList()
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val itemBinding = if (convertView != null) {
                    ItemErrorAppBinding.bind(convertView)
                } else {
                    ItemErrorAppBinding.inflate(LayoutInflater.from(context), parent, false)
                }
                val (snapedApp, exception) = items[position]
                itemBinding.appIcon.setImageBitmap(snapedApp.appInfo.icon)
                itemBinding.appLabel.text = snapedApp.appInfo.label
                itemBinding.packageName.text = snapedApp.appInfo.packageName
                itemBinding.errorIcon.setOnClickListener {
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Error: ${snapedApp.appInfo.label}")
                        .setMessage(exception.toString())
                        .setPositiveButton("OK", null).show()
                }
                return itemBinding.root
            }
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("创建快照失败 (${items.size}个)")
            .setAdapter(adapter) { _, _ -> }
            .setPositiveButton("OK", null).show()
    }

    private fun updateDialogFinishState(
        loadingDialog: GroupItemsProgressDialog, totalTime: Long,
        succeedCount: Int, errorCount: Int, isCancelled: Boolean
    ) {
        val timeSeconds = totalTime / 1000
        val timeStr = if (timeSeconds < 60) "${timeSeconds}秒" else "${timeSeconds / 60}分${timeSeconds % 60}秒"
        loadingDialog.setLabel(if (isCancelled) "已中止" else "已完成")
        loadingDialog.setCurrentItem("总耗时: $timeStr")
        loadingDialog.setItemMessage("成功: $succeedCount")
        loadingDialog.setItemStatus("失败: $errorCount")
        loadingDialog.setPackageName("")
        loadingDialog.setFinishButtonAsClose { loadingDialog.dismiss() }
    }

    private fun showSuccessAppsDialog(successedList: List<SuccessSnapshotInfo>) {
        val items = successedList.toList()
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val itemBinding = ItemSuccessAppBinding.inflate(LayoutInflater.from(context), parent, false)
                val info = items[position]
                itemBinding.appIcon.setImageBitmap(info.archivedApp.appInfo.icon)
                itemBinding.appLabel.text = info.archivedApp.appInfo.label
                itemBinding.packageName.text = info.archivedApp.appInfo.packageName
                val timeSeconds = info.timeMillis / 1000
                val timeStr = if (timeSeconds < 1) "${info.timeMillis}ms"
                    else if (timeSeconds < 60) "${timeSeconds}s"
                    else "${timeSeconds / 60}min${timeSeconds % 60}s"
                itemBinding.successInfo.text = "耗时: $timeStr, 数据: ${Formatter.formatFileSize(context, info.archiveSize)}"
                return itemBinding.root
            }
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("创建快照成功 (${items.size}个)")
            .setAdapter(adapter) { _, _ -> }
            .setPositiveButton("OK", null).show()
    }

    private fun showSuccessStatistics(successedList: List<SuccessSnapshotInfo>) {
        val totalCount = successedList.size
        val totalTimeMillis = successedList.sumOf { it.timeMillis }
        val totalSize = successedList.sumOf { it.archiveSize }
        val avgTimeMillis = if (totalCount > 0) totalTimeMillis / totalCount else 0L
        val avgSize = if (totalCount > 0) totalSize / totalCount else 0L
        fun formatTime(ms: Long): String {
            val s = ms / 1000
            return if (s < 60) "${s}秒" else "${s / 60}分${s % 60}秒"
        }
        val message = buildString {
            appendLine("成功项数: $totalCount")
            appendLine("总耗时: ${formatTime(totalTimeMillis)}")
            appendLine("平均耗时: ${formatTime(avgTimeMillis)}")
            appendLine("总数据大小: ${Formatter.formatFileSize(context, totalSize)}")
            appendLine("平均数据大小: ${Formatter.formatFileSize(context, avgSize)}")
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("成功项统计数据")
            .setMessage(message)
            .setPositiveButton("OK", null).show()
    }

    fun showGroupStatistics(group: SnapGroup) {
        val totalApps = group.apps.size
        val installedApps = group.apps.count { AppStatusHelper.isAppInstalled(it) }
        val archivedApps = group.apps.count { it.archives.isNotEmpty() }
        val totalArchives = group.apps.sumOf { it.archives.size }
        val totalSize = group.apps.flatMap { it.archives.values }.sumOf { archive ->
            try { tiiehenry.android.app.snapshot.archieve.MetaInfoHelper.getTotalSize(archive.metaInfo, archive.path) }
            catch (e: Exception) { 0L }
        }
        val avgArchives = if (archivedApps > 0) totalArchives.toDouble() / archivedApps else 0.0
        val message = buildString {
            appendLine("总应用数: $totalApps")
            appendLine("已安装应用: $installedApps")
            appendLine("已存档应用: $archivedApps")
            appendLine("总存档数: $totalArchives")
            appendLine("平均存档数: ${String.format("%.1f", avgArchives)}")
            appendLine("总存档大小: ${Formatter.formatFileSize(context, totalSize)}")
        }
        MaterialAlertDialogBuilder(context)
            .setTitle("${group.name} - 统计数据")
            .setMessage(message)
            .setPositiveButton("OK", null).show()
    }
}
