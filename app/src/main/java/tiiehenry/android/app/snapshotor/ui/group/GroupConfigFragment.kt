package tiiehenry.android.app.snapshotor.ui.group

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.config.CompressItems
import tiiehenry.android.app.snapshotor.config.CompressItems.COMPRESS_ITEM_APK
import tiiehenry.android.app.snapshotor.config.GroupConfig
import tiiehenry.android.app.snapshotor.databinding.FragmentGroupConfigBinding
import tiiehenry.android.app.snapshotor.databinding.IncludeShotOptionsBinding
import tiiehenry.android.app.snapshotor.group.SnapGroup
import tiiehenry.android.app.snapshotor.ui.common.ShotOptionsManager

class GroupConfigFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentGroupConfigBinding? = null
    private val binding get() = _binding!!
    private lateinit var groupId: String
    private lateinit var groupConfig: GroupConfig
    private lateinit var shotOptionsManager: ShotOptionsManager
    private lateinit var sortTypeSpinner: Spinner
    private var onConfigSavedListener: (() -> Unit)? = null

    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                onPathSelected(uri)
            }
        }
    }

    companion object {
        private const val ARG_GROUP_ID = "group_id"

        fun newInstance(group: SnapGroup, onConfigSaved: (() -> Unit)? = null): GroupConfigFragment {
            val fragment = GroupConfigFragment()
            val args = Bundle()
            args.putString(ARG_GROUP_ID, group.id)
            fragment.arguments = args
            fragment.groupConfig = group.config
            fragment.onConfigSavedListener = onConfigSaved
            return fragment
        }
    }

    fun setOnConfigSavedListener(listener: () -> Unit) {
        onConfigSavedListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            groupId =
                it.getString(ARG_GROUP_ID) ?: throw IllegalArgumentException("groupId is required")
        }
        if (!this::groupConfig.isInitialized) {
            groupConfig = GroupConfig(groupId)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        loadConfig()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme).apply {
            // 设置全屏显示
            setOnShowListener {
                val bottomSheet =
                    findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.let {
                    // 设置为全屏
                    val layoutParams = it.layoutParams
                    layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    it.layoutParams = layoutParams
                }
            }
        }
    }

    private fun initViews() {
        binding.btnSave.setOnClickListener {
            saveConfig()
            onConfigSavedListener?.invoke()
            dismiss()
        }

        binding.btnReset.setOnClickListener {
            loadConfig() // 重新加载配置，相当于重置
        }

        binding.btnDeleteGroup.setOnClickListener {
            showDeleteConfirmDialog()
        }

        // 设置排序类型Spinner
        sortTypeSpinner = binding.spinnerSortType
        val sortTypes = arrayOf("默认排序", "按名称升序", "按名称降序", "自定义排序")
        val adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortTypeSpinner.adapter = adapter

        // 初始化截图选项管理器
        shotOptionsManager = ShotOptionsManager(
            binding.includeShotOptions, groupConfig.shotConfig
        )

        // 设置压缩算法下拉框
        val algorithms = SnapShotApp.getInstance().fileSystem.compressor.supportedAlgorithms()
        shotOptionsManager.setCompressAlgorithmOptions(algorithms.toTypedArray())

        // 设置路径选择器点击监听
        binding.etRootPath.setOnClickListener {
            openPathSelector()
        }

        binding.etRootPath.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                openPathSelector()
            }
        }
    }

    private fun loadConfig() {
        binding.etRootPath.setText(groupConfig.rootPath)

        // 使用截图选项管理器加载配置
        shotOptionsManager.loadConfig()

        // 设置排序类型Spinner的选中项
        val sortType = groupConfig.sortConfig.sortType
        if (sortType in 0..3) {
            sortTypeSpinner.setSelection(sortType)
        }
    }

    private fun saveConfig() {
        groupConfig.rootPath = binding.etRootPath.text.toString()

        // 使用截图选项管理器保存配置
        groupConfig.shotConfig.autoSnapshot = shotOptionsManager.getAutoSnapshot()
        groupConfig.shotConfig.permission = shotOptionsManager.getPermission()
        groupConfig.shotConfig.uninstallArchived = shotOptionsManager.getUninstallArchived()
        groupConfig.shotConfig.compressItems = shotOptionsManager.getCompressItems()
        groupConfig.shotConfig.compressAlgorithm = shotOptionsManager.getCompressAlgorithm()

        // 保存排序类型
        groupConfig.sortConfig.sortType = sortTypeSpinner.selectedItemPosition

        // 保存所有配置到文件
        groupConfig.save()
    }

    private fun openPathSelector() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        openDocumentTreeLauncher.launch(intent)
    }

    private fun onPathSelected(uri: Uri) {
        val absolutePath = uriToAbsolutePath(uri)
        binding.etRootPath.setText(absolutePath)

        try {
            requireContext().contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    /**
     * 将 URI 转换为绝对路径
     */
    private fun uriToAbsolutePath(uri: Uri): String {
        val documentFile = DocumentFile.fromTreeUri(requireContext(), uri)
        if (documentFile != null) {
            // 尝试获取真实路径
            val realPath = getRealPathFromUri(uri)
            if (realPath.isNotEmpty()) {
                return realPath
            }
        }
        // 如果无法获取真实路径，返回 URI 字符串
        return uri.toString()
    }

    /**
     * 从 URI 获取真实文件路径
     */
    private fun getRealPathFromUri(uri: Uri): String {
        val docId = DocumentsContract.getTreeDocumentId(uri)

        // 处理 primary 存储（内部存储）
        if (docId.startsWith("primary:")) {
            val path = docId.substringAfter("primary:")
            return "/storage/emulated/0/$path"
        }

        // 处理 SD 卡等外部存储
        if (docId.contains(":")) {
            val (storageId, path) = docId.split(":", limit = 2)
            return "/storage/$storageId/$path"
        }

        // 如果无法解析，返回空字符串
        return ""
    }

    private fun showDeleteConfirmDialog() {
        val context = requireContext()
        val group = SnapShotApp.getViewModel().groupList.value?.find { it.id == groupId }

        if (group != null) {
            AlertDialog.Builder(context)
                .setTitle("删除组")
                .setMessage("确定要删除组 ${group.name}[${groupId}] 吗？\n此操作不可恢复。")
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    SnapShotApp.getViewModel().deleteGroup(groupId)
                    dismiss() // 关闭对话框
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}