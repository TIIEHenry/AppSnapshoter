package tiiehenry.android.app.snapshot.main.launch.config

import android.content.Context
import android.os.Environment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import tiiehenry.android.app.snapshot.config.AppConfig
import tiiehenry.android.app.snapshot.config.ExtraCompressItem
import tiiehenry.android.app.snapshot.databinding.IncludeExtraItemsBinding
import tiiehenry.android.app.snapshot.main.launch.config.fragments.ExtraItemEditBottomSheet

/**
 * 额外项目管理器
 * 管理应用单独配置中的额外压缩项目
 */
class ExtraItemsManager(
    private val binding: IncludeExtraItemsBinding,
    private val context: Context,
    private val appConfig: AppConfig,
    private val fragmentManager: FragmentManager
) {
    private var extraItems = mutableListOf<ExtraCompressItem>()
    private lateinit var adapter: ExtraItemAdapter
    
    init {
        setupRecyclerView()
        setupListeners()
    }
    
    private fun setupRecyclerView() {
        adapter = ExtraItemAdapter(
            context = context,
            onItemClick = { item -> onExtraItemClick(item) },
            onDeleteClick = { item -> deleteExtraItem(item) },
            onEnabledChange = { item, isChecked -> onExtraItemEnabledChange(item, isChecked) }
        )
        
        binding.recyclerViewExtraItems.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@ExtraItemsManager.adapter
        }
    }
    
    private fun setupListeners() {
        binding.btnAddExtraItem.setOnClickListener {
            addNewExtraItem()
        }
    }
    
    /**
     * 加载配置
     */
    fun loadConfig() {
        extraItems.clear()
        extraItems.addAll(appConfig.extraItems)
        adapter.submitList(extraItems.toList())
    }
    
    /**
     * 保存配置
     */
    fun saveToConfig() {
        appConfig.saveExtraItems(extraItems)
    }
    
    /**
     * 添加新的额外项目
     */
    private fun addNewExtraItem() {
        // 显示编辑 BottomSheet 来添加新项目
        showEditBottomSheet(null)
    }
    
    /**
     * 显示编辑 BottomSheet
     */
    private fun showEditBottomSheet(item: ExtraCompressItem?) {
        // 获取应用数据目录作为根路径
        val rootPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        
        val bottomSheet = ExtraItemEditBottomSheet.newInstance(
            item = item,
            rootPath = rootPath
        )
        
        bottomSheet.setOnItemConfirmedListener { newItem ->
            if (item != null) {
                // 编辑模式：更新现有项目
                updateExtraItem(item, newItem)
            } else {
                // 新建模式：添加到列表
                extraItems.add(newItem)
                // 使用 submitList 更新 ListAdapter，触发 DiffUtil
                adapter.submitList(extraItems.toList())
                onItemsChanged()
            }
        }
        
        bottomSheet.show(fragmentManager, "extra_item_edit")
    }
    
    /**
     * 删除额外项目
     */
    private fun deleteExtraItem(item: ExtraCompressItem) {
        val index = extraItems.indexOf(item)
        if (index >= 0) {
            extraItems.removeAt(index)
            // 使用 submitList 更新 ListAdapter，触发 DiffUtil
            adapter.submitList(extraItems.toList())
            onItemsChanged()
        }
    }
    
    /**
     * 更新额外项目
     */
    fun updateExtraItem(oldItem: ExtraCompressItem, newItem: ExtraCompressItem) {
        val index = extraItems.indexOf(oldItem)
        if (index >= 0) {
            extraItems[index] = newItem
            // 使用 submitList 更新 ListAdapter，触发 DiffUtil
            adapter.submitList(extraItems.toList())
            onItemsChanged()
        }
    }
    
    /**
     * 点击额外项目项
     */
    private fun onExtraItemClick(item: ExtraCompressItem) {
        // 显示编辑 BottomSheet
        showEditBottomSheet(item)
    }
    
    /**
     * 额外项目启用状态变化
     */
    private fun onExtraItemEnabledChange(item: ExtraCompressItem, isChecked: Boolean) {
        // 找到对应的 item 并更新 enabled 状态
        val index = extraItems.indexOfFirst { 
            it.name == item.name && it.path == item.path 
        }
        if (index >= 0) {
            // 创建一个新的 ExtraCompressItem，更新 enabled 字段
            val updatedItem = item.copy()
            updatedItem.isEnabled = isChecked
            extraItems[index] = updatedItem
            
            // 使用 submitList 更新整个列表，触发 DiffUtil 和 RecyclerView 刷新
            adapter.submitList(extraItems.toList())
            onItemsChanged()
        }
    }
    
    /**
     * 列表变化时的回调
     */
    private fun onItemsChanged() {
        // 可以在这里添加变化监听器
    }
    
    /**
     * 设置启用状态
     */
    fun setEnabled(enabled: Boolean) {
        binding.btnAddExtraItem.isEnabled = enabled
        adapter.setEnabled(enabled)
    }
}
