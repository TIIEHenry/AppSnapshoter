package tiiehenry.android.app.snapshot.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.os.Environment
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import tiiehenry.android.app.snapshot.config.ExtraCompressItem
import tiiehenry.android.app.snapshot.databinding.BottomSheetExtraItemEditBinding
import com.google.android.material.R as MaterialR

/**
 * 额外项目编辑 BottomSheetDialogFragment
 * 用于编辑额外项目的名称、路径和排除模式
 */
class ExtraItemEditBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetExtraItemEditBinding? = null
    private val binding get() = _binding!!

    private var itemName: String = ""
    private var itemPath: String = ""
    private var excludePatterns: MutableList<String> = mutableListOf()
    private var rootPath: String = "/data/data"
    private var isEditMode: Boolean = false

    private var onItemConfirmedListener: ((ExtraCompressItem) -> Unit)? = null

    companion object {
        private const val ARG_NAME = "name"
        private const val ARG_PATH = "path"
        private const val ARG_EXCLUDES = "excludes"
        private const val ARG_ROOT_PATH = "root_path"
        private const val ARG_EDIT_MODE = "edit_mode"

        /**
         * 创建编辑额外项目实例
         * @param item 要编辑的额外项目（新建时传 null）
         * @param rootPath 根路径，用于文件选择器
         */
        fun newInstance(
            item: ExtraCompressItem? = null,
            rootPath: String = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath
        ): ExtraItemEditBottomSheet {
            return ExtraItemEditBottomSheet().apply {
                arguments = Bundle().apply {
                    item?.let {
                        putString(ARG_NAME, it.name)
                        putString(ARG_PATH, it.path)
                        putStringArrayList(
                            ARG_EXCLUDES, ArrayList(it.excludePatterns)
                        )
                        putBoolean(ARG_EDIT_MODE, true)
                    }
                    putString(ARG_ROOT_PATH, rootPath)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            itemName = it.getString(ARG_NAME, "")
            itemPath = it.getString(ARG_PATH, "")
            excludePatterns = it.getStringArrayList(ARG_EXCLUDES)?.toMutableList() ?: mutableListOf()
            rootPath = it.getString(ARG_ROOT_PATH, "/data/data")
            isEditMode = it.getBoolean(ARG_EDIT_MODE, false)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetExtraItemEditBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        loadData()
    }

    private fun initViews() {
        // 设置标题
        binding.tvTitle.text = if (isEditMode) "编辑额外项目" else "添加额外项目"

        // 关闭按钮
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        // 路径输入框的文件夹图标点击事件 - 打开文件选择器
        binding.tilPath.setEndIconOnClickListener {
            showFilePicker()
        }

        // 添加排除模式按钮
        binding.btnAddExclude.setOnClickListener {
            showAddExcludeDialog()
        }

        // 确认按钮
        binding.btnConfirm.setOnClickListener {
            confirmEdit()
        }
    }

    private fun loadData() {
        binding.etName.setText(itemName)
        binding.etPath.setText(itemPath)
        refreshExcludesDisplay()
    }

    /**
     * 显示文件选择器
     */
    private fun showFilePicker() {
        val filePicker = FilePickerBottomSheet.newInstance(
            userId = 0, rootPath = rootPath
        )

        filePicker.setOnFilesSelectedListener { selectedFiles ->
            if (selectedFiles.isNotEmpty()) {
                // 取第一个选中的文件/目录的完整路径
                val selectedPath = "$rootPath/${selectedFiles.first()}"
                binding.etPath.setText(selectedPath)

                // 如果名称为空，则自动填入文件名
                if (binding.etName.text.toString().trim().isEmpty()) {
                    val fileName =
                        selectedFiles.first().substringAfterLast('/').substringAfterLast('\\')
                    binding.etName.setText(fileName)
                }
            }
        }

        filePicker.show(parentFragmentManager, "file_picker")
    }

    /**
     * 显示添加排除模式对话框
     */
    private fun showAddExcludeDialog() {
        val bottomSheet = ExtraExcludePatternBottomSheet.newInstance(
            excludePatterns = excludePatterns,
            rootPath = binding.etPath.text.toString()
        )

        bottomSheet.setOnPatternsConfirmedListener { patterns ->
            excludePatterns = patterns.toMutableList()
            refreshExcludesDisplay()
        }

        bottomSheet.show(parentFragmentManager, "exclude_pattern_bottom_sheet")
    }

    /**
     * 刷新排除模式显示
     */
    private fun refreshExcludesDisplay() {
        binding.chipGroupExcludes.removeAllViews()

        for (pattern in excludePatterns) {
            val chip = Chip(
                ContextThemeWrapper(
                    requireContext(), MaterialR.style.Widget_Material3_Chip_Filter
                )
            ).apply {
                text = pattern
                isCloseIconVisible = true
                isCheckable = false
                setOnCloseIconClickListener {
                    excludePatterns.remove(pattern)
                    refreshExcludesDisplay()
                }
            }
            binding.chipGroupExcludes.addView(chip)
        }

        binding.chipGroupExcludes.isVisible = excludePatterns.isNotEmpty()
    }

    /**
     * 确认编辑
     */
    private fun confirmEdit() {
        val name = binding.etName.text.toString().trim()
        val path = binding.etPath.text.toString().trim()

        if (name.isEmpty()) {
            binding.tilName.error = "请输入名称"
            return
        }
        binding.tilName.error = null

        if (path.isEmpty()) {
            binding.tilPath.error = "请输入路径"
            return
        }
        binding.tilPath.error = null

        val item = ExtraCompressItem(name, path, excludePatterns, true)

        onItemConfirmedListener?.invoke(item)
        dismiss()
    }

    /**
     * 设置确认回调
     */
    fun setOnItemConfirmedListener(listener: (ExtraCompressItem) -> Unit) {
        onItemConfirmedListener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
