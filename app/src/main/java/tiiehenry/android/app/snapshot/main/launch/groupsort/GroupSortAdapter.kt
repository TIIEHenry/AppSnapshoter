package tiiehenry.android.app.snapshot.main.launch.groupsort

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshot.databinding.ItemSortGroupBinding

class GroupSortAdapter(
    private val onItemMove: (Int, Int) -> Unit,
    private val onToggleExpand: (Int) -> Unit,
) : ListAdapter<SortableItem, GroupSortAdapter.ViewHolder>(ItemDiffCallback()) {

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        onItemMove.invoke(fromPosition, toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSortGroupBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false,
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onToggleExpand)
    }

    class ViewHolder(
        private val binding: ItemSortGroupBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: SortableItem, onToggleExpand: (Int) -> Unit) {
            val density = binding.root.resources.displayMetrics.density
            when (item) {
                is SortableItem.Root -> {
                    binding.root.setPadding(
                        (16 * density).toInt(),
                        binding.root.paddingTop,
                        (16 * density).toInt(),
                        binding.root.paddingBottom,
                    )
                    val suffix = when {
                        !item.expandable -> ""
                        item.expanded -> " ▾"
                        else -> " ▸"
                    }
                    binding.tvGroupName.text = item.label + suffix
                    binding.root.setOnClickListener {
                        if (item.expandable) {
                            val pos = bindingAdapterPosition
                            if (pos != RecyclerView.NO_POSITION) onToggleExpand(pos)
                        }
                    }
                }
                is SortableItem.Member -> {
                    binding.root.setPadding(
                        (36 * density).toInt(),
                        binding.root.paddingTop,
                        (16 * density).toInt(),
                        binding.root.paddingBottom,
                    )
                    binding.tvGroupName.text = item.label
                    binding.root.setOnClickListener(null)
                }
            }
        }
    }

    class ItemDiffCallback : DiffUtil.ItemCallback<SortableItem>() {
        override fun areItemsTheSame(oldItem: SortableItem, newItem: SortableItem): Boolean {
            return when {
                oldItem is SortableItem.Root && newItem is SortableItem.Root ->
                    oldItem.root == newItem.root
                oldItem is SortableItem.Member && newItem is SortableItem.Member ->
                    oldItem.setId == newItem.setId && oldItem.basename == newItem.basename
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: SortableItem, newItem: SortableItem): Boolean {
            return oldItem == newItem
        }
    }
}
