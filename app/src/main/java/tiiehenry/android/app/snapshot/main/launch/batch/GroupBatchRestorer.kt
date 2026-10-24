package tiiehenry.android.app.snapshot.main.launch.batch

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.archive.restore.ArchiveRestorer
import tiiehenry.android.app.snapshot.databinding.ItemErrorAppBinding
import tiiehenry.android.app.snapshot.databinding.ItemSuccessAppBinding
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.main.launch.makearchive.progress.GroupItemsProgressDialog
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class GroupBatchRestorer(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val snapshotViewModel: SnapshotViewModel,
    private val onRefresh: (SnapGroup) -> Unit
) {

    fun execute(group: SnapGroup, tasks: List<GroupRestoreTask>) {
        if (tasks.isEmpty()) return
        if (!snapshotViewModel.tryBeginBatchOperation()) {
            Toast.makeText(context, R.string.batch_operation_in_progress, Toast.LENGTH_SHORT).show()
            return
        }

        val loadingDialog = GroupItemsProgressDialog(context)
        loadingDialog.setTotalProgress(tasks.size)
        val succeeded = mutableListOf<ArchivedApp>()
        val failed = mutableMapOf<ArchivedApp, Exception>()
        val isCancelled = AtomicBoolean(false)
        val isForceCancelled = AtomicBoolean(false)
        val currentRestore = AtomicReference<Job?>(null)
        val startTime = System.currentTimeMillis()

        loadingDialog.setOnCancelListener {
            isCancelled.set(true)
            loadingDialog.setFinishButtonAsForceCancel {
                isForceCancelled.set(true)
                currentRestore.get()?.cancel()
            }
            loadingDialog.setLabel(context.getString(R.string.timeline_batch_cancelled))
        }
        loadingDialog.setOnFailListener {
            if (failed.isNotEmpty()) showErroredAppsDialog(failed)
            else Toast.makeText(context, R.string.timeline_batch_no_errors, Toast.LENGTH_SHORT).show()
        }
        loadingDialog.setOnSuccessListener {
            if (succeeded.isNotEmpty()) showSuccessAppsDialog(succeeded)
            else Toast.makeText(context, R.string.timeline_batch_no_success, Toast.LENGTH_SHORT).show()
        }

        coroutineScope.launch(Dispatchers.IO) {
            try {
                runGroupRestoreLoop(
                    taskCount = tasks.size,
                    isCancelled = isCancelled,
                    isForceCancelled = isForceCancelled,
                    currentRestore = currentRestore,
                    resolve = { index ->
                        GroupBatchRestorePlanner.resolveTaskAt(
                            tasks[index],
                            snapshotViewModel.groupList.value.orEmpty()
                        )
                    },
                    onItem = { index, task ->
                        withContext(Dispatchers.Main) {
                            loadingDialog.setProgress(index + 1)
                            loadingDialog.setLabel(task.app.appInfo.label)
                            loadingDialog.setPackageName(task.app.appInfo.packageName)
                            loadingDialog.setCurrentItem(task.archive.name)
                        }
                    },
                    onStale = { index ->
                        failed[tasks[index].app] = IllegalStateException(
                            context.getString(R.string.timeline_entry_stale)
                        )
                    },
                    restore = { task ->
                        ArchiveRestorer.restoreArchiveSuspend(context, task.app, task.archive)
                    },
                    onSucceeded = { app -> succeeded.add(app) },
                    onFailed = { app, error -> failed[app] = error }
                )
            } finally {
                withContext(Dispatchers.Main) {
                    snapshotViewModel.endBatchOperation()
                    snapshotViewModel.loadGroups()
                    onRefresh(group)
                    updateDialogFinishState(
                        loadingDialog,
                        System.currentTimeMillis() - startTime,
                        succeeded.size,
                        failed.size,
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
        val timeStr = if (timeSeconds < 60) {
            context.getString(R.string.timeline_batch_time_seconds, timeSeconds)
        } else {
            context.getString(R.string.timeline_batch_time_minutes, timeSeconds / 60, timeSeconds % 60)
        }
        loadingDialog.setLabel(
            if (isCancelled) context.getString(R.string.timeline_batch_cancelled)
            else context.getString(R.string.timeline_batch_finished)
        )
        loadingDialog.setCurrentItem(context.getString(R.string.timeline_batch_total_time, timeStr))
        loadingDialog.setItemMessage(context.getString(R.string.timeline_batch_success_count, succeedCount))
        loadingDialog.setItemStatus(context.getString(R.string.timeline_batch_fail_count, errorCount))
        loadingDialog.setPackageName("")
        loadingDialog.setFinishButtonAsClose { loadingDialog.dismiss() }
    }

    private fun showErroredAppsDialog(erroredList: Map<ArchivedApp, Exception>) {
        val items = erroredList.entries.toList()
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
                val (archivedApp, exception) = items[position]
                itemBinding.appIcon.setImageBitmap(archivedApp.appInfo.icon)
                itemBinding.appLabel.text = archivedApp.appInfo.label
                itemBinding.packageName.text = archivedApp.appInfo.packageName
                itemBinding.errorIcon.setOnClickListener {
                    MaterialAlertDialogBuilder(context)
                        .setTitle(
                            context.getString(
                                R.string.timeline_batch_error_detail_title,
                                archivedApp.appInfo.label
                            )
                        )
                        .setMessage(exception.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
                return itemBinding.root
            }
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.timeline_batch_error_title, items.size))
            .setAdapter(adapter) { _, _ -> }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showSuccessAppsDialog(succeededList: List<ArchivedApp>) {
        val items = succeededList.toList()
        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(position: Int) = items[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val itemBinding = ItemSuccessAppBinding.inflate(LayoutInflater.from(context), parent, false)
                val archivedApp = items[position]
                itemBinding.appIcon.setImageBitmap(archivedApp.appInfo.icon)
                itemBinding.appLabel.text = archivedApp.appInfo.label
                itemBinding.packageName.text = archivedApp.appInfo.packageName
                itemBinding.successInfo.text = archivedApp.group.name
                return itemBinding.root
            }
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.timeline_batch_success_title, items.size))
            .setAdapter(adapter) { _, _ -> }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}

internal suspend fun runGroupRestoreLoop(
    taskCount: Int,
    isCancelled: AtomicBoolean,
    isForceCancelled: AtomicBoolean,
    currentRestore: AtomicReference<Job?>,
    resolve: (Int) -> GroupRestoreTask?,
    onItem: suspend (Int, GroupRestoreTask) -> Unit,
    onStale: (Int) -> Unit,
    restore: suspend (GroupRestoreTask) -> Unit,
    onSucceeded: (ArchivedApp) -> Unit,
    onFailed: (ArchivedApp, Exception) -> Unit,
) {
    supervisorScope {
        var index = 0
        while (index < taskCount && !isCancelled.get() && !isForceCancelled.get()) {
            val task = resolve(index)
            if (task == null) {
                onStale(index)
                index++
                continue
            }
            onItem(index, task)
            if (isForceCancelled.get()) break
            val failure = AtomicReference<Exception?>(null)
            val job = launch {
                try {
                    restore(task)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failure.set(e)
                }
            }
            currentRestore.set(job)
            try {
                if (isForceCancelled.get()) job.cancel()
                job.join()
            } finally {
                currentRestore.compareAndSet(job, null)
            }
            if (!job.isCancelled) {
                val error = failure.get()
                if (error != null) onFailed(task.app, error) else onSucceeded(task.app)
            }
            if (isCancelled.get() || isForceCancelled.get()) break
            index++
        }
    }
}
