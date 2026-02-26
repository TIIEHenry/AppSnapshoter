package tiiehenry.android.app.snapshotor.main.launch

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshotor.archive.ArchiveItem
import tiiehenry.android.app.snapshotor.databinding.ItemArchiveBinding

class ArchiveItemAdapter(
    private val onItemClick: (ArchiveItem) -> Unit
) : ListAdapter<ArchiveItem, ArchiveItemAdapter.ViewHolder>(ArchiveItemDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemArchiveBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemArchiveBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(item: ArchiveItem) {
            binding.archiveName.text = item.name
            
            binding.root.setOnClickListener {
                onItemClick.invoke(item)
            }
        }
    }

    private class ArchiveItemDiffCallback : DiffUtil.ItemCallback<ArchiveItem>() {
        override fun areItemsTheSame(oldItem: ArchiveItem, newItem: ArchiveItem): Boolean {
            return oldItem.name == newItem.name
        }

        override fun areContentsTheSame(oldItem: ArchiveItem, newItem: ArchiveItem): Boolean {
            return oldItem == newItem
        }
    }
}