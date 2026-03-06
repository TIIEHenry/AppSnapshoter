package tiiehenry.android.app.snapshotor.app

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.config.AppConfig
import tiiehenry.android.app.snapshotor.databinding.FragmentAppConfigBinding
import tiiehenry.android.app.snapshotor.databinding.IncludeShotOptionsBinding
import tiiehenry.android.app.snapshotor.model.PairedDevice
import tiiehenry.android.app.snapshotor.ui.common.PairedDeviceAdapter
import tiiehenry.android.app.snapshotor.ui.common.ShotOptionsManager
import tiiehenry.android.app.snapshotor.ui.common.SyncOptionsManager

class AppConfigFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentAppConfigBinding? = null
    private val binding get() = _binding!!
    private lateinit var packageName: String
    private lateinit var appConfig: AppConfig
    
    private var dismissListener: (() -> Unit)? = null
    private lateinit var syncOptionsManager: SyncOptionsManager
    private lateinit var shotOptionsManager: ShotOptionsManager
    private lateinit var pairedDeviceAdapter: PairedDeviceAdapter


    companion object {
        private const val ARG_PACKAGE_NAME = "package_name"

        fun newInstance(packageName: String): AppConfigFragment {
            val fragment = AppConfigFragment()
            val args = Bundle()
            args.putString(ARG_PACKAGE_NAME, packageName)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            packageName =
                it.getString(ARG_PACKAGE_NAME) ?: throw IllegalArgumentException("packageName is required")
        }
        appConfig = AppConfig(packageName)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppConfigBinding.inflate(inflater, container, false)
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
            resetConfig()
        }

        // 初始化截图选项管理器
        shotOptionsManager = ShotOptionsManager(
            binding.includeShotOptions, appConfig.shotConfig
        )
        
        // 设置压缩算法下拉框
        val algorithms = SnapShotApp.getInstance().fileSystem.compressor.supportedAlgorithms()
        shotOptionsManager.setCompressAlgorithmOptions(algorithms.toTypedArray())

        // 初始化同步选项管理器
        syncOptionsManager = SyncOptionsManager(
            binding.includeSyncOptions, appConfig.syncConfig
        )
        
        // 初始化配对设备列表
        pairedDeviceAdapter = PairedDeviceAdapter(emptyList()) { _, _ ->
            // 当设备选择状态改变时更新配置
            updateSyncTargetsFromDeviceSelection()
        }
        binding.includeSyncOptions.recyclerPairedDevices.layoutManager = LinearLayoutManager(requireContext())
        binding.includeSyncOptions.recyclerPairedDevices.adapter = pairedDeviceAdapter
    }

    private fun loadConfig() {
        // 使用截图选项管理器加载配置
        shotOptionsManager.loadConfig()
        
        // 设置同步目标和系统
        syncOptionsManager.setSyncTargets(appConfig.syncConfig.syncTargets)
        syncOptionsManager.setSyncSystems(appConfig.syncConfig.syncSystems)
        
        // 设置启用状态
        syncOptionsManager.setEnableSyncToTarget(appConfig.syncConfig.enableSyncToTarget)
        syncOptionsManager.setEnableSyncToSystem(appConfig.syncConfig.enableSyncToSystem)
        syncOptionsManager.setSyncType(appConfig.syncConfig.syncType)
    }

    private fun saveConfig() {
        // 使用截图选项管理器保存配置
        appConfig.shotConfig.autoSnapshot = shotOptionsManager.getAutoSnapshot()
        appConfig.shotConfig.permission = shotOptionsManager.getPermission()
        appConfig.shotConfig.uninstallArchived = shotOptionsManager.getUninstallArchived()
        appConfig.shotConfig.compressItems = shotOptionsManager.getCompressItems()
        appConfig.shotConfig.compressAlgorithm = shotOptionsManager.getCompressAlgorithm()

        // 保存同步配置
        appConfig.syncConfig.syncTargets = syncOptionsManager.getSyncTargets()
        appConfig.syncConfig.syncSystems = syncOptionsManager.getSyncSystems()
        appConfig.syncConfig.enableSyncToTarget = syncOptionsManager.getEnableSyncToTarget()
        appConfig.syncConfig.enableSyncToSystem = syncOptionsManager.getEnableSyncToSystem()
        appConfig.syncConfig.syncType = syncOptionsManager.getSyncType()

        // 保存所有配置到文件
        appConfig.save()
    }

    private fun resetConfig() {
        appConfig.reset()
        loadConfig()
        Toast.makeText(requireContext(), "配置已重置", Toast.LENGTH_SHORT).show()
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
                    // 根据配置更新选中的设备
                    val selectedDeviceIds = appConfig.syncConfig.syncTargets
                    
                    // 使用SyncOptionsManager来设置配对设备列表
                    syncOptionsManager.setPairedDevices(pairedDevices, selectedDeviceIds)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(requireContext(), "加载配对设备失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateSyncTargetsFromDeviceSelection() {
        // 通过SyncOptionsManager获取同步目标
        val selectedDevices = syncOptionsManager.getSyncTargets()
        
        // 同时更新ChipGroup中的同步目标列表
        syncOptionsManager.setSyncTargets(selectedDevices)
    }

    fun setOnDismissListener(listener: () -> Unit) {
        this.dismissListener = listener
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        dismissListener?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}