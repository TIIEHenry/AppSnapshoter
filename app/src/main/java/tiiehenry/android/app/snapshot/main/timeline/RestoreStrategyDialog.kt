package tiiehenry.android.app.snapshot.main.timeline

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.DialogRestoreStrategyBinding

object RestoreStrategyDialog {

    fun show(
        context: Context,
        totalCount: Int,
        multiSnapshotCount: Int,
        onStrategySelected: (RestoreStrategy) -> Unit
    ) {
        val binding = DialogRestoreStrategyBinding.inflate(
            android.view.LayoutInflater.from(context)
        )
        binding.description.text = context.getString(R.string.timeline_restore_strategy_desc, multiSnapshotCount)

        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.timeline_restore_strategy_title, totalCount))
            .setView(binding.root)
            .setPositiveButton(R.string.timeline_restore_strategy_confirm) { _, _ ->
                val strategy = if (binding.radioNewest.isChecked) {
                    RestoreStrategy.NEWEST_FIRST
                } else {
                    RestoreStrategy.OLDEST_FIRST
                }
                onStrategySelected(strategy)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
