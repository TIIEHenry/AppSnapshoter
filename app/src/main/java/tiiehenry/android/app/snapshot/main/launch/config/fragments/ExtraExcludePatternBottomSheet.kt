package tiiehenry.android.app.snapshot.main.launch.config.fragments

import android.app.Dialog
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.R as MaterialR
import tiiehenry.android.app.snapshot.databinding.BottomSheetExtraExcludePatternBinding

/**
 * 排除模式输入 BottomSheetDialogFragment
 * 用于编辑额外项目的排除模式列表
 */
class ExtraExcludePatternBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetExtraExcludePatternBinding? = null
    private val binding get() = _binding!!

    private var rootPath: String = ""
    // 当前排除模式列表
    private val currentPatterns = mutableListOf<String>()

    private var onPatternsConfirmedListener: ((List<String>) -> Unit)? = null

    companion object {
        private const val ARG_EXCLUDE_PATTERNS = "exclude_patterns"
        private const val ARG_ROOT_PATH = "root_path"

        /**
         * 创建排除模式输入器实例 (用于额外项目)
         * @param excludePatterns 已存在的排除模式列表
         * @param rootPath 直接指定的根路径（用于文件选择器）
         */
        fun newInstance(
            excludePatterns: List<String> = emptyList(),
            rootPath: String = ""
        ): ExtraExcludePatternBottomSheet {
            return ExtraExcludePatternBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_ROOT_PATH, rootPath)
                    // 传递 List<String>
                    putStringArrayList(ARG_EXCLUDE_PATTERNS, ArrayList(excludePatterns))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            rootPath = it.getString(ARG_ROOT_PATH, "")
            // 读取排除模式列表
            currentPatterns.clear()
            currentPatterns.addAll(it.getStringArrayList(ARG_EXCLUDE_PATTERNS) ?: mutableListOf())
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
        _binding = BottomSheetExtraExcludePatternBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        refreshPatternsDisplay()
    }

    private fun initViews() {
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
            // 返回排除模式列表
            onPatternsConfirmedListener?.invoke(currentPatterns.toList())
            dismiss()
        }
    }

    /**
     * 显示文件选择器
     */
    private fun showFilePicker() {
        val activity = requireActivity()
        val actualRootPath = rootPath.ifEmpty {
            "/data/data"
        }
        val filePicker = FilePickerBottomSheet.newInstance(
            rootPath = actualRootPath
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
     * 添加排除模式
     */
    private fun addPattern(pattern: String) {
        if (!currentPatterns.contains(pattern)) {
            currentPatterns.add(pattern)
            refreshPatternsDisplay()
        }
    }

    /**
     * 从列表移除排除模式
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

        for (pattern in currentPatterns) {
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
     * @param listener 回调函数，参数为：排除模式列表
     */
    fun setOnPatternsConfirmedListener(listener: (List<String>) -> Unit) {
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
