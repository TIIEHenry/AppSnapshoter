package tiiehenry.android.app.snapshot.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.alibaba.fastjson2.JSON
import com.google.android.material.chip.Chip
import tiiehenry.android.app.snapshot.config.CompressItems
import tiiehenry.android.app.snapshot.config.ExcludeConfig
import tiiehenry.android.app.snapshot.databinding.IncludeExcludePatternsBinding
import tiiehenry.android.app.snapshot.ui.dialog.ExcludePatternBottomSheet
import com.google.android.material.R as MaterialR

class ExcludePatternsManager(
    private val binding: IncludeExcludePatternsBinding,
    private val context: Context,
    private var packageName: String = "",
    private var excludeConfig: ExcludeConfig? = null,
    private var fragmentManager: FragmentManager? = null
) {
    // 按压缩项目分类的排除模式映射
    private val itemPatterns = mutableMapOf<String, MutableList<String>>()
    private var onPatternsChangedListener: ((Map<String, List<String>>) -> Unit)? = null

    init {
        setupListeners()
    }

    /**
     * 设置包名（用于文件选择器的根路径）
     */
    fun setPackageName(name: String) {
        packageName = name
    }

    /**
     * 设置ExcludeConfig（用于保存排除模式）
     */
    fun setExcludeConfig(config: ExcludeConfig) {
        excludeConfig = config
    }

    /**
     * 设置FragmentManager（用于显示BottomSheet）
     */
    fun setFragmentManager(fm: FragmentManager) {
        fragmentManager = fm
    }

    private fun setupListeners() {
        // 添加按钮点击事件 - 直接显示BottomSheet，让用户选择压缩项目
        binding.btnBrowseExclude.setOnClickListener {
            showExcludePatternBottomSheet()
        }

        // 添加按钮长按事件 - 解析剪切板并导入排除列表
        binding.btnBrowseExclude.setOnLongClickListener {
            parseAndImportFromClipboard()
            true
        }

        // 复制按钮点击事件 - 复制当前排除列表到剪切板
        binding.btnCopyExclude.setOnClickListener {
            copyPatternsToClipboard()
        }
    }

    /**
     * 复制当前排除列表到剪切板
     */
    private fun copyPatternsToClipboard() {
        if (itemPatterns.isEmpty()) {
            Toast.makeText(context, "排除列表为空，无需复制", Toast.LENGTH_SHORT).show()
            return
        }

        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val patternsJson = JSON.toJSONString(itemPatterns)
        val clipData = ClipData.newPlainText("排除项目列表", patternsJson)
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(context, "已复制排除列表到剪切板", Toast.LENGTH_SHORT).show()
    }

    /**
     * 解析剪切板并导入排除列表
     */
    private fun parseAndImportFromClipboard() {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboardManager.hasPrimaryClip()) {
            Toast.makeText(context, "剪切板为空", Toast.LENGTH_SHORT).show()
            return
        }

        val clipData = clipboardManager.primaryClip
        if (clipData == null || clipData.itemCount == 0) {
            Toast.makeText(context, "剪切板为空", Toast.LENGTH_SHORT).show()
            return
        }

        val clipText = clipData.getItemAt(0).text?.toString()
        if (clipText.isNullOrEmpty()) {
            Toast.makeText(context, "剪切板内容为空", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val jsonObject = JSON.parseObject(clipText)
            val parsedPatterns = mutableMapOf<String, MutableList<String>>()

            for (key in jsonObject.keys) {
                val jsonArray = jsonObject.getJSONArray(key)
                val patterns = jsonArray.toList(String::class.java).toMutableList()
                if (patterns.isNotEmpty()) {
                    parsedPatterns[key] = patterns
                }
            }

            if (parsedPatterns.isEmpty()) {
                Toast.makeText(context, "剪切板中没有有效的排除项目", Toast.LENGTH_SHORT).show()
                return
            }

            // 显示导入确认对话框
            showImportConfirmDialog(parsedPatterns)

        } catch (e: Exception) {
            Toast.makeText(context, "剪切板内容格式无效", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 显示导入确认对话框
     */
    private fun showImportConfirmDialog(parsedPatterns: Map<String, List<String>>) {
        val patternCount = parsedPatterns.values.sumOf { it.size }
        val message = StringBuilder("检测到 $patternCount 个排除项目：\n\n")
        parsedPatterns.forEach { (item, patterns) ->
            val displayName = ExcludePatternBottomSheet.getCompressItemDisplayName(item)
            message.append("[$displayName]: ${patterns.joinToString(", ")}\n")
        }
        message.append("\n是否导入？")

        AlertDialog.Builder(context)
            .setTitle("导入排除项目")
            .setMessage(message.toString())
            .setPositiveButton("导入") { _, _ ->
                parsedPatterns.forEach { (item, patterns) ->
                    val existing = itemPatterns.getOrPut(item) { mutableListOf() }
                    for (pattern in patterns) {
                        if (!existing.contains(pattern)) {
                            existing.add(pattern)
                        }
                    }
                }
                refreshPatternsDisplay()
                onPatternsChangedListener?.invoke(getItemPatternsMap())
                Toast.makeText(context, "已导入排除项目", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 显示排除项目输入BottomSheet
     */
    private fun showExcludePatternBottomSheet() {
        val fm = fragmentManager ?: (context as? FragmentActivity)?.supportFragmentManager ?: return

        val bottomSheet = ExcludePatternBottomSheet.newInstance(
            packageName = packageName,
            itemPatternsMap = getItemPatternsMap()
        )

        bottomSheet.setOnPatternsConfirmedListener { _, itemPatternsMap ->
            // 更新所有压缩项目的排除模式
            setItemPatternsMap(itemPatternsMap)
        }

        bottomSheet.show(fm, "exclude_pattern_bottom_sheet")
    }

    /**
     * 为指定压缩项目设置排除模式
     */
    fun setPatternsForItem(compressItem: String, patterns: List<String>) {
        if (patterns.isEmpty()) {
            itemPatterns.remove(compressItem)
        } else {
            itemPatterns[compressItem] = patterns.toMutableList()
        }
        refreshPatternsDisplay()
        onPatternsChangedListener?.invoke(getItemPatternsMap())
    }

    /**
     * 设置所有压缩项目的排除模式映射（完全替换）
     */
    fun setItemPatternsMap(patternsMap: Map<String, List<String>>) {
        itemPatterns.clear()
        patternsMap.forEach { (item, patterns) ->
            if (patterns.isNotEmpty()) {
                itemPatterns[item] = patterns.toMutableList()
            }
        }
        refreshPatternsDisplay()
        onPatternsChangedListener?.invoke(getItemPatternsMap())
    }

    /**
     * 添加排除模式到指定压缩项目
     */
    fun addPatternForItem(compressItem: String, pattern: String) {
        val patterns = itemPatterns.getOrPut(compressItem) { mutableListOf() }
        if (!patterns.contains(pattern)) {
            patterns.add(pattern)
            refreshPatternsDisplay()
            onPatternsChangedListener?.invoke(getItemPatternsMap())
        }
    }

    /**
     * 从指定压缩项目移除排除模式
     */
    fun removePatternFromItem(compressItem: String, pattern: String) {
        itemPatterns[compressItem]?.let { patterns ->
            patterns.remove(pattern)
            if (patterns.isEmpty()) {
                itemPatterns.remove(compressItem)
            }
            refreshPatternsDisplay()
            onPatternsChangedListener?.invoke(getItemPatternsMap())
        }
    }

    /**
     * 获取指定压缩项目的排除模式列表
     */
    fun getPatternsForItem(compressItem: String): List<String> {
        return itemPatterns[compressItem]?.toList() ?: emptyList()
    }

    /**
     * 获取所有排除模式（按压缩项目分类的映射）
     */
    fun getItemPatternsMap(): Map<String, List<String>> {
        return itemPatterns.mapValues { it.value.toList() }
    }

    /**
     * 获取所有排除模式（合并所有压缩项目的排除模式）
     */
    fun getAllPatterns(): List<String> {
        return itemPatterns.values.flatten()
    }

    /**
     * 从ExcludeConfig加载排除模式
     */
    fun loadFromExcludeConfig(config: ExcludeConfig) {
        excludeConfig = config
        itemPatterns.clear()
        val map = config.getItemExcludePatternsMap()
        map.forEach { (item, patterns) ->
            itemPatterns[item] = patterns.toMutableList()
        }
        refreshPatternsDisplay()
    }

    /**
     * 保存排除模式到ExcludeConfig
     */
    fun saveToExcludeConfig(config: ExcludeConfig) {
        config.setItemExcludePatternsMap(getItemPatternsMap())
    }

    /**
     * 设置排除模式列表（兼容旧接口，所有模式都保存到data项目）
     */
    @Deprecated("Use setPatternsForItem instead")
    fun setPatterns(newPatterns: List<String>) {
        itemPatterns.clear()
        if (newPatterns.isNotEmpty()) {
            itemPatterns[CompressItems.COMPRESS_ITEM_DATA] = newPatterns.toMutableList()
        }
        refreshPatternsDisplay()
    }

    /**
     * 获取当前排除模式列表（兼容旧接口，返回所有模式）
     */
    @Deprecated("Use getAllPatterns or getPatternsForItem instead")
    fun getPatterns(): List<String> {
        return getAllPatterns()
    }

    /**
     * 添加排除模式（兼容旧接口，添加到data项目）
     */
    @Deprecated("Use addPatternForItem instead")
    fun addPattern(pattern: String) {
        addPatternForItem(CompressItems.COMPRESS_ITEM_DATA, pattern)
    }

    /**
     * 移除排除模式（兼容旧接口，从所有项目中移除）
     */
    @Deprecated("Use removePatternFromItem instead")
    fun removePattern(pattern: String) {
        itemPatterns.forEach { (item, patterns) ->
            patterns.remove(pattern)
        }
        // 清理空列表
        itemPatterns.entries.removeAll { it.value.isEmpty() }
        refreshPatternsDisplay()
        onPatternsChangedListener?.invoke(getItemPatternsMap())
    }

    /**
     * 设置模式变化监听器
     */
    fun setOnPatternsChangedListener(listener: (Map<String, List<String>>) -> Unit) {
        onPatternsChangedListener = listener
    }

    /**
     * 刷新排除模式显示
     * 显示格式: [压缩项目] 排除模式
     */
    private fun refreshPatternsDisplay() {
        binding.chipGroupExcludePatterns.removeAllViews()

        itemPatterns.forEach { (compressItem, patterns) ->
            val displayName = ExcludePatternBottomSheet.getCompressItemDisplayName(compressItem)

            for (pattern in patterns) {
                val chip = Chip(
                    ContextThemeWrapper(
                        context,
                        MaterialR.style.Widget_Material3_Chip_Filter
                    )
                ).apply {
                    text = "[$displayName] $pattern"
                    isCloseIconVisible = true
                    isCheckable = false
                    setOnCloseIconClickListener {
                        removePatternFromItem(compressItem, pattern)
                    }
                }
                binding.chipGroupExcludePatterns.addView(chip)
            }
        }
    }

    /**
     * 设置启用状态
     */
    fun setEnabled(enabled: Boolean) {
        binding.btnBrowseExclude.isEnabled = enabled
        binding.btnCopyExclude.isEnabled = enabled
        binding.chipGroupExcludePatterns.isEnabled = enabled
        // 更新所有 chip 的启用状态
        for (i in 0 until binding.chipGroupExcludePatterns.childCount) {
            binding.chipGroupExcludePatterns.getChildAt(i).isEnabled = enabled
        }
    }

}
