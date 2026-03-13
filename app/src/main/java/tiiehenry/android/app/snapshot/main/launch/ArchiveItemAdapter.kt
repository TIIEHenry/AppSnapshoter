package tiiehenry.android.app.snapshot.main.launch

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.data.MetaInfoHelper
import tiiehenry.android.app.snapshot.databinding.ItemArchiveBinding
import tiiehenry.android.app.snapshot.utils.ArchiveRenameHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ArchiveItemAdapter(
    private val onItemClick: (ArchiveItem, Boolean) -> Unit,
    private val onDeleteClick: (ArchiveItem) -> Unit,
    private val onRenameSuccess: ((oldName: String, newName: String) -> Unit)? = null,
    private val onAdvancedRestoreClick: ((ArchiveItem, Set<String>) -> Unit)? = null
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

        private val btnLock: ImageView = binding.root.findViewById(R.id.btn_lock)

        fun bind(item: ArchiveItem) {
            binding.archiveName.text = item.name

            // 更新锁定按钮图标和颜色
            updateLockButtonUI(item)

            // 锁定按钮点击事件
            btnLock.setOnClickListener {
                toggleArchiveLock(item)
            }

            // 根据删除模式更新UI
            if (deleteMode) {
                // 删除模式：红色图标和文字
                binding.archiveIcon.setImageResource(R.drawable.delete_forever_outline)
                binding.archiveIcon.setColorFilter(Color.RED)
                binding.archiveName.setTextColor(Color.RED)
                btnLock.setColorFilter(Color.GRAY)
                btnLock.isEnabled = false

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
                btnLock.isEnabled = true

                binding.archiveIcon.setOnClickListener {
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

        /**
         * 更新锁定按钮UI
         */
        private fun updateLockButtonUI(item: ArchiveItem) {
            if (item.metaInfo.isLocked) {
                btnLock.setImageResource(R.drawable.lock_outline)
                btnLock.setColorFilter(Color.parseColor("#FF9800")) // 橙色表示已锁定
            } else {
                btnLock.setImageResource(R.drawable.lock_outline)
                btnLock.setColorFilter(Color.GRAY) // 灰色表示未锁定
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
                .setNeutralButton("重命名") { _, _ ->
                    showRenameDialog(item, context)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("高级恢复") { _, _ ->
                    showAdvancedRestoreDialog(item, context)
                }
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

        /**
         * 显示重命名对话框
         */
        private fun showRenameDialog(item: ArchiveItem, context: Context) {
            val input = android.widget.EditText(context)
            input.setText(item.name)
            input.setSelection(item.name.length)

            AlertDialog.Builder(context)
                .setTitle("重命名存档")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val newName = input.text.toString().trim()
                    if (newName.isNotEmpty() && newName != item.name) {
                        // 执行重命名操作
                        (binding.root.context as? LifecycleOwner)?.lifecycleScope?.launch {
                            val fs = SnapshotApp.getInstance().fileSystem
                            val success = ArchiveRenameHelper.renameArchive(
                                fs,
                                item.path,
                                item.name,
                                newName
                            )

                            withContext(Dispatchers.Main) {
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        "存档 '${item.name}' 已重命名为 '$newName'",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    // 通知UI更新
                                    onRenameSuccess?.invoke(item.name, newName)
                                } else {
                                    Toast.makeText(
                                        context,
                                        "重命名失败",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    } else if (newName == item.name) {
                        Toast.makeText(
                            context,
                            "新名称与原名称相同",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        /**
         * 切换存档锁定状态
         */
        private fun toggleArchiveLock(item: ArchiveItem) {
            val newLockState = !item.metaInfo.isLocked
            val context = binding.root.context

            (binding.root.context as? LifecycleOwner)?.lifecycleScope?.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        // 更新 MetaInfo 的锁定状态
                        item.metaInfo.setLocked(newLockState)
                        // 保存到文件
                        MetaInfoHelper.writeToArchive(
                            item.metaInfo,
                            item.path,
                            true
                        )
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }

                withContext(Dispatchers.Main) {
                    if (success) {
                        // 更新按钮UI
                        updateLockButtonUI(item)
                        val message = if (newLockState) "存档已锁定，不会被自动清理" else "存档已解锁"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    } else {
                        // 恢复原来的状态
                        item.metaInfo.setLocked(!newLockState)
                        Toast.makeText(context, "锁定状态保存失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        /**
         * 显示高级恢复对话框，让用户选择要恢复的数据类型
         */
        private fun showAdvancedRestoreDialog(item: ArchiveItem, context: Context) {
            if (onAdvancedRestoreClick == null) {
                Toast.makeText(context, "高级恢复功能未配置", Toast.LENGTH_SHORT).show()
                return
            }

            val dataItems = item.dataItems
            if (dataItems.isEmpty()) {
                Toast.makeText(context, "没有可恢复的数据项", Toast.LENGTH_SHORT).show()
                return
            }

            // 数据类型名称映射
            val dataTypeNames = mapOf(
                "apk" to "APK安装包",
                "data" to "应用数据(data)",
                "user" to "用户数据(user)",
                "user_de" to "用户DE数据(user_de)",
                "obb" to "OBB数据",
                "media" to "外部媒体数据(media)"
            )

            // 构建选项列表
            val options = dataItems.map { dataItem ->
                val displayName = dataTypeNames[dataItem.name] ?: dataItem.name
                val sizeStr = formatFileSize(dataItem.targetSize)
                "$displayName ($sizeStr)"
            }.toTypedArray()

            // 默认全部选中
            val checkedItems = BooleanArray(options.size) { true }

            AlertDialog.Builder(context)
                .setTitle("选择要恢复的数据")
                .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                .setPositiveButton("开始恢复") { _, _ ->
                    // 收集选中的数据项名称
                    val selectedTypes = mutableSetOf<String>()
                    checkedItems.forEachIndexed { index, isChecked ->
                        if (isChecked) {
                            selectedTypes.add(dataItems[index].name)
                        }
                    }

                    if (selectedTypes.isEmpty()) {
                        Toast.makeText(context, "请至少选择一项数据", Toast.LENGTH_SHORT).show()
                    } else {
                        onAdvancedRestoreClick.invoke(item, selectedTypes)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
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