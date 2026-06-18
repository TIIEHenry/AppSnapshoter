package tiiehenry.android.app.snapshot.main.launch.batch

import android.content.Context
import android.view.LayoutInflater
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.DialogGroupBatchRestoreBinding
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.utils.AppStatusHelper

object GroupBatchRestoreDialog {

    fun show(
        context: Context,
        group: SnapGroup,
        onStartRestore: (List<GroupRestoreTask>) -> Unit
    ) {
        val hasAnyArchive = group.apps.any { it.archives.isNotEmpty() }
        if (!hasAnyArchive) {
            Toast.makeText(context, R.string.group_batch_restore_no_archives, Toast.LENGTH_SHORT).show()
            return
        }

        val binding = DialogGroupBatchRestoreBinding.inflate(LayoutInflater.from(context))
        val records = RestoreRecordStore.loadAll(group)
        val isInstalled: (tiiehenry.android.app.snapshot.group.ArchivedApp) -> Boolean =
            { AppStatusHelper.isAppInstalled(it) }

        var dialog: AlertDialog? = null

        fun selectedScope(): GroupRestoreScope = when (binding.scopeGroup.checkedRadioButtonId) {
            R.id.radio_scope_not_installed -> GroupRestoreScope.NOT_INSTALLED
            R.id.radio_scope_since_last -> GroupRestoreScope.SINCE_LAST_RESTORE
            R.id.radio_scope_all -> GroupRestoreScope.ALL
            else -> GroupRestoreScope.ALL
        }

        fun selectedStrategy(): ArchivePickStrategy = when (binding.strategyGroup.checkedRadioButtonId) {
            R.id.radio_strategy_oldest -> ArchivePickStrategy.OLDEST
            R.id.radio_strategy_last_restored -> ArchivePickStrategy.LAST_RESTORED
            R.id.radio_strategy_newest -> ArchivePickStrategy.NEWEST
            else -> ArchivePickStrategy.NEWEST
        }

        fun updateScopeLabels() {
            val counts = GroupBatchRestorePlanner.countByScope(group, records, isInstalled)
            binding.radioScopeNotInstalled.text =
                context.getString(R.string.group_batch_restore_scope_not_installed, counts.notInstalled)
            binding.radioScopeAll.text =
                context.getString(R.string.group_batch_restore_scope_all, counts.all)
            binding.radioScopeSinceLast.text =
                context.getString(R.string.group_batch_restore_scope_since_last, counts.sinceLastRestore)
        }

        fun refreshPreview() {
            updateScopeLabels()
            val preview = GroupBatchRestorePlanner.preview(
                group, selectedScope(), selectedStrategy(), records, isInstalled
            )
            if (preview.tasks.isEmpty()) {
                binding.previewText.text = context.getString(R.string.group_batch_restore_preview_empty)
                dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = false
            } else {
                binding.previewText.text =
                    context.getString(R.string.group_batch_restore_preview, preview.tasks.size)
                dialog?.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
            }
            if (preview.fallbackCount > 0) {
                binding.fallbackNote.visibility = android.view.View.VISIBLE
                binding.fallbackNote.text = context.getString(
                    R.string.group_batch_restore_preview_fallback,
                    preview.fallbackCount
                )
            } else {
                binding.fallbackNote.visibility = android.view.View.GONE
            }
        }

        val listener = RadioGroup.OnCheckedChangeListener { _, _ -> refreshPreview() }
        binding.scopeGroup.setOnCheckedChangeListener(listener)
        binding.strategyGroup.setOnCheckedChangeListener(listener)

        dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.group_batch_restore_title)
            .setView(binding.root)
            .setPositiveButton(R.string.group_batch_restore_confirm, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            refreshPreview()
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val preview = GroupBatchRestorePlanner.preview(
                    group, selectedScope(), selectedStrategy(), records, isInstalled
                )
                if (preview.tasks.isEmpty()) return@setOnClickListener
                dialog.dismiss()
                onStartRestore(preview.tasks)
            }
        }
        dialog.show()
    }
}
