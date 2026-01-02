package tiieherny.android.app.snapshotor.main.launch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tiieherny.android.app.snapshotor.R
import tiieherny.android.app.snapshotor.archive.ArchieveItem
import tiieherny.android.app.snapshotor.group.SnapGroup

class GroupItemAdapter(
    private val viewModel: LauncherViewModel,
    private val group: SnapGroup
) : ListAdapter<ArchieveItem, GroupItemAdapter.ViewHolder>(ItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app, parent, false)
        return ViewHolder(view, viewModel, group)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        itemView: View,
        private val viewModel: LauncherViewModel,
        private val group: SnapGroup
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val iconImageView: ImageView = itemView.findViewById(R.id.app_icon)
        private val nameTextView: TextView = itemView.findViewById(R.id.app_name)

        fun bind(item: ArchieveItem) {
            nameTextView.text = item.appInfo.label
            
            // 使用Glide加载图标
            if (item.appInfo.icon != null) {
                com.bumptech.glide.Glide.with(itemView.context)
                    .load(item.appInfo.icon)
                    .into(iconImageView)
            } else {
                iconImageView.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            itemView.setOnClickListener {
                viewModel.onGroupItemClicked(group.id, group.mmkv, item.appInfo.packageName, item)
            }

            itemView.setOnLongClickListener {
                // TODO: 显示悬浮菜单
                true
            }
        }
    }

    private class ItemDiffCallback : DiffUtil.ItemCallback<ArchieveItem>() {
        override fun areItemsTheSame(oldItem: ArchieveItem, newItem: ArchieveItem): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: ArchieveItem, newItem: ArchieveItem): Boolean {
            return oldItem == newItem
        }
    }
}
