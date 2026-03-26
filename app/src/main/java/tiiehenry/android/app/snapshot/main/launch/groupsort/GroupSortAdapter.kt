package tiiehenry.android.app.snapshot.main.launch.groupsort

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshot.databinding.ItemSortGroupBinding
import tiiehenry.android.app.snapshot.group.SnapGroup

class GroupSortAdapter(
    private val onItemMove: (Int, Int) -> Unit
) : ListAdapter<SnapGroup, GroupSortAdapter.ViewHolder>(GroupDiffCallback()) {

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        onItemMove.invoke(fromPosition, toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSortGroupBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemSortGroupBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(group: SnapGroup) {
            binding.tvGroupName.text = group.name
        }
    }

    class GroupDiffCallback : DiffUtil.ItemCallback<SnapGroup>() {
        override fun areItemsTheSame(oldItem: SnapGroup, newItem: SnapGroup): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SnapGroup, newItem: SnapGroup): Boolean {
            return oldItem.name == newItem.name
        }
    }
}