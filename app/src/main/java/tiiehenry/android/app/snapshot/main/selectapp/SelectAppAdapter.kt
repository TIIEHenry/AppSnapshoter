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

class SelectAppAdapter(
    private val onItemClick: (AppInfo) -> Unit,
    private val onMultiSelectModeChanged: (Boolean) -> Unit = {},
    private val onMultiSelectedAppsChanged: (List<AppInfo>) -> Unit = {}
) : ListAdapter<AppInfo, SelectAppAdapter.ViewHolder>(AppDiffCallback()) {

    private var isMultiSelectMode = false
    private val selectedApps = mutableSetOf<String>() // 使用包名作为唯一标识

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

    fun toggleAppSelection(appInfo: AppInfo) {
        if (isMultiSelectMode) {
            if (selectedApps.contains(appInfo.packageName)) {
                selectedApps.remove(appInfo.packageName)
            } else {
                selectedApps.add(appInfo.packageName)
            }
            notifyItemChanged(getItemPosition(appInfo))
            onMultiSelectedAppsChanged(getSelectedApps())
        }
    }

    private fun getItemPosition(appInfo: AppInfo): Int {
        return currentList.indexOfFirst { it.packageName == appInfo.packageName }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onItemClick, ::toggleAppSelection, ::toggleMultiSelectMode)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), isMultiSelectMode, getItem(position).packageName in selectedApps)
    }

    class ViewHolder(
        private val binding: ItemAppListBinding,
        private val onItemClick: (AppInfo) -> Unit,
        private val onAppToggle: (AppInfo) -> Unit,
        private val onLongPressEnterMultiSelectMode: () -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(appInfo: AppInfo, isMultiSelectMode: Boolean, isSelected: Boolean) {
            binding.appName.text = appInfo.label
            binding.appPackage.text = appInfo.packageName
            binding.appVersion.text = "${appInfo.versionName} (${appInfo.versionCode})"

            // 使用Glide加载图标
            Glide.with(binding.root.context)
                .load(appInfo.icon)
                .into(binding.appIcon)

            // 设置多选模式下的UI状态
            if (isMultiSelectMode) {
                binding.appCheckbox.visibility = View.VISIBLE
                binding.appCheckbox.isChecked = isSelected
                binding.root.isActivated = isSelected
            } else {
                binding.appCheckbox.visibility = View.GONE
                binding.root.isActivated = false
            }

            // 设置点击事件
            binding.root.setOnClickListener {
                if (isMultiSelectMode) {
                    onAppToggle(appInfo)
                } else {
                    onItemClick(appInfo)
                }
            }

            // 设置长按事件进入多选模式
            binding.root.setOnLongClickListener {
                onLongPressEnterMultiSelectMode() // 长按时进入多选模式
                true // 消费长按事件
            }
        }
    }

    private class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
        override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
            return oldItem == newItem
        }
    }
}