package tiiehenry.android.app.snapshot.main.apps

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.databinding.LayoutAppsPopupMenuBinding
import tiiehenry.android.app.snapshot.group.AddAppsResultUi
import tiiehenry.android.app.snapshot.group.AppGroupMembership
import tiiehenry.android.app.snapshot.group.GroupMembershipResolver
import tiiehenry.android.app.snapshot.group.JoinTargetCard
import tiiehenry.android.app.snapshot.main.launch.ArchiveListItem
import tiiehenry.android.app.snapshot.main.launch.app.AppConfigFragment
import tiiehenry.android.app.snapshot.utils.AppDetailsLauncher

class AppsItemPopupMenu(
    private val context: Context,
    private val fragmentManager: FragmentManager,
    private val snapshotViewModel: SnapshotViewModel,
    private val onNavigateToGroup: (groupId: String, packageName: String) -> Unit,
    private val onUninstall: (AppInfo) -> Unit,
) {
    private var popupWindow: PopupWindow? = null

    fun show(anchor: View, appInfo: AppInfo, membership: AppGroupMembership) {
        dismiss()

        val binding = LayoutAppsPopupMenuBinding.inflate(LayoutInflater.from(context))
        val window = PopupWindow(
            binding.root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.isOutsideTouchable = true
        window.isFocusable = true
        window.elevation = 16f * context.resources.displayMetrics.density

        val adapter = AppsPopupGroupAdapter { row ->
            dismiss()
            if (snapshotViewModel.resolveGroup(row.group.id) == null) {
                Toast.makeText(context, R.string.apps_popup_group_gone, Toast.LENGTH_SHORT).show()
            } else {
                onNavigateToGroup(row.group.id, appInfo.packageName)
            }
        }
        binding.groupList.layoutManager = LinearLayoutManager(context)
        binding.groupList.adapter = adapter
        adapter.submit(GroupMembershipResolver.membershipRows(membership))

        binding.btnSettings.setOnClickListener {
            dismiss()
            AppConfigFragment.newInstance(appInfo.packageName, appInfo.userId)
                .show(fragmentManager, "AppConfigFragment")
        }

        binding.btnInfo.setOnClickListener {
            dismiss()
            AppDetailsLauncher.open(context, appInfo.packageName)
        }

        binding.btnAdd.setOnClickListener {
            showJoinGroupPicker(appInfo)
        }

        binding.btnUninstall.isEnabled = appInfo.packageName != context.packageName
        binding.btnUninstall.setOnClickListener {
            if (appInfo.packageName == context.packageName) return@setOnClickListener
            AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.apps_popup_uninstall_title, appInfo.label))
                .setMessage(R.string.apps_popup_uninstall_message)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    dismiss()
                    onUninstall(appInfo)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        popupWindow = window
        window.showAsDropDown(anchor)
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    private fun showJoinGroupPicker(appInfo: AppInfo) {
        val cards = snapshotViewModel.archiveList.value.orEmpty()
            .filterIsInstance<ArchiveListItem.GroupCard>()
            .map { JoinTargetCard(it.group, it.setId, it.group.userId) }
        val targets = GroupMembershipResolver.independentJoinTargets(
            cards,
            appInfo.packageName,
            appInfo.userId,
        )
        if (targets.isEmpty()) {
            Toast.makeText(context, R.string.apps_popup_add_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val names = targets.map { it.name }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.apps_popup_pick_group_title)
            .setItems(names) { _, which ->
                val targetId = targets[which].id
                dismiss()
                snapshotViewModel.addAppsToGroup(targetId, listOf(appInfo)) { result ->
                    AddAppsResultUi.handle(
                        context,
                        snapshotViewModel,
                        targetId,
                        result,
                        onMembershipChanged = {},
                    )
                }
            }
            .show()
    }
}
