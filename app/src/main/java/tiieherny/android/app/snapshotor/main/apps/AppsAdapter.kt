package tiieherny.android.app.snapshotor.main.apps

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import tiieherny.android.app.snapshotor.R
import tiieherny.android.app.snapshotor.app.AppInfo
import tiieherny.android.app.snapshotor.databinding.ItemAppListBinding

class AppsAdapter(
    private val onItemClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppsAdapter.ViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAppListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemAppListBinding,
        private val onItemClick: (AppInfo) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(appInfo: AppInfo) {
            binding.appName.text = appInfo.label
            binding.appPackage.text = appInfo.packageName
            binding.appVersion.text = "${appInfo.versionName} (${appInfo.versionCode})"

            // 使用Glide加载图标
            if (appInfo.icon != null) {
                Glide.with(binding.root.context)
                    .load(appInfo.icon)
                    .into(binding.appIcon)
            } else {
                binding.appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            binding.root.setOnClickListener {
                onItemClick(appInfo)
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