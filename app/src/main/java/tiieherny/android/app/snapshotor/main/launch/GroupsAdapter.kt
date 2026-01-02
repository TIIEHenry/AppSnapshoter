package tiieherny.android.app.snapshotor.main.launch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tiieherny.android.app.snapshotor.R
import tiieherny.android.app.snapshotor.group.SnapGroup

class GroupsAdapter(
    private val viewModel: LauncherViewModel
) : ListAdapter<SnapGroup, GroupsAdapter.GroupViewHolder>(GroupDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return GroupViewHolder(view, viewModel)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class GroupViewHolder(
        itemView: View,
        private val viewModel: LauncherViewModel
    ) : RecyclerView.ViewHolder(itemView) {
        
        private val titleTextView: TextView = itemView.findViewById(R.id.group_title)
        private val addButton: Button = itemView.findViewById(R.id.btn_add)
        private val moveButton: Button = itemView.findViewById(R.id.btn_move)
        private val progressBar: ProgressBar = itemView.findViewById(R.id.progress_bar)
        private val groupRecyclerView: RecyclerView = itemView.findViewById(R.id.group_recycler_view)

        fun bind(group: SnapGroup) {
            titleTextView.text = group.name
            
            // 设置GridLayout，4列
            groupRecyclerView.layoutManager = GridLayoutManager(itemView.context, 4)
            
            val adapter = GroupItemAdapter(viewModel, group)
            groupRecyclerView.adapter = adapter
            adapter.submitList(group.archives)

            // 控制加载状态
            if (group.archives.isEmpty()) {
                progressBar.visibility = View.GONE
            } else {
                progressBar.visibility = View.GONE
            }

            addButton.setOnClickListener {
                // TODO: 添加应用到分组
            }

            moveButton.setOnClickListener {
                // TODO: 进入排序状态
            }
        }
    }

    private class GroupDiffCallback : DiffUtil.ItemCallback<SnapGroup>() {
        override fun areItemsTheSame(oldItem: SnapGroup, newItem: SnapGroup): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SnapGroup, newItem: SnapGroup): Boolean {
            return oldItem == newItem
        }
    }
}
