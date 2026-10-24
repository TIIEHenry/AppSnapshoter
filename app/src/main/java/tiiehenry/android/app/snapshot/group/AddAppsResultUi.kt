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
        val plan = planHandle(result)
        if (plan.notice != null) showNotice(context, plan.notice)
        if (!plan.openMoveDialog) return
        showMoveDialog(context, snapshotViewModel, targetGroupId, result.conflicts, onMembershipChanged)
    }

    internal fun planHandle(result: AddAppsResult): HandlePlan {
        val openMoveDialog = result.conflicts.isNotEmpty()
        return HandlePlan(
            notice = batchNotice(result, includeAlreadyHere = !openMoveDialog),
            openMoveDialog = openMoveDialog,
        )
    }

    internal fun driveMoves(
        conflicts: Map<String, String>,
        move: (packageName: String, fromGroupId: String, onResult: (MoveAppResult) -> Unit) -> Unit,
        onFailure: (packageName: String, result: MoveAppResult) -> Unit,
        onMembershipChanged: () -> Unit,
    ) {
        val entries = conflicts.entries.toList()
        var sawMoved = false
        fun moveNext(index: Int) {
            if (index >= entries.size) {
                onMembershipChanged()
                return
            }
            val (pkg, fromId) = entries[index]
            move(pkg, fromId) { result ->
                when (result) {
                    is MoveAppResult.Moved -> {
                        sawMoved = true
                        moveNext(index + 1)
                    }
                    is MoveAppResult.AlreadyAtTarget -> moveNext(index + 1)
                    is MoveAppResult.Busy -> {
                        onFailure(pkg, result)
                        if (sawMoved) onMembershipChanged()
                    }
                    is MoveAppResult.Locked,
                    is MoveAppResult.TargetNonEmpty,
                    is MoveAppResult.CorruptMultiOwner,
                    is MoveAppResult.Error -> {
                        onFailure(pkg, result)
                        moveNext(index + 1)
                    }
                }
            }
        }
        moveNext(0)
    }

    private fun batchNotice(result: AddAppsResult, includeAlreadyHere: Boolean): Notice? {
        val values = result.items.values
        val corrupt = values.any { it is AddAppItemResult.CorruptMultiOwner }
        val busy = values.any { it is AddAppItemResult.Busy }
        val error = values.filterIsInstance<AddAppItemResult.Error>().firstOrNull()
        val alreadyAll = includeAlreadyHere && result.items.isNotEmpty() &&
            values.all { it is AddAppItemResult.AlreadyHere }
        return when {
            corrupt -> Notice.Corrupt
            busy -> Notice.Busy
            error != null -> Notice.Failed(error.message)
            alreadyAll -> Notice.AlreadyHere
            else -> null
        }
    }

    private fun showNotice(context: Context, notice: Notice) {
        when (notice) {
            Notice.Corrupt -> Toast.makeText(
                context, R.string.group_membership_corrupt, Toast.LENGTH_LONG
            ).show()
            Notice.Busy -> Toast.makeText(
                context, R.string.batch_operation_in_progress, Toast.LENGTH_SHORT
            ).show()
            is Notice.Failed -> Toast.makeText(
                context,
                context.getString(R.string.apps_popup_add_failed, notice.message),
                Toast.LENGTH_LONG
            ).show()
            Notice.AlreadyHere -> Toast.makeText(
                context, R.string.apps_popup_already_here, Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun showMoveDialog(
        context: Context,
        snapshotViewModel: SnapshotViewModel,
        targetGroupId: String,
        conflicts: Map<String, String>,
        onMembershipChanged: () -> Unit,
    ) {
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
        driveMoves(
            conflicts = conflicts,
            move = { pkg, fromId, onResult ->
                snapshotViewModel.moveAppBetweenGroups(fromId, targetGroupId, pkg, onResult)
            },
            onFailure = { pkg, result -> toastMoveFailure(context, pkg, result) },
            onMembershipChanged = onMembershipChanged,
        )
    }

    private fun toastMoveFailure(context: Context, pkg: String, result: MoveAppResult) {
        when (result) {
            is MoveAppResult.Busy -> Toast.makeText(
                context, R.string.batch_operation_in_progress, Toast.LENGTH_SHORT
            ).show()
            is MoveAppResult.Locked -> Toast.makeText(
                context,
                context.getString(R.string.group_move_failed_locked, pkg),
                Toast.LENGTH_LONG
            ).show()
            is MoveAppResult.TargetNonEmpty -> Toast.makeText(
                context,
                context.getString(R.string.group_move_failed_target_nonempty, pkg),
                Toast.LENGTH_LONG
            ).show()
            is MoveAppResult.CorruptMultiOwner -> Toast.makeText(
                context, R.string.group_membership_corrupt, Toast.LENGTH_LONG
            ).show()
            is MoveAppResult.Error -> Toast.makeText(
                context,
                context.getString(R.string.group_move_failed_generic, pkg, result.message),
                Toast.LENGTH_LONG
            ).show()
            is MoveAppResult.Moved, is MoveAppResult.AlreadyAtTarget -> Unit
        }
    }

    internal data class HandlePlan(
        val notice: Notice?,
        val openMoveDialog: Boolean,
    )

    internal sealed class Notice {
        data object Corrupt : Notice()
        data object Busy : Notice()
        data class Failed(val message: String) : Notice()
        data object AlreadyHere : Notice()
    }
}
