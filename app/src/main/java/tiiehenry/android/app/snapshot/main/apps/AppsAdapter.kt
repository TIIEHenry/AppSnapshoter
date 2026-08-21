package tiiehenry.android.app.snapshot.main.apps

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.databinding.ItemAppListBinding
import tiiehenry.android.app.snapshot.group.AppGroupMembership

class AppsAdapter(
    private val membershipIndexProvider: () -> Map<String, AppGroupMembership>,
    private val onItemClick: (AppInfo) -> Unit,
    private val onItemLongClick: (AppInfo, AppGroupMembership) -> Unit,
) : ListAdapter<AppInfo, AppsAdapter.ViewHolder>(AppDiffCallback()) {

    private var membershipIndex: Map<String, AppGroupMembership> = emptyMap()

    override fun submitList(list: List<AppInfo>?) {
        membershipIndex = membershipIndexProvider()
        super.submitList(list)
    }

    fun refreshMembership() {
        membershipIndex = membershipIndexProvider()
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onItemClick, onItemLongClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), membershipIndex)
    }

    class ViewHolder(
        private val binding: ItemAppListBinding,
        private val onItemClick: (AppInfo) -> Unit,
        private val onItemLongClick: (AppInfo, AppGroupMembership) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(appInfo: AppInfo, membershipIndex: Map<String, AppGroupMembership>) {
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

            binding.root.setOnClickListener { onItemClick(appInfo) }
            binding.root.setOnLongClickListener {
                onItemLongClick(appInfo, membership)
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
