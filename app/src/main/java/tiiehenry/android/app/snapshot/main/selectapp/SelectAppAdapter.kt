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

    private var isMultiSelectMode = false
    private val selectedApps = mutableSetOf<String>()
    private var membershipIndex: Map<String, AppGroupMembership> = emptyMap()

    override fun submitList(list: List<AppInfo>?) {
        membershipIndex = membershipIndexProvider()
        super.submitList(list)
    }

    fun toggleMultiSelectMode() {
        isMultiSelectMode = !isMultiSelectMode
        if (!isMultiSelectMode) {
            selectedApps.clear()
        }
        notifyDataSetChanged()
        onMultiSelectModeChanged(isMultiSelectMode)
        onMultiSelectedAppsChanged(getSelectedApps())
    }

    fun getSelectedApps(): List<AppInfo> {
        return currentList.filter { it.packageName in selectedApps }
    }

    fun selectAll() {
        if (isMultiSelectMode) {
            selectedApps.addAll(currentList.map { it.packageName })
            notifyDataSetChanged()
            onMultiSelectedAppsChanged(getSelectedApps())
        }
    }

    fun clearSelection() {
        selectedApps.clear()
        notifyDataSetChanged()
        onMultiSelectedAppsChanged(getSelectedApps())
    }

    fun toggleAppSelection(appInfo: AppInfo, bindingAdapterPosition: Int, isSelected: Boolean) {
        if (isMultiSelectMode) {
            if (isSelected) {
                selectedApps.add(appInfo.packageName)
            } else {
                selectedApps.remove(appInfo.packageName)
            }
            onMultiSelectedAppsChanged(getSelectedApps())
            notifyItemChanged(bindingAdapterPosition)
        }
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
        holder.bind(
            getItem(position),
            isMultiSelectMode,
            getItem(position).packageName in selectedApps,
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
