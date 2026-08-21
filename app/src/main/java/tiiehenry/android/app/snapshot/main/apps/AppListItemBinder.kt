package tiiehenry.android.app.snapshot.main.apps

import android.view.View
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.databinding.ItemAppListBinding
import tiiehenry.android.app.snapshot.group.AppGroupMembership
import tiiehenry.android.app.snapshot.group.GroupMembershipResolver
import tiiehenry.android.app.snapshot.group.SnapGroup

object AppListItemBinder {

    fun bindBasics(
        binding: ItemAppListBinding,
        appInfo: AppInfo,
        membership: AppGroupMembership?,
    ) {
        binding.appName.text = appInfo.label
        binding.appPackage.text = buildString {
            append(appInfo.packageName)
            if (!appInfo.versionName.isNullOrBlank()) {
                append(" · ")
                append(appInfo.versionName)
                append(" (")
                append(appInfo.versionCode)
                append(')')
            }
        }
        val summary = membership?.summaryLabel(
            binding.root.context.getString(R.string.app_membership_ungrouped)
        )
        if (summary.isNullOrBlank() || membership?.hasAny != true) {
            binding.appGroups.visibility = View.GONE
            binding.appGroups.text = ""
        } else {
            binding.appGroups.visibility = View.VISIBLE
            binding.appGroups.text = summary
        }
    }

    fun membershipOf(
        groups: List<SnapGroup>,
        appInfo: AppInfo,
        index: Map<String, AppGroupMembership>? = null,
    ): AppGroupMembership {
        val key = "${appInfo.packageName}:${appInfo.userId}"
        index?.get(key)?.let { return it }
        return GroupMembershipResolver.resolveMembership(
            groups, appInfo.packageName, appInfo.userId
        )
    }
}
