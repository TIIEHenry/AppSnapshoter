package tiiehenry.android.app.snapshot.main.launch

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.data.MetaInfoHelper
import tiiehenry.android.app.snapshot.data.bean.MetaDataItem
import tiiehenry.android.app.snapshot.databinding.ItemArchiveBinding
import tiiehenry.android.app.snapshot.utils.ArchiveRenameHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 存档列表适配器
 * @param onItemClick 存档点击回调 (archiveItem, needConfirm)
 * @param onDeleteClick 删除按钮点击回调
 * @param onRenameSuccess 重命名成功回调 (oldName, newName)
 * @param onAdvancedRestoreClick 高级恢复点击回调 (archiveItem, selectedTypes)
 */
class ArchiveItemAdapter(
    private val onItemClick: (ArchiveItem, Boolean) -> Unit,
    private val onDeleteClick: (ArchiveItem) -> Unit,
    private val onRenameSuccess: ((String, String) -> Unit)? = null,
    private val onAdvancedRestoreClick: ((ArchiveItem, Set<String>) -> Unit)? = null
) : ListAdapter<ArchiveItem, ArchiveItemAdapter.ViewHolder>(ArchiveItemDiffCallback()) {

    private var deleteMode = false

    fun setDeleteMode(mode: Boolean) {
        deleteMode = mode
        notifyDataSetChanged()
    }

    fun toggleDeleteMode(): Boolean {
        deleteMode = !deleteMode
        notifyDataSetChanged()
        return deleteMode
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
    
        private val btnLock: ImageView = binding.btnLock
    
        fun bind(item: ArchiveItem) {
            binding.archiveName.text = item.name
            updateLockButtonUI(item)
            setupClickListeners(item)
            updateUIMode(item)
            setupLongClickListeners(item)
        }
    
        /**
         * 设置点击事件监听器
         */
        private fun setupClickListeners(item: ArchiveItem) {
            btnLock.setOnClickListener { toggleArchiveLock(item) }
            btnLock.setOnLongClickListener {
                showRenameDialog(item, it.context)
                true
            }
        }
    
        /**
         * 根据模式更新 UI
         */
        private fun updateUIMode(item: ArchiveItem) {
            if (deleteMode) {
                setupDeleteModeUI(item)
            } else {
                setupNormalModeUI(item)
            }
        }
    
        /**
         * 设置删除模式的 UI
         */
        private fun setupDeleteModeUI(item: ArchiveItem) {
            binding.archiveIcon.setImageResource(R.drawable.delete_forever_outline)
            binding.archiveIcon.setColorFilter(Color.RED)
            binding.archiveName.setTextColor(Color.RED)
            btnLock.setColorFilter(Color.GRAY)
            btnLock.isEnabled = false
    
            binding.archiveIcon.setOnClickListener { onDeleteClick.invoke(item) }
            binding.archiveIcon.setOnLongClickListener(null)
            binding.root.setOnClickListener {
                showDeleteConfirmDialog(item, binding.root.context, item.name)
            }
        }
    
        /**
         * 设置正常模式的 UI
         */
        private fun setupNormalModeUI(item: ArchiveItem) {
            binding.archiveIcon.setImageResource(R.drawable.archive_arrow_up_outline)
            binding.archiveIcon.setColorFilter(Color.BLACK)
            binding.archiveName.setTextColor(Color.BLACK)
            btnLock.isEnabled = true
    
            binding.archiveIcon.setOnClickListener { onItemClick.invoke(item, false) }
            binding.archiveIcon.setOnLongClickListener {
                showAdvancedRestoreDialog(item, it.context)
                true
            }
            binding.root.setOnClickListener { onItemClick.invoke(item, true) }
        }
    
        /**
         * 设置长按事件监听器
         */
        private fun setupLongClickListeners(item: ArchiveItem) {
            binding.root.setOnLongClickListener {
                showArchiveInfoDialog(item, it.context)
                true
            }
        }

        /**
         * 更新锁定按钮 UI
         */
        private fun updateLockButtonUI(item: ArchiveItem) {
            if (item.metaInfo.isLocked) {
                btnLock.setImageResource(R.drawable.lock_outline)
                btnLock.setColorFilter(Color.parseColor("#FF9800"))
            } else {
                btnLock.setImageResource(R.drawable.lock_outline)
                btnLock.setColorFilter(Color.GRAY)
            }
        }
        
        private fun showDeleteConfirmDialog(item: ArchiveItem, context: Context, itemName: String) {
            AlertDialog.Builder(context)
                .setTitle("确认删除")
                .setMessage("确定要删除 '$itemName' 吗？")
                .setPositiveButton("删除") { _, _ -> onDeleteClick.invoke(item) }
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
                    item.dataItems.forEachIndexed { index, dataItem ->
                        append("${index + 1}. ${dataItem.name}: ${formatFileSize(dataItem.targetSize)}")
                        if (dataItem.algorithm.isNotBlank()) {
                            append(" [${dataItem.algorithm}]\n")
                        } else {
                            append("\n")
                        }
                    }
                }

                if (item.extraItems.isNotEmpty()) {
                    append("\n额外数据项:\n")
                    item.extraItems.toList().forEachIndexed { index, (key, value) ->
                        append("${index + 1}. ${key.name}: ${formatFileSize(key.targetSize)}")
                        if (key.algorithm.isNotBlank()) {
                            append(" [${key.algorithm}]\n")
                        } else {
                            append("\n")
                        }
                        append("$value\n")
                    }
                }
            }

            MaterialAlertDialogBuilder(context)
                .setTitle("存档信息 - ${item.name}")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton("更多") { _, _ ->
                    showMorePopupMenu(binding.root, item)
                }
                .show()
        }

        /**
         * 显示更多操作弹出菜单
         */
        private fun showMorePopupMenu(anchor: View, item: ArchiveItem) {
            val popupMenu = PopupMenu(anchor.context, anchor).apply {
                menu.add(0, 1, 0, "重命名")
                menu.add(0, 2, 1, "高级恢复")
                setOnMenuItemClickListener { menuItem: MenuItem ->
                    when (menuItem.itemId) {
                        1 -> {
                            showRenameDialog(item, anchor.context)
                            true
                        }
                        2 -> {
                            showAdvancedRestoreDialog(item, anchor.context)
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
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
            val input = android.widget.EditText(context).apply {
                setText(item.name)
                setSelection(item.name.length)
            }
        
            AlertDialog.Builder(context)
                .setTitle("重命名存档")
                .setView(input)
                .setPositiveButton("确定") { _, _ ->
                    val newName = input.text.toString().trim()
                    when {
                        newName.isNotEmpty() && newName != item.name -> {
                            executeRename(item, context, newName)
                        }
                        newName == item.name -> {
                            Toast.makeText(context, "新名称与原名称相同", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        /**
         * 执行重命名操作
         */
        private fun executeRename(item: ArchiveItem, context: Context, newName: String) {
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
                        onRenameSuccess?.invoke(item.name, newName)
                    } else {
                        Toast.makeText(context, "重命名失败", Toast.LENGTH_SHORT).show()
                    }
                }
            }
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
                        item.metaInfo.setLocked(newLockState)
                        MetaInfoHelper.writeToArchive(item.metaInfo, item.path, true)
                        true
                    } catch (e: Exception) {
                        e.printStackTrace()
                        false
                    }
                }
        
                withContext(Dispatchers.Main) {
                    if (success) {
                        updateLockButtonUI(item)
                        val message = if (newLockState) "存档已锁定，不会被自动清理" else "存档已解锁"
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    } else {
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

            val allRestoreOptions = buildRestoreOptions(item)
            
            if (allRestoreOptions.isEmpty()) {
                Toast.makeText(context, "没有可恢复的数据项", Toast.LENGTH_SHORT).show()
                return
            }

            val options = buildRestoreOptionNames(allRestoreOptions)
            val checkedItems = BooleanArray(options.size) { true }

            AlertDialog.Builder(context)
                .setTitle("选择要恢复的数据")
                .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                .setPositiveButton("开始恢复") { _, _ ->
                    val selectedTypes = collectSelectedTypes(allRestoreOptions, checkedItems)
                    if (selectedTypes.isEmpty()) {
                        Toast.makeText(context, "请至少选择一项数据", Toast.LENGTH_SHORT).show()
                    } else {
                        onAdvancedRestoreClick.invoke(item, selectedTypes)
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        /**
         * 构建恢复选项列表
         */
        private fun buildRestoreOptions(item: ArchiveItem): MutableList<Pair<MetaDataItem, String?>> {
            val allRestoreOptions = mutableListOf<Pair<MetaDataItem, String?>>()
            
            item.dataItems.forEach { dataItem ->
                allRestoreOptions.add(Pair(dataItem, null))
            }
            
            item.extraItems.forEach { (dataItem, path) ->
                allRestoreOptions.add(Pair(dataItem, path))
            }
            
            return allRestoreOptions
        }

        /**
         * 构建恢复选项名称数组
         */
        private fun buildRestoreOptionNames(
            allRestoreOptions: List<Pair<MetaDataItem, String?>>
        ): Array<String> {
            val dataTypeNames = mapOf(
                "apk" to "APK 安装包",
                "data" to "应用数据 (data)",
                "user" to "用户数据 (user)",
                "user_de" to "用户 DE 数据 (user_de)",
                "obb" to "OBB 数据",
                "media" to "外部媒体数据 (media)"
            )

            return allRestoreOptions.map { (dataItem, extraPath) ->
                var displayName = dataTypeNames[dataItem.name] ?: dataItem.name
                val sizeStr = formatFileSize(dataItem.targetSize)

                if (extraPath != null) {
                    val shortPath = extraPath.substringAfterLast("/").take(20)
                    "$displayName [$shortPath] ($sizeStr)"
                } else {
                    "$displayName ($sizeStr)"
                }
            }.toTypedArray()
        }

        /**
         * 收集选中的数据类型
         */
        private fun collectSelectedTypes(
            allRestoreOptions: List<Pair<MetaDataItem, String?>>,
            checkedItems: BooleanArray
        ): MutableSet<String> {
            val selectedTypes = mutableSetOf<String>()
            checkedItems.forEachIndexed { index, isChecked ->
                if (isChecked) {
                    selectedTypes.add(allRestoreOptions[index].first.name)
                }
            }
            return selectedTypes
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