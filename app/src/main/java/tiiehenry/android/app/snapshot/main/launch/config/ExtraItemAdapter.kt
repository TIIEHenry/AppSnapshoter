package tiiehenry.android.app.snapshot.main.launch.config

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.config.ExtraCompressItem
import tiiehenry.android.app.snapshot.databinding.ExtraItemLayoutBinding

/**
 * 额外项目 RecyclerView 适配器
 */
class ExtraItemAdapter(
    private val context: Context,
    private val onItemClick: (ExtraCompressItem) -> Unit,
    private val onDeleteClick: (ExtraCompressItem) -> Unit,
    private val onEnabledChange: ((ExtraCompressItem, Boolean) -> Unit)? = null
) : ListAdapter<ExtraCompressItem, ExtraItemAdapter.ViewHolder>(DiffCallback()) {

    private var enabled = true

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ExtraItemLayoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        notifyDataSetChanged()
    }

    inner class ViewHolder(
        private val binding: ExtraItemLayoutBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                if (enabled) {
                    getItem(adapterPosition)?.let(onItemClick)
                }
            }
            
            binding.btnDelete.setOnClickListener {
                if (enabled) {
                    getItem(adapterPosition)?.let(onDeleteClick)
                }
            }
        }

        fun bind(item: ExtraCompressItem) {
            with(binding) {
                textItemName.text = item.name
                textItemPath.text = item.path
                
                // 显示排除模式数量
                val excludesCount = item.excludePatterns.size
                textItemExcludes.text = if (excludesCount > 0) {
                    context.getString(R.string.extra_item_excludes_count, excludesCount)
                } else {
                    context.getString(R.string.extra_item_no_excludes)
                }
                
                // 移除旧的监听器，避免重复触发
                checkboxEnabled.setOnCheckedChangeListener(null)
                
                // 设置勾选框状态
                checkboxEnabled.isChecked = item.isEnabled
                
                // 设置新的监听器
                checkboxEnabled.setOnCheckedChangeListener { _, isChecked ->
                    if (enabled) {
                        getItem(adapterPosition)?.let { currentItem ->
                            onEnabledChange?.invoke(currentItem, isChecked)
                        }
                    }
                }
                
                // 更新启用状态
                root.isEnabled = enabled
                textItemName.alpha = if (enabled) 1.0f else 0.5f
                textItemPath.alpha = if (enabled) 1.0f else 0.5f
                textItemExcludes.alpha = if (enabled) 1.0f else 0.5f
                btnDelete.isEnabled = enabled
                checkboxEnabled.isEnabled = enabled
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ExtraCompressItem>() {
        override fun areItemsTheSame(oldItem: ExtraCompressItem, newItem: ExtraCompressItem): Boolean {
            return oldItem.name == newItem.name && oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: ExtraCompressItem, newItem: ExtraCompressItem): Boolean {
            return oldItem.name == newItem.name &&
                    oldItem.path == newItem.path &&
                    oldItem.excludePatterns == newItem.excludePatterns &&
                    oldItem.isEnabled == newItem.isEnabled
        }
    }
}
