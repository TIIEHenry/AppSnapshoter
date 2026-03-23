package tiiehenry.android.app.snapshot.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.databinding.BottomSheetFilePickerBinding
import tiiehenry.android.app.snapshot.databinding.ItemFilePickerBinding
import tiiehenry.android.snapshot.file.IFileSystem

/**
 * 文件选择器 BottomSheetDialogFragment
 * 使用 IFileSystem 展示文件列表，支持长按多选
 */
class FilePickerBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetFilePickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var fileAdapter: FilePickerAdapter
    private val selectedFiles = mutableSetOf<String>()
    private var currentPath: String = ""
    private var rootPath: String = ""

    private var onFilesSelectedListener: ((List<String>) -> Unit)? = null

    companion object {
        private const val ARG_ROOT_PATH = "root_path"

        /**
         * 创建文件选择器实例
         * @param rootPath 根路径，默认为 /data/data
         */
        fun newInstance(rootPath: String = "/data/data"): FilePickerBottomSheet {
            return FilePickerBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_ROOT_PATH, rootPath)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            rootPath = it.getString(ARG_ROOT_PATH, "/data/data")
        }
        currentPath = rootPath
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme).apply {
            setOnShowListener { dialogInterface ->
                val bottomSheet =
                    findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.let {
                    val layoutParams = it.layoutParams
                    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    it.layoutParams = layoutParams
                }
                // 获取 BottomSheetBehavior 并禁用拖动
                val bottomSheetDialog = dialogInterface as BottomSheetDialog
                val behavior = bottomSheetDialog.behavior
                behavior.isDraggable = false
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetFilePickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        loadFiles()
    }

    private fun initViews() {
        // 设置 RecyclerView
        fileAdapter = FilePickerAdapter(
            onItemClick = { fileItem ->
                if (fileItem.isDirectory) {
                    // 点击进入目录
                    enterDirectory(fileItem)
                } else {
                    // 文件：点击切换选中状态
                    toggleSelection(fileItem)
                }
            },
            onItemLongClick = { fileItem ->
                // 长按切换选中状态（文件和文件夹都支持）
                toggleSelection(fileItem)
                true
            },
            onFolderSelectClick = { fileItem ->
                // 文件夹：点击选择按钮切换选中状态
                toggleSelection(fileItem)
            }
        )

        binding.recyclerViewFiles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fileAdapter
            setHasFixedSize(true) // 固定高度，提升性能
            isNestedScrollingEnabled = false // 禁用嵌套滚动，避免与 BottomSheet 冲突
        }

        // 返回按钮
        binding.btnBack.setOnClickListener {
            navigateUp()
        }

        // 关闭按钮
        binding.btnClose.setOnClickListener {
            dismiss()
        }

        // 确认按钮
        binding.btnConfirm.setOnClickListener {
            onFilesSelectedListener?.invoke(selectedFiles.toList())
            dismiss()
        }
    }

    /**
     * 切换文件选中状态
     */
    private fun toggleSelection(fileItem: FileItem) {
        val relativePath = getRelativePath(fileItem.path)
        if (selectedFiles.contains(relativePath)) {
            selectedFiles.remove(relativePath)
        } else {
            selectedFiles.add(relativePath)
        }
        updateSelectionUI()
        fileAdapter.setSelectedFiles(selectedFiles)
    }

    /**
     * 进入目录
     */
    private fun enterDirectory(fileItem: FileItem) {
        currentPath = fileItem.path
        loadFiles()
    }

    /**
     * 更新选中状态UI
     */
    private fun updateSelectionUI() {
        binding.tvSelectedCount.text = "已选择: ${selectedFiles.size}"
        binding.btnConfirm.isEnabled = selectedFiles.isNotEmpty()
    }

    /**
     * 获取相对路径
     */
    private fun getRelativePath(absolutePath: String): String {
        return if (absolutePath.startsWith(rootPath)) {
            absolutePath.removePrefix(rootPath).removePrefix("/")
        } else {
            absolutePath
        }
    }

    /**
     * 返回上级目录
     */
    private fun navigateUp() {
        if (currentPath == rootPath) {
            return
        }
        val parentPath = getParentPath(currentPath)
        if (parentPath != null && parentPath.length >= rootPath.length) {
            currentPath = parentPath
            loadFiles()
        }
    }

    /**
     * 获取父目录路径
     */
    private fun getParentPath(path: String): String? {
        return try {
            SnapshotApp.getInstance().fileSystem.getParent(path)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 加载文件列表
     */
    private fun loadFiles() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val fileSystem = SnapshotApp.getInstance().fileSystem
                val files = loadFilesInternal(fileSystem, currentPath)
                withContext(Dispatchers.Main) {
                    updateUI(files)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    showEmptyState()
                }
            }
        }
    }

    /**
     * 内部加载文件列表
     */
    private fun loadFilesInternal(fileSystem: IFileSystem, path: String): List<FileItem> {
        val fileNames = fileSystem.listDir(path) ?: emptyList()
        val fileItems = mutableListOf<FileItem>()

        for (fileName in fileNames) {
            val filePath = "$path/$fileName"
            try {
                val fileType = fileSystem.fileType(filePath)
                val isDirectory = fileType == 1
                fileItems.add(
                    FileItem(
                        name = fileName,
                        path = filePath,
                        isDirectory = isDirectory
                    )
                )
            } catch (e: Exception) {
                // 忽略无法访问的文件
            }
        }

        // 目录在前，文件在后，按名称排序
        return fileItems.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /**
     * 更新UI
     */
    private fun updateUI(files: List<FileItem>) {
        binding.tvCurrentPath.text = currentPath
        if (files.isEmpty()) {
            showEmptyState()
        } else {
            binding.recyclerViewFiles.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
            fileAdapter.submitList(files)
        }
        updateSelectionUI()
    }

    /**
     * 显示空状态
     */
    private fun showEmptyState() {
        binding.recyclerViewFiles.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
    }

    /**
     * 设置文件选择回调
     */
    fun setOnFilesSelectedListener(listener: (List<String>) -> Unit) {
        onFilesSelectedListener = listener
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 文件项数据类
     */
    data class FileItem(
        val name: String,
        val path: String,
        val isDirectory: Boolean
    )

    /**
     * 文件选择器适配器
     */
    inner class FilePickerAdapter(
        private val onItemClick: (FileItem) -> Unit,
        private val onItemLongClick: (FileItem) -> Boolean,
        private val onFolderSelectClick: (FileItem) -> Unit
    ) : RecyclerView.Adapter<FilePickerAdapter.ViewHolder>() {

        private var fileList = listOf<FileItem>()
        private var selectedFiles = setOf<String>()

        inner class ViewHolder(val binding: ItemFilePickerBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemFilePickerBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val fileItem = fileList[position]
            val relativePath = getRelativePath(fileItem.path)
            val isSelected = selectedFiles.contains(relativePath)

            holder.binding.apply {
                tvFileName.text = fileItem.name

                // 设置图标
                ivFileIcon.setImageResource(
                    if (fileItem.isDirectory) R.drawable.ic_folder_open
                    else R.drawable.ic_file
                )

                // 设置选中状态
                cbFileSelected.isChecked = isSelected

                cbFileSelected.setOnClickListener {
                    onFolderSelectClick(fileItem)
                }
                if (fileItem.isDirectory) {
                    // 文件夹：显示选择按钮，点击选择按钮切换选中状态
                    cbFileSelected.visibility = View.VISIBLE
                    // 整个条目点击进入目录
                    root.setOnClickListener {
                        onItemClick(fileItem)
                    }
                } else {
                    // 文件：显示选择按钮，点击整个条目切换选中状态
                    cbFileSelected.visibility = View.VISIBLE
                    root.setOnClickListener {
                        onItemClick(fileItem)
                    }
                }

                // 长按事件（文件和文件夹都支持）
                root.setOnLongClickListener {
                    onItemLongClick(fileItem)
                }
            }
        }

        override fun getItemCount(): Int = fileList.size

        fun submitList(list: List<FileItem>) {
            fileList = list
            notifyDataSetChanged()
        }

        fun setSelectedFiles(selected: Set<String>) {
            selectedFiles = selected
            notifyDataSetChanged()
        }
    }
}
