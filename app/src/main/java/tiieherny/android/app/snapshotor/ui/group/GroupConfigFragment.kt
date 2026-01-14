package tiieherny.android.app.snapshotor.ui.group

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiieherny.android.app.snapshotor.SnapShotApp
import tiieherny.android.app.snapshotor.config.CompressItems
import tiieherny.android.app.snapshotor.config.CompressItems.COMPRESS_ITEM_APK
import tiieherny.android.app.snapshotor.config.GroupConfig
import tiieherny.android.app.snapshotor.databinding.FragmentGroupConfigBinding
import tiieherny.android.app.snapshotor.databinding.IncludeShotOptionsBinding
import tiieherny.android.app.snapshotor.group.SnapGroup
import tiieherny.android.app.snapshotor.model.PairedDevice
import tiieherny.android.app.snapshotor.ui.common.PairedDeviceAdapter
import tiieherny.android.app.snapshotor.ui.common.ShotOptionsManager
import tiieherny.android.app.snapshotor.ui.common.SyncOptionsManager

class GroupConfigFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentGroupConfigBinding? = null
    private val binding get() = _binding!!
    private lateinit var groupId: String
    private lateinit var groupConfig: GroupConfig
    private lateinit var syncOptionsManager: SyncOptionsManager
    private lateinit var shotOptionsManager: ShotOptionsManager
    private lateinit var pairedDeviceAdapter: PairedDeviceAdapter
    private lateinit var sortTypeSpinner: Spinner

    companion object {
        private const val ARG_GROUP_ID = "group_id"

        fun newInstance(group: SnapGroup): GroupConfigFragment {
            val fragment = GroupConfigFragment()
            val args = Bundle()
            args.putString(ARG_GROUP_ID, group.id)
            fragment.arguments = args
            fragment.groupConfig = group.config
            return fragment
        }
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
        loadPairedDevices()
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
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, sortTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortTypeSpinner.adapter = adapter

        // 初始化截图选项管理器
        shotOptionsManager = ShotOptionsManager(
            binding.includeShotOptions, groupConfig.shotConfig
        )

        // 设置压缩算法下拉框
        val algorithms = SnapShotApp.getInstance().fileSystem.compressor.supportedAlgorithms()
        shotOptionsManager.setCompressAlgorithmOptions(algorithms.toTypedArray())

        // 初始化同步选项管理器
        syncOptionsManager = SyncOptionsManager(
            binding.includeSyncOptions, groupConfig.syncConfig
        )
        syncOptionsManager.loadConfig()

        // 初始化配对设备列表
        pairedDeviceAdapter = PairedDeviceAdapter(emptyList()) { _, _ ->
            // 当设备选择状态改变时更新配置
            updateSyncTargetsFromDeviceSelection()
        }
        binding.includeSyncOptions.recyclerPairedDevices.layoutManager =
            LinearLayoutManager(requireContext())
        binding.includeSyncOptions.recyclerPairedDevices.adapter = pairedDeviceAdapter
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

        syncOptionsManager.loadConfig()
        // 加载配对设备列表
        loadPairedDevices()
    }

    private fun saveConfig() {
        groupConfig.rootPath = binding.etRootPath.text.toString()

        // 使用截图选项管理器保存配置
        groupConfig.shotConfig.autoSnapshot = shotOptionsManager.getAutoSnapshot()
        groupConfig.shotConfig.permission = shotOptionsManager.getPermission()
        groupConfig.shotConfig.uninstallArchived = shotOptionsManager.getUninstallArchived()
        groupConfig.shotConfig.compressItems = shotOptionsManager.getCompressItems()
        groupConfig.shotConfig.compressAlgorithm = shotOptionsManager.getCompressAlgorithm()

        groupConfig.syncConfig.enableSyncToTarget = syncOptionsManager.getEnableSyncToTarget()
        groupConfig.syncConfig.enableSyncToSystem = syncOptionsManager.getEnableSyncToSystem()
        
        // 保存排序类型
        groupConfig.sortConfig.sortType = sortTypeSpinner.selectedItemPosition
        
        groupConfig.shotConfig.compressAlgorithm =
            binding.autoCompleteCompressAlgorithm.text.toString()

        // 保存压缩选项
        val compressItems = mutableSetOf<String>()
        if (binding.chipApk.isChecked) compressItems.add("apk")
        if (binding.chipData.isChecked) compressItems.add("data")
        if (binding.chipUser.isChecked) compressItems.add("user")
        if (binding.chipUserDe.isChecked) compressItems.add("user_de")
        if (binding.chipObb.isChecked) compressItems.add("obb")
        if (binding.chipExternalData.isChecked) compressItems.add("external_data")
        groupConfig.shotConfig.compressItems = compressItems

        // 保存同步目标和系统
        groupConfig.syncConfig.syncTargets = syncOptionsManager.getSyncTargets()
        groupConfig.syncConfig.syncSystems = syncOptionsManager.getSyncSystems()
    }

    private fun loadPairedDevices() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataSyncer = SnapShotApp.getInstance().dataSyncer
                val pairedDeviceIds = dataSyncer.getPairedDevices()

                // 将设备ID列表转换为PairedDevice列表
                val pairedDevices = pairedDeviceIds.map { deviceId ->
                    PairedDevice(deviceId, deviceId) // 使用设备ID作为设备名称，实际应用中可能需要从其他地方获取设备名称
                }

                withContext(Dispatchers.Main) {
                    // 更新适配器数据
                    pairedDeviceAdapter = PairedDeviceAdapter(
                        pairedDevices,
                        groupConfig.syncConfig.syncTargets.toMutableSet()
                    ) { _, _ ->
                        updateSyncTargetsFromDeviceSelection()
                    }

                    binding.includeSyncOptions.recyclerPairedDevices.adapter = pairedDeviceAdapter

                    // 根据配置更新选中的设备
                    val selectedDeviceIds = groupConfig.syncConfig.syncTargets
                    pairedDeviceAdapter.updateSelectedDevices(selectedDeviceIds)

                    // 使用SyncOptionsManager来设置配对设备列表
                    syncOptionsManager.setPairedDevices(pairedDevices, selectedDeviceIds)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    // 可以在这里添加错误提示
                }
            }
        }
    }

    private fun updateSyncTargetsFromDeviceSelection() {
        val selectedDevices = pairedDeviceAdapter.getSelectedDevices()

        // 同时更新ChipGroup中的同步目标列表
        syncOptionsManager.setSyncTargets(selectedDevices)
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