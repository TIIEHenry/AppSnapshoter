package tiiehenry.android.app.snapshot.main.selectapp

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.databinding.ItemAppListBinding
import tiiehenry.android.app.snapshot.group.AppGroupMembership
import tiiehenry.android.app.snapshot.main.apps.AppListItemBinder

class SelectAppAdapter(
    private val membershipIndexProvider: () -> Map<String, AppGroupMembership>,
    private val onItemClick: (AppInfo) -> Unit,
    private val onMultiSelectModeChanged: (Boolean) -> Unit = {},
    private val onMultiSelectedAppsChanged: (List<AppInfo>) -> Unit = {}
) : ListAdapter<AppInfo, SelectAppAdapter.ViewHolder>(AppDiffCallback()) {

    private val selection = SelectAppSelection()
    private var visibleApps: List<AppInfo> = emptyList()
    private var membershipIndex: Map<String, AppGroupMembership> = emptyMap()

    override fun submitList(list: List<AppInfo>?) {
        membershipIndex = membershipIndexProvider()
        visibleApps = list.orEmpty()
        super.submitList(list)
    }

    fun toggleMultiSelectMode() {
        selection.toggleMultiSelectMode()
        notifyDataSetChanged()
        onMultiSelectModeChanged(selection.isMultiSelectMode)
        onMultiSelectedAppsChanged(getSelectedApps())
    }

    fun getSelectedApps(): List<AppInfo> = selection.getSelectedApps()

    fun selectAll() {
        if (!selection.isMultiSelectMode) return
        selection.selectAll(visibleApps)
        notifyDataSetChanged()
        onMultiSelectedAppsChanged(getSelectedApps())
    }

    fun clearSelection() {
        selection.clear()
        notifyDataSetChanged()
        onMultiSelectedAppsChanged(getSelectedApps())
    }

    fun toggleAppSelection(appInfo: AppInfo, bindingAdapterPosition: Int, isSelected: Boolean) {
        if (!selection.isMultiSelectMode) return
        selection.toggle(appInfo, isSelected)
        onMultiSelectedAppsChanged(getSelectedApps())
        notifyItemChanged(bindingAdapterPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(
            binding,
            onItemClick,
            ::toggleAppSelection,
            ::toggleMultiSelectMode
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(
            item,
            selection.isMultiSelectMode,
            selection.isSelected(item),
            membershipIndex,
        )
    }

    class ViewHolder(
        private val binding: ItemAppListBinding,
        private val onItemClick: (AppInfo) -> Unit,
        private val onAppToggle: (AppInfo, Int, Boolean) -> Unit,
        private val onLongPressEnterMultiSelectMode: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(
            appInfo: AppInfo,
            isMultiSelectMode: Boolean,
            isSelected: Boolean,
            membershipIndex: Map<String, AppGroupMembership>,
        ) {
            val membership = membershipIndex["${appInfo.packageName}:${appInfo.userId}"]
                ?: AppGroupMembership(
                    appInfo.packageName,
                    appInfo.userId,
                    emptyList(),
                    emptyList(),
                )
            AppListItemBinder.bindBasics(binding, appInfo, membership)

            Glide.with(binding.root.context)
                .load(appInfo.icon)
                .into(binding.appIcon)

            if (isMultiSelectMode) {
                binding.appCheckbox.visibility = View.VISIBLE
                binding.appCheckbox.setOnCheckedChangeListener(null)
                binding.appCheckbox.isChecked = isSelected
                binding.root.isActivated = isSelected
            } else {
                binding.appCheckbox.visibility = View.GONE
                binding.root.isActivated = false
            }

            binding.root.setOnClickListener {
                if (isMultiSelectMode) {
                    onAppToggle(appInfo, bindingAdapterPosition, !binding.appCheckbox.isChecked)
                } else {
                    onItemClick(appInfo)
                }
            }

            binding.appCheckbox.setOnCheckedChangeListener { _, isChecked ->
                if (isMultiSelectMode) {
                    binding.root.post {
                        onAppToggle(appInfo, bindingAdapterPosition, isChecked)
                    }
                }
            }
            binding.root.setOnLongClickListener {
                onLongPressEnterMultiSelectMode()
                true
            }
        }
    }

    private class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName && oldItem.userId == newItem.userId
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem
        }
    }
}

internal class SelectAppSelection {
    private val selectedApps = linkedMapOf<String, AppInfo>()
    var isMultiSelectMode: Boolean = false
        private set

    fun selectionKey(appInfo: AppInfo): String = "${appInfo.packageName}:${appInfo.userId}"

    fun isSelected(appInfo: AppInfo): Boolean = selectionKey(appInfo) in selectedApps

    fun getSelectedApps(): List<AppInfo> = selectedApps.values.toList()

    fun toggleMultiSelectMode() {
        isMultiSelectMode = !isMultiSelectMode
        if (!isMultiSelectMode) {
            selectedApps.clear()
        }
    }

    fun selectAll(visible: List<AppInfo>) {
        if (!isMultiSelectMode) return
        for (app in visible) {
            selectedApps[selectionKey(app)] = app
        }
    }

    fun clear() {
        selectedApps.clear()
    }

    fun toggle(appInfo: AppInfo, isSelected: Boolean) {
        if (!isMultiSelectMode) return
        val key = selectionKey(appInfo)
        if (isSelected) {
            selectedApps[key] = appInfo
        } else {
            selectedApps.remove(key)
        }
    }
}
