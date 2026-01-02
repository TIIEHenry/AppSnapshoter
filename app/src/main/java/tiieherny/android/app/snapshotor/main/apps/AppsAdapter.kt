package tiieherny.android.app.snapshotor.main.apps

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import tiieherny.android.app.snapshotor.R
import tiieherny.android.app.snapshotor.app.AppInfo

class AppsAdapter(
    private val onItemClick: (AppInfo) -> Unit
) : ListAdapter<AppInfo, AppsAdapter.ViewHolder>(AppDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_list, parent, false)
        return ViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val onItemClick: (AppInfo) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val iconImageView: ImageView = itemView.findViewById(R.id.app_icon)
        private val nameTextView: TextView = itemView.findViewById(R.id.app_name)
        private val packageTextView: TextView = itemView.findViewById(R.id.app_package)
        private val versionTextView: TextView = itemView.findViewById(R.id.app_version)

        @SuppressLint("SetTextI18n")
        fun bind(appInfo: AppInfo) {
            nameTextView.text = appInfo.label
            packageTextView.text = appInfo.packageName
            versionTextView.text = "${appInfo.versionName} (${appInfo.versionCode})"

            // 使用Glide加载图标
            if (appInfo.icon != null) {
                Glide.with(itemView.context)
                    .load(appInfo.icon)
                    .into(iconImageView)
            } else {
                iconImageView.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            itemView.setOnClickListener {
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
