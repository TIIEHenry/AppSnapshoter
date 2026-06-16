package tiiehenry.android.app.snapshot.main.timeline

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.archive.manage.ArchiveManager
import tiiehenry.android.app.snapshot.archive.restore.ArchiveRestorer
import tiiehenry.android.app.snapshot.databinding.ItemErrorAppBinding
import tiiehenry.android.app.snapshot.databinding.ItemSuccessAppBinding
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.main.launch.makearchive.progress.GroupItemsProgressDialog
import java.util.concurrent.atomic.AtomicBoolean

class TimelineBatchOperator(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val snapshotViewModel: SnapshotViewModel,
    private val timelineViewModel: TimelineViewModel
) {

    data class BatchResult(
        val succeeded: MutableList<Pair<TimelineEntryKey, ArchivedApp>> = mutableListOf(),
        val failed: MutableList<Pair<TimelineEntryKey, Exception>> = mutableListOf(),
        var skippedLocked: Int = 0
    )

    fun batchRestore(
        entries: List<TimelineEntry>,
        groups: List<SnapGroup>,
        timeRange: TimeRange,
        strategy: RestoreStrategy
    ) {
        timelineViewModel.isBatchRunning.value = true
        val loadingDialog = GroupItemsProgressDialog(context)
        loadingDialog.setTotalProgress(entries.size)
        val result = BatchResult()
        val isCancelled = AtomicBoolean(false)
        val isForceCancelled = AtomicBoolean(false)
        val startTime = System.currentTimeMillis()

        loadingDialog.setOnCancelListener {
            isCancelled.set(true)
            loadingDialog.setFinishButtonAsForceCancel { isForceCancelled.set(true) }
            loadingDialog.setLabel(context.getString(R.string.timeline_batch_cancelled))
        }
        loadingDialog.setOnFailListener {
            if (result.failed.isNotEmpty()) showErroredAppsDialog(result.failed)
            else Toast.makeText(context, R.string.timeline_batch_no_errors, Toast.LENGTH_SHORT).show()
        }
        loadingDialog.setOnSuccessListener {
            if (result.succeeded.isNotEmpty()) showSuccessAppsDialog(result.succeeded)
            else Toast.makeText(context, R.string.timeline_batch_no_success, Toast.LENGTH_SHORT).show()
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                var currentIndex = 0
                while (currentIndex < entries.size && !isCancelled.get()) {
                    val entry = entries[currentIndex]
                    withContext(Dispatchers.Main) {
                        loadingDialog.setProgress(currentIndex + 1)
                        loadingDialog.setLabel(entry.appLabel)
                        loadingDialog.setPackageName(entry.key.packageName)
                        loadingDialog.setCurrentItem(entry.groupName)
                    }

                    try {
                        val resolved = TimelineRepository.resolveEntry(entry.key, groups, timeRange)
                        if (resolved == null) {
                            result.failed.add(entry.key to IllegalStateException(context.getString(R.string.timeline_entry_stale)))
                        } else {
                            val (archivedApp, matchingArchives) = resolved
                            val archiveToRestore = TimelineRepository.resolveArchive(matchingArchives, strategy)
                            ArchiveRestorer.restoreArchiveSuspend(context, archivedApp, archiveToRestore)
                            result.succeeded.add(entry.key to archivedApp)
                        }
                    } catch (e: Exception) {
                        result.failed.add(entry.key to e)
                    }

                    currentIndex++
                }
            } finally {
                withContext(Dispatchers.Main) {
                    timelineViewModel.isBatchRunning.value = false
                    snapshotViewModel.loadGroups()
                    updateDialogFinishState(
                        loadingDialog,
                        System.currentTimeMillis() - startTime,
                        result.succeeded.size,
                        result.failed.size,
                        isCancelled.get()
                    )
                }
            }
        }
        loadingDialog.show()
    }

    fun batchDelete(
        entries: List<TimelineEntry>,
        groups: List<SnapGroup>,
        timeRange: TimeRange,
        onConfirmed: () -> Unit = {}
    ) {
        var totalArchives = 0
        var lockedCount = 0
        val deletePlan = mutableListOf<Triple<TimelineEntry, ArchivedApp, List<ArchiveItem>>>()

        for (entry in entries) {
            val resolved = TimelineRepository.resolveEntry(entry.key, groups, timeRange) ?: continue
            val (archivedApp, archives) = resolved
            val locked = archives.count { it.metaInfo.isLocked }
            lockedCount += locked
            totalArchives += archives.size
            deletePlan.add(Triple(entry, archivedApp, archives))
        }

        val message = context.getString(
            R.string.timeline_delete_confirm,
            deletePlan.size,
            totalArchives,
            lockedCount
        )

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.timeline_delete)
            .setMessage(message)
            .setPositiveButton(R.string.timeline_delete) { _, _ ->
                onConfirmed()
                executeBatchDelete(deletePlan)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun executeBatchDelete(deletePlan: List<Triple<TimelineEntry, ArchivedApp, List<ArchiveItem>>>) {
        timelineViewModel.isBatchRunning.value = true
        val loadingDialog = GroupItemsProgressDialog(context)
        loadingDialog.setTotalProgress(deletePlan.size)
        val result = BatchResult()
        val isCancelled = AtomicBoolean(false)
        val isForceCancelled = AtomicBoolean(false)
        val startTime = System.currentTimeMillis()

        loadingDialog.setOnCancelListener {
            isCancelled.set(true)
            loadingDialog.setFinishButtonAsForceCancel { isForceCancelled.set(true) }
            loadingDialog.setLabel(context.getString(R.string.timeline_batch_cancelled))
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                var currentIndex = 0
                while (currentIndex < deletePlan.size && !isCancelled.get()) {
                    val (entry, archivedApp, archives) = deletePlan[currentIndex]
                    withContext(Dispatchers.Main) {
                        loadingDialog.setProgress(currentIndex + 1)
                        loadingDialog.setLabel(entry.appLabel)
                        loadingDialog.setPackageName(entry.key.packageName)
                    }

                    var deletedAny = false
                    var hasError = false
                    for (archive in archives) {
                        if (isCancelled.get()) break
                        if (archive.metaInfo.isLocked) {
                            result.skippedLocked++
                            continue
                        }
                        try {
                            ArchiveManager.deleteArchive(archivedApp, archive)
                            deletedAny = true
                        } catch (e: Exception) {
                            result.failed.add(entry.key to e)
                            hasError = true
                        }
                    }
                    if (!hasError && deletedAny) {
                        result.succeeded.add(entry.key to archivedApp)
                    }

                    currentIndex++
                }
            } finally {
                withContext(Dispatchers.Main) {
                    timelineViewModel.isBatchRunning.value = false
                    snapshotViewModel.loadGroups()
                    val skippedMsg = if (result.skippedLocked > 0) context.getString(R.string.timeline_batch_skipped_locked, result.skippedLocked) else ""
                    Toast.makeText(
                        context,
                        context.getString(R.string.timeline_batch_delete_done, result.succeeded.size, result.failed.size, skippedMsg),
                        Toast.LENGTH_LONG
                    ).show()
                    updateDialogFinishState(
                        loadingDialog,
                        System.currentTimeMillis() - startTime,
                        result.succeeded.size,
                        result.failed.size,
                        isCancelled.get()
                    )
                }
            }
        }
        loadingDialog.show()
    }

    private fun updateDialogFinishState(
        loadingDialog: GroupItemsProgressDialog,
        totalTime: Long,
        succeedCount: Int,
        errorCount: Int,
        isCancelled: Boolean
    ) {
        val timeSeconds = totalTime / 1000
        val timeStr = if (timeSeconds < 60) context.getString(R.string.timeline_batch_time_seconds, timeSeconds) else context.getString(R.string.timeline_batch_time_minutes, timeSeconds / 60, timeSeconds % 60)
        loadingDialog.setLabel(if (isCancelled) context.getString(R.string.timeline_batch_cancelled) else context.getString(R.string.timeline_batch_finished))
        loadingDialog.setCurrentItem(context.getString(R.string.timeline_batch_total_time, timeStr))
        loadingDialog.setItemMessage(context.getString(R.string.timeline_batch_success_count, succeedCount))
        loadingDialog.setItemStatus(context.getString(R.string.timeline_batch_fail_count, errorCount))
        loadingDialog.setPackageName("")
        loadingDialog.setFinishButtonAsClose { loadingDialog.dismiss() }
    }

    private fun showErroredAppsDialog(erroredList: List<Pair<TimelineEntryKey, Exception>>) {
        val items = erroredList.toList()
        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val itemBinding = if (convertView != null) {
                    ItemErrorAppBinding.bind(convertView)
                } else {
                    ItemErrorAppBinding.inflate(LayoutInflater.from(context), parent, false)
                }
                val (key, exception) = items[position]
                itemBinding.appLabel.text = key.packageName
                itemBinding.packageName.text = exception.message ?: exception.toString()
                itemBinding.errorIcon.setOnClickListener {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(context.getString(R.string.timeline_batch_error_detail_title, key.packageName))
                        .setMessage(exception.toString())
                        .setPositiveButton(android.R.string.ok, null).show()
                }
                return itemBinding.root
            }
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.timeline_batch_error_title, items.size))
            .setAdapter(adapter) { _, _ -> }
            .setPositiveButton(android.R.string.ok, null).show()
    }

    fun batchExport(
        entries: List<TimelineEntry>,
        groups: List<SnapGroup>,
        timeRange: TimeRange,
        destinationPath: String
    ) {
        timelineViewModel.isBatchRunning.value = true
        val loadingDialog = GroupItemsProgressDialog(context)
        loadingDialog.setTotalProgress(entries.size)
        val result = BatchResult()
        val isCancelled = AtomicBoolean(false)
        val isForceCancelled = AtomicBoolean(false)
        val startTime = System.currentTimeMillis()

        loadingDialog.setOnCancelListener {
            isCancelled.set(true)
            loadingDialog.setFinishButtonAsForceCancel { isForceCancelled.set(true) }
            loadingDialog.setLabel(context.getString(R.string.timeline_batch_cancelled))
        }

        val fs = SnapshotApp.getInstance().fileSystem

        coroutineScope.launch(Dispatchers.IO) {
            try {
                fs.mkdirs(destinationPath)
                var currentIndex = 0
                while (currentIndex < entries.size && !isCancelled.get()) {
                    val entry = entries[currentIndex]
                    withContext(Dispatchers.Main) {
                        loadingDialog.setProgress(currentIndex + 1)
                        loadingDialog.setLabel(entry.appLabel)
                        loadingDialog.setPackageName(entry.key.packageName)
                        loadingDialog.setCurrentItem(entry.groupName)
                    }

                    try {
                        val resolved = TimelineRepository.resolveEntry(entry.key, groups, timeRange)
                        if (resolved == null) {
                            result.failed.add(entry.key to IllegalStateException(context.getString(R.string.timeline_entry_stale)))
                        } else {
                            val (archivedApp, matchingArchives) = resolved
                            for (archive in matchingArchives) {
                                if (isCancelled.get()) break
                                val targetDir = "$destinationPath/${entry.key.packageName}/${archive.name}"
                                fs.copyRecursively(archive.path, targetDir, false)
                            }
                            result.succeeded.add(entry.key to archivedApp)
                        }
                    } catch (e: Exception) {
                        result.failed.add(entry.key to e)
                    }

                    currentIndex++
                }
            } finally {
                withContext(Dispatchers.Main) {
                    timelineViewModel.isBatchRunning.value = false
                    Toast.makeText(
                        context,
                        context.getString(R.string.timeline_export_done, result.succeeded.size, result.failed.size),
                        Toast.LENGTH_LONG
                    ).show()
                    updateDialogFinishState(
                        loadingDialog,
                        System.currentTimeMillis() - startTime,
                        result.succeeded.size,
                        result.failed.size,
                        isCancelled.get()
                    )
                }
            }
        }
        loadingDialog.show()
    }

    private fun showSuccessAppsDialog(successedList: List<Pair<TimelineEntryKey, ArchivedApp>>) {
        val items = successedList.toList()
        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val itemBinding = ItemSuccessAppBinding.inflate(LayoutInflater.from(context), parent, false)
                val (key, archivedApp) = items[position]
                itemBinding.appIcon.setImageBitmap(archivedApp.appInfo.icon)
                itemBinding.appLabel.text = archivedApp.appInfo.label
                itemBinding.packageName.text = key.packageName
                itemBinding.successInfo.text = key.groupId
                return itemBinding.root
            }
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.timeline_batch_success_title, items.size))
            .setAdapter(adapter) { _, _ -> }
            .setPositiveButton(android.R.string.ok, null).show()
    }
}
