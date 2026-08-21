package tiiehenry.android.app.snapshot.main.apps

import android.app.AlertDialog
import android.content.Context
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.group.AppGroupMembership
import tiiehenry.android.app.snapshot.group.SnapGroup

/**
 * 应用 Tab 长按：展示独占 / 共享归属，点选跳转存档对应组。
 */
object AppMembershipDialog {

    fun show(
        context: Context,
        appInfo: AppInfo,
        membership: AppGroupMembership,
        onNavigate: (SnapGroup) -> Unit,
    ) {
        if (!membership.hasAny) {
            AlertDialog.Builder(context)
                .setTitle(appInfo.label)
                .setMessage(R.string.app_membership_ungrouped)
                .setPositiveButton(R.string.confirm, null)
                .show()
            return
        }

        data class Row(val label: String, val group: SnapGroup)

        val rows = mutableListOf<Row>()
        for (g in membership.exclusiveGroups) {
            rows += Row(
                context.getString(R.string.app_membership_exclusive_item, g.name),
                g
            )
        }
        for (g in membership.sharedGroups) {
            rows += Row(
                context.getString(R.string.app_membership_shared_item, g.name),
                g
            )
        }

        AlertDialog.Builder(context)
            .setTitle(
                context.getString(R.string.app_membership_detail_title, appInfo.label)
            )
            .setItems(rows.map { it.label }.toTypedArray()) { _, which ->
                onNavigate(rows[which].group)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
