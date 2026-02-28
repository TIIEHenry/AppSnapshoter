package tiiehenry.android.app.snapshotor.main.launch

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshotor.R
import tiiehenry.android.app.snapshotor.archive.ArchiveItem
import tiiehenry.android.app.snapshotor.databinding.ItemArchiveBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArchiveItemAdapter(
    private val onItemClick: (ArchiveItem, Boolean) -> Unit,
    private val onDeleteClick: (ArchiveItem) -> Unit
) : ListAdapter<ArchiveItem, ArchiveItemAdapter.ViewHolder>(ArchiveItemDiffCallback()) {

    // 添加删除模式标志
    private var deleteMode = false

    // 设置删除模式
    fun setDeleteMode(mode: Boolean) {
        deleteMode = mode
        notifyDataSetChanged()
    }

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

            // 根据删除模式更新UI
            if (deleteMode) {
                // 删除模式：红色图标和文字
                binding.archiveIcon.setImageResource(R.drawable.delete_forever_outline)
                binding.archiveIcon.setColorFilter(Color.RED)
                binding.archiveName.setTextColor(Color.RED)

                // 删除模式下：点击图标直接删除，无需弹框确认
                binding.archiveIcon.setOnClickListener {
                    onDeleteClick.invoke(item)
                }

                // 删除模式下：点击item区域需要确认删除
                binding.root.setOnClickListener {
                    showDeleteConfirmDialog(item, binding.root.context, item.name)
                }
            } else {
                // 正常模式：默认图标和文字颜色
                binding.archiveIcon.setImageResource(R.drawable.archive_arrow_up_outline)
                binding.archiveIcon.setColorFilter(Color.BLACK)
                binding.archiveName.setTextColor(Color.BLACK)

                binding.archiveIcon.setOnClickListener{
                    onItemClick.invoke(item, true)
                }
                // 正常模式下的点击事件
                binding.root.setOnClickListener {
                    onItemClick.invoke(item, false)
                }
            }

            binding.root.setOnLongClickListener {
                showArchiveInfoDialog(item, it.context)
                true
            }
        }

        private fun showDeleteConfirmDialog(item: ArchiveItem, context: Context, itemName: String) {
            AlertDialog.Builder(context)
                .setTitle("确认删除")
                .setMessage("确定要删除 '$itemName' 吗？")
                .setPositiveButton("删除") { _, _ ->
                    onDeleteClick.invoke(item)
                }
                .setNegativeButton("取消", null)
                .show()
        }

        private fun showArchiveInfoDialog(item: ArchiveItem, context: Context) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val makeTimeStr = dateFormat.format(Date(item.metaInfo.makeTime))

            val dataSize = item.dataItems.size
            val totalSize = item.dataItems.sumOf { it.targetSize }
            val uncompressedSize = item.dataItems.sumOf { it.originSize }

            val message = buildString {
                append("应用名称: ${item.appInfo.label}\n")
                append("包名: ${item.metaInfo.packageInfo.packageName}\n")
                append("版本: ${item.metaInfo.packageInfo.versionName} (${item.metaInfo.packageInfo.versionCode})\n")
                append("用户ID: ${item.metaInfo.userId}\n")
                append("备份时间: $makeTimeStr\n")
                append("数据项数量: $dataSize\n")
                append("压缩后大小: ${formatFileSize(totalSize)}\n")
                append("原始大小: ${formatFileSize(uncompressedSize)}\n")
                append("压缩率: ${calculateCompressionRatio(totalSize, uncompressedSize)}\n\n")

                if (item.dataItems.isNotEmpty()) {
                    append("数据项详情:\n")
                    item.dataItems.take(5).forEachIndexed { index, dataItem ->
                        append("${index + 1}. ${dataItem.name}: ${formatFileSize(dataItem.targetSize)}")
                        if (dataItem.algorithm.isNotBlank()) {
                            append(" [${dataItem.algorithm}]\n")
                        } else {
                            append("\n")
                        }
                    }
                    if (item.dataItems.size > 5) {
                        append("... 还有 ${item.dataItems.size - 5} 项\n")
                    }
                }
            }

            AlertDialog.Builder(context)
                .setTitle("存档信息 - ${item.name}")
                .setMessage(message)
                .setPositiveButton("确定", null)
                .show()
        }

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 * 1024 -> String.format(
                    "%.2f GB",
                    bytes / (1024.0 * 1024.0 * 1024.0)
                )

                bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        }

        private fun calculateCompressionRatio(compressedSize: Long, originalSize: Long): String {
            return if (originalSize > 0) {
                val ratio = (1 - compressedSize.toDouble() / originalSize) * 100
                String.format("%.1f%%", ratio)
            } else {
                "0%"
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