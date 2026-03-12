package tiiehenry.android.app.snapshot.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.R as MaterialR
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.config.CompressItems
import tiiehenry.android.app.snapshot.databinding.BottomSheetExcludePatternBinding
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.TypeReference

/**
 * 排除项目输入 BottomSheetDialogFragment
 * 用于为特定压缩项目添加排除模式
 */
class ExcludePatternBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetExcludePatternBinding? = null
    private val binding get() = _binding!!

    private var compressItem: String = ""
    private var packageName: String = ""
    // 按压缩项目分类的排除模式映射，用于保存所有压缩项目的排除模式
    private val itemPatternsMap = mutableMapOf<String, MutableList<String>>()
    // 当前选中压缩项目的排除模式列表（引用自 itemPatternsMap）
    private val currentPatterns: MutableList<String>
        get() = itemPatternsMap.getOrPut(compressItem) { mutableListOf() }
    
    // 压缩项目选项列表
    private val compressItemOptions = listOf(
        CompressItems.COMPRESS_ITEM_DATA to "DATA",
        CompressItems.COMPRESS_ITEM_USER to "USER",
        CompressItems.COMPRESS_ITEM_USER_DE to "USER_DE",
        CompressItems.COMPRESS_ITEM_OBB to "OBB",
        CompressItems.COMPRESS_ITEM_MEDIA to "MEDIA"
    )

    private var onPatternsConfirmedListener: ((String, Map<String, List<String>>) -> Unit)? = null

    companion object {
        private const val ARG_COMPRESS_ITEM = "compress_item"
        private const val ARG_PACKAGE_NAME = "package_name"
        private const val ARG_ITEM_PATTERNS_MAP = "item_patterns_map"

        /**
         * 创建排除项目输入器实例
         * @param compressItem 压缩项目类型 (如 CompressItems.COMPRESS_ITEM_DATA)，为空时让用户选择
         * @param packageName 应用包名（用于文件选择器的根路径）
         * @param itemPatternsMap 已存在的按压缩项目分类的排除模式映射 Map<压缩项目, 排除模式列表>
         */
        fun newInstance(
            compressItem: String = "",
            packageName: String = "",
            itemPatternsMap: Map<String, List<String>> = emptyMap()
        ): ExcludePatternBottomSheet {
            return ExcludePatternBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_COMPRESS_ITEM, compressItem)
                    putString(ARG_PACKAGE_NAME, packageName)
                    // 将 Map 序列化为 JSON 字符串传递
                    val jsonString = JSON.toJSONString(itemPatternsMap)
                    putString(ARG_ITEM_PATTERNS_MAP, jsonString)
                }
            }
        }

        /**
         * 获取压缩项目的显示名称
         */
        fun getCompressItemDisplayName(item: String): String {
            return when (item) {
                CompressItems.COMPRESS_ITEM_APK -> "APK"
                CompressItems.COMPRESS_ITEM_DATA -> "DATA"
                CompressItems.COMPRESS_ITEM_USER -> "USER"
                CompressItems.COMPRESS_ITEM_USER_DE -> "USER_DE"
                CompressItems.COMPRESS_ITEM_OBB -> "OBB"
                CompressItems.COMPRESS_ITEM_MEDIA -> "MEDIA"
                else -> item.uppercase()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            compressItem = it.getString(ARG_COMPRESS_ITEM, "")
            packageName = it.getString(ARG_PACKAGE_NAME, "")
            // 解析按压缩项目分类的排除模式映射
            val jsonString = it.getString(ARG_ITEM_PATTERNS_MAP, "{}")
            itemPatternsMap.clear()
            try {
                val parsedMap: Map<String, List<String>>? = JSON.parseObject(
                    jsonString,
                    object : TypeReference<Map<String, List<String>>>() {}
                )
                parsedMap?.forEach { (item, patterns) ->
                    itemPatternsMap[item] = patterns.toMutableList()
                }
            } catch (e: Exception) {
                // 解析失败时保持空映射
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetExcludePatternBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        refreshPatternsDisplay()
    }

    private fun initViews() {
        // 设置 Spinner 适配器
        setupCompressItemSpinner()

        // 帮助按钮
        binding.tilExcludePattern.setEndIconOnClickListener {
            showHelpDialog()
        }

        // 关闭按钮
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        // 添加按钮
        binding.btnAdd.setOnClickListener {
            val pattern = binding.etExcludePattern.text.toString().trim()
            if (pattern.isNotEmpty()) {
                addPattern(pattern)
                binding.etExcludePattern.text?.clear()
            }
        }

        // 浏览文件按钮
        binding.btnBrowse.setOnClickListener {
            showFilePicker()
        }

        // 完成按钮
        binding.btnConfirm.setOnClickListener {
            // 返回所有压缩项目的排除模式映射
            onPatternsConfirmedListener?.invoke(compressItem, itemPatternsMap.mapValues { it.value.toList() })
            dismiss()
        }
    }
    
    /**
     * 设置压缩项目 Spinner
     */
    private fun setupCompressItemSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            compressItemOptions.map { it.second }
        )
        binding.spinnerCompressItem.setAdapter(adapter)
        
        // 设置初始选中项
        val initialIndex = compressItemOptions.indexOfFirst { it.first == compressItem }
        if (initialIndex >= 0) {
            binding.spinnerCompressItem.setText(compressItemOptions[initialIndex].second, false)
        } else {
            // 默认选中第一个
            binding.spinnerCompressItem.setText(compressItemOptions[0].second, false)
            compressItem = compressItemOptions[0].first
        }
        
        // 监听选择变化
        binding.spinnerCompressItem.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = compressItemOptions[position]
            compressItem = selectedItem.first
            // 切换压缩项目时刷新显示
            refreshPatternsDisplay()
        }
    }

    /**
     * 显示文件选择器
     * 根据 compressItem 构建对应的根路径
     */
    private fun showFilePicker() {
        val activity = requireActivity()
        // 根据 compressItem 和 packageName 构建对应的根路径
        val rootPath = when (compressItem) {
            CompressItems.COMPRESS_ITEM_DATA -> "/data/data/$packageName"
            CompressItems.COMPRESS_ITEM_USER -> "/data/user/0/$packageName"
            CompressItems.COMPRESS_ITEM_USER_DE -> "/data/user_de/0/$packageName"
            CompressItems.COMPRESS_ITEM_OBB -> "/storage/emulated/0/Android/obb/$packageName"
            CompressItems.COMPRESS_ITEM_MEDIA -> "/storage/emulated/0/Android/media/$packageName"
            else -> "/data/data/$packageName"
        }
        val filePicker = FilePickerBottomSheet.newInstance(
            userId = 0,
            rootPath = rootPath
        )
        filePicker.setOnFilesSelectedListener { selectedFiles ->
            // 将选中的文件添加到排除列表
            selectedFiles.forEach { filePath ->
                addPattern(filePath)
            }
        }
        filePicker.show(activity.supportFragmentManager, "file_picker")
    }

    /**
     * 添加排除模式到当前压缩项目
     */
    private fun addPattern(pattern: String) {
        val patterns = currentPatterns
        if (!patterns.contains(pattern)) {
            patterns.add(pattern)
            refreshPatternsDisplay()
        }
    }

    /**
     * 从当前压缩项目移除排除模式
     */
    private fun removePattern(pattern: String) {
        currentPatterns.remove(pattern)
        refreshPatternsDisplay()
    }

    /**
     * 刷新排除模式显示
     */
    private fun refreshPatternsDisplay() {
        binding.chipGroupPatterns.removeAllViews()

        val patterns = currentPatterns
        for (pattern in patterns) {
            val chip = Chip(ContextThemeWrapper(requireContext(), MaterialR.style.Widget_Material3_Chip_Filter)).apply {
                text = pattern
                isCloseIconVisible = true
                isCheckable = false
                setOnCloseIconClickListener {
                    removePattern(pattern)
                }
            }
            binding.chipGroupPatterns.addView(chip)
        }
    }

    /**
     * 设置排除模式确认回调
     * @param listener 回调函数，参数为：(当前选中的压缩项目, 所有压缩项目的排除模式映射)
     */
    fun setOnPatternsConfirmedListener(listener: (compressItem: String, itemPatternsMap: Map<String, List<String>>) -> Unit) {
        onPatternsConfirmedListener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 显示帮助对话框
     */
    private fun showHelpDialog() {
        val message = """
            排除项目用于 tar 命令的 --exclude 参数，支持以下模式：
            
            1. 精确匹配：直接输入文件名或目录名
               例如：cache、temp.txt
            
            2. 通配符匹配：
               • * 匹配任意字符（不包括/）
                 例如：*.log、cache/*
               • ? 匹配单个字符
                 例如：file?.txt
               • ** 匹配任意字符（包括/）
                 例如：**/cache/**
            
            3. 路径匹配：
               • 相对路径：从备份目录开始的相对路径
                 例如：files/cache、shared_prefs/*.xml
               • 绝对路径：完整路径（不推荐）
            
            常用示例：
            • cache - 排除所有名为 cache 的目录
            • *.tmp - 排除所有临时文件
            • files/logs/* - 排除 logs 目录下的所有文件
            • **/thumbnail* - 排除所有缩略图相关文件
            
            注意：排除模式在打包时生效，已排除的文件不会包含在备份中。
        """.trimIndent()

        AlertDialog.Builder(requireContext())
            .setTitle("排除项目使用说明")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show()
    }
}
