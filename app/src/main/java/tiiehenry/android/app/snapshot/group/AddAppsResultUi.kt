package tiiehenry.android.app.snapshot.group

import android.app.AlertDialog
import android.content.Context
import android.widget.Toast
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotViewModel

object AddAppsResultUi {
    fun handle(
        context: Context,
        snapshotViewModel: SnapshotViewModel,
        targetGroupId: String,
        result: AddAppsResult,
        onMembershipChanged: () -> Unit,
    ) {
        val conflicts = result.conflicts
        if (conflicts.isEmpty()) {
            val busy = result.items.values.any { it is AddAppItemResult.Busy }
            val corrupt = result.items.values.any { it is AddAppItemResult.CorruptMultiOwner }
            val error = result.items.values.filterIsInstance<AddAppItemResult.Error>().firstOrNull()
            val alreadyAll = result.items.isNotEmpty() &&
                result.items.values.all { it is AddAppItemResult.AlreadyHere }
            when {
                corrupt -> Toast.makeText(
                    context, R.string.group_membership_corrupt, Toast.LENGTH_LONG
                ).show()
                busy -> Toast.makeText(
                    context, R.string.batch_operation_in_progress, Toast.LENGTH_SHORT
                ).show()
                error != null -> Toast.makeText(
                    context,
                    context.getString(R.string.apps_popup_add_failed, error.message),
                    Toast.LENGTH_LONG
                ).show()
                alreadyAll -> Toast.makeText(
                    context, R.string.apps_popup_already_here, Toast.LENGTH_SHORT
                ).show()
            }
            return
        }
        val target = snapshotViewModel.resolveGroup(targetGroupId) ?: return
        if (conflicts.size == 1) {
            val (pkg, ownerId) = conflicts.entries.first()
            val ownerName = snapshotViewModel.resolveGroup(ownerId)?.name ?: ownerId
            AlertDialog.Builder(context)
                .setTitle(R.string.group_move_conflict_title)
                .setMessage(
                    context.getString(
                        R.string.group_move_conflict_message,
                        pkg,
                        ownerName,
                        target.name
                    )
                )
                .setPositiveButton(R.string.group_move_action) { _, _ ->
                    moveApps(context, snapshotViewModel, mapOf(pkg to ownerId), targetGroupId, onMembershipChanged)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        } else {
            val lines = conflicts.entries.joinToString("\n") { (pkg, ownerId) ->
                val ownerName = snapshotViewModel.resolveGroup(ownerId)?.name ?: ownerId
                "$pkg → $ownerName"
            }
            AlertDialog.Builder(context)
                .setTitle(R.string.group_move_conflict_title)
                .setMessage(
                    context.getString(R.string.group_move_conflict_multi_message, lines)
                )
                .setPositiveButton(R.string.group_move_all_action) { _, _ ->
                    moveApps(context, snapshotViewModel, conflicts, targetGroupId, onMembershipChanged)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun moveApps(
        context: Context,
        snapshotViewModel: SnapshotViewModel,
        conflicts: Map<String, String>,
        targetGroupId: String,
        onMembershipChanged: () -> Unit,
    ) {
        val entries = conflicts.entries.toList()
        fun moveNext(index: Int) {
            if (index >= entries.size) {
                onMembershipChanged()
                return
            }
            val (pkg, fromId) = entries[index]
            snapshotViewModel.moveAppBetweenGroups(fromId, targetGroupId, pkg) { result ->
                when (result) {
                    is MoveAppResult.Moved, is MoveAppResult.AlreadyAtTarget -> moveNext(index + 1)
                    is MoveAppResult.Busy -> Toast.makeText(
                        context, R.string.batch_operation_in_progress, Toast.LENGTH_SHORT
                    ).show()
                    is MoveAppResult.Locked -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.group_move_failed_locked, pkg),
                            Toast.LENGTH_LONG
                        ).show()
                        moveNext(index + 1)
                    }
                    is MoveAppResult.TargetNonEmpty -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.group_move_failed_target_nonempty, pkg),
                            Toast.LENGTH_LONG
                        ).show()
                        moveNext(index + 1)
                    }
                    is MoveAppResult.CorruptMultiOwner -> {
                        Toast.makeText(context, R.string.group_membership_corrupt, Toast.LENGTH_LONG).show()
                        moveNext(index + 1)
                    }
                    is MoveAppResult.Error -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.group_move_failed_generic, pkg, result.message),
                            Toast.LENGTH_LONG
                        ).show()
                        moveNext(index + 1)
                    }
                }
            }
        }
        moveNext(0)
    }
}
