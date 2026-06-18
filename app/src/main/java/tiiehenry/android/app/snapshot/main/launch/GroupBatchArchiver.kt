package tiiehenry.android.app.snapshot.main.launch

import android.content.Context
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotViewModel
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
    private val snapshotViewModel: SnapshotViewModel,
    private val onRefresh: (SnapGroup) -> Unit
) {

    fun archiveAllApps(group: SnapGroup) {
        val installedApps = group.apps.filter { AppStatusHelper.isAppInstalled(it) }.filter {
            val appConfig = AppConfigManager.getInstance().getConfig(
                it.appInfo.packageName,
                it.appInfo.userId
            )
            val actionConfig = if (appConfig.actionConfig.enabled) {
                appConfig.actionConfig
            } else {
                group.config.actionConfig
            }
            actionConfig.isAutoSnapshot
        }
        if (installedApps.isEmpty()) {
            Toast.makeText(context, R.string.group_batch_no_installed_apps, Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.group_batch_menu_archive)
            .setMessage(
                context.getString(
                    R.string.group_batch_archive_confirm,
                    group.name,
                    installedApps.size,
                    group.apps.size
                )
            )
            .setPositiveButton(R.string.confirm) { _, _ ->
                if (!snapshotViewModel.tryBeginBatchOperation()) {
                    Toast.makeText(context, R.string.batch_operation_in_progress, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
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
                    loadingDialog.setLabel(context.getString(R.string.group_batch_archive_stopping))
                }
                loadingDialog.setOnFailListener {
                    if (erroredList.isNotEmpty()) showErroredAppsDialog(erroredList)
                    else Toast.makeText(context, R.string.timeline_batch_no_errors, Toast.LENGTH_SHORT).show()
                }
                loadingDialog.setOnSuccessListener {
                    if (succeedList.isNotEmpty()) showSuccessAppsDialog(succeedList)
                    else Toast.makeText(context, R.string.timeline_batch_no_success, Toast.LENGTH_SHORT).show()
                }
                loadingDialog.setOnSuccessLongClickListener {
                    if (succeedList.isNotEmpty()) showSuccessStatistics(succeedList)
                    else Toast.makeText(context, R.string.timeline_batch_no_success, Toast.LENGTH_SHORT).show()
                }
                createSnapshotsSequentially(
                    loadingDialog, installedApps, group, erroredList, succeedList,
                    isCancelled, isForceCancelled, 0, startTime
                )
                loadingDialog.show()
            }
            .setNegativeButton(R.string.cancel, null)
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
            snapshotViewModel.loadGroups()
            snapshotViewModel.endBatchOperation()
            Toast.makeText(context, R.string.group_batch_archive_complete, Toast.LENGTH_SHORT).show()
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
                        snapshotViewModel.endBatchOperation()
                        updateDialogFinishState(
                            loadingDialog, System.currentTimeMillis() - startTime,
                            succeedList.size, erroredList.size, true
                        )
                        Toast.makeText(context, R.string.group_batch_archive_cancelled, Toast.LENGTH_SHORT).show()
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
                        .setTitle(context.getString(R.string.snapshot_error_detail_title, snapedApp.appInfo.label))
                        .setMessage(exception.toString())
                        .setPositiveButton(R.string.confirm, null).show()
                }
                return itemBinding.root
            }
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.snapshot_create_failed_title, items.size))
            .setAdapter(adapter) { _, _ -> }
            .setPositiveButton(R.string.confirm, null).show()
    }

    private fun updateDialogFinishState(
        loadingDialog: GroupItemsProgressDialog, totalTime: Long,
        succeedCount: Int, errorCount: Int, isCancelled: Boolean
    ) {
        val timeStr = formatDuration(totalTime)
        loadingDialog.setLabel(
            context.getString(if (isCancelled) R.string.timeline_batch_cancelled else R.string.timeline_batch_finished)
        )
        loadingDialog.setCurrentItem(context.getString(R.string.timeline_batch_total_time, timeStr))
        loadingDialog.setItemMessage(context.getString(R.string.timeline_batch_success_count, succeedCount))
        loadingDialog.setItemStatus(context.getString(R.string.timeline_batch_fail_count, errorCount))
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
                val timeStr = formatDuration(info.timeMillis)
                itemBinding.successInfo.text = context.getString(
                    R.string.snapshot_success_item_info,
                    timeStr,
                    Formatter.formatFileSize(context, info.archiveSize)
                )
                return itemBinding.root
            }
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.snapshot_create_success_title, items.size))
            .setAdapter(adapter) { _, _ -> }
            .setPositiveButton(R.string.confirm, null).show()
    }

    private fun showSuccessStatistics(successedList: List<SuccessSnapshotInfo>) {
        val totalCount = successedList.size
        val totalTimeMillis = successedList.sumOf { it.timeMillis }
        val totalSize = successedList.sumOf { it.archiveSize }
        val avgTimeMillis = if (totalCount > 0) totalTimeMillis / totalCount else 0L
        val avgSize = if (totalCount > 0) totalSize / totalCount else 0L
        fun formatTime(ms: Long): String = formatDuration(ms)
        val message = buildString {
            appendLine(context.getString(R.string.snapshot_success_stats_count, totalCount))
            appendLine(context.getString(R.string.snapshot_success_stats_total_time, formatTime(totalTimeMillis)))
            appendLine(context.getString(R.string.snapshot_success_stats_avg_time, formatTime(avgTimeMillis)))
            appendLine(context.getString(R.string.snapshot_success_stats_total_size, Formatter.formatFileSize(context, totalSize)))
            appendLine(context.getString(R.string.snapshot_success_stats_avg_size, Formatter.formatFileSize(context, avgSize)))
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.snapshot_success_stats_title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm, null).show()
    }

    fun showGroupStatistics(group: SnapGroup) {
        val totalApps = group.apps.size
        val installedApps = group.apps.count { AppStatusHelper.isAppInstalled(it) }
        val archivedApps = group.apps.count { it.archives.isNotEmpty() }
        val totalArchives = group.apps.sumOf { it.archives.size }
        val totalSize = group.apps.flatMap { it.archives.values }.sumOf { archive ->
            try { tiiehenry.android.app.snapshot.archive.MetaInfoHelper.getTotalSize(archive.metaInfo, archive.path) }
            catch (e: Exception) { 0L }
        }
        val avgArchives = if (archivedApps > 0) totalArchives.toDouble() / archivedApps else 0.0
        val message = buildString {
            appendLine(context.getString(R.string.group_stats_total_apps, totalApps))
            appendLine(context.getString(R.string.group_stats_installed_apps, installedApps))
            appendLine(context.getString(R.string.group_stats_archived_apps, archivedApps))
            appendLine(context.getString(R.string.group_stats_total_archives, totalArchives))
            appendLine(context.getString(R.string.group_stats_avg_archives, String.format("%.1f", avgArchives)))
            appendLine(context.getString(R.string.group_stats_total_size, Formatter.formatFileSize(context, totalSize)))
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.group_stats_title, group.name))
            .setMessage(message)
            .setPositiveButton(R.string.confirm, null).show()
    }

    private fun formatDuration(ms: Long): String {
        val timeMillis = ms
        val timeSeconds = timeMillis / 1000
        return when {
            timeSeconds < 1 -> context.getString(R.string.time_format_millis, timeMillis)
            timeSeconds < 60 -> context.getString(R.string.timeline_batch_time_seconds, timeSeconds)
            else -> context.getString(
                R.string.timeline_batch_time_minutes,
                timeSeconds / 60,
                timeSeconds % 60
            )
        }
    }
}
