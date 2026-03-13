package tiiehenry.android.app.snapshot.app

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.config.AppConfig
import tiiehenry.android.app.snapshot.databinding.FragmentAppConfigBinding
import tiiehenry.android.app.snapshot.ui.common.ShotOptionsManager
import tiiehenry.android.app.snapshot.ui.common.VersionRetentionManager
import tiiehenry.android.app.snapshot.ui.common.ExcludePatternsManager

class AppConfigFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentAppConfigBinding? = null
    private val binding get() = _binding!!
    private lateinit var packageName: String
    private lateinit var appConfig: AppConfig
    
    private var dismissListener: (() -> Unit)? = null
    private lateinit var shotOptionsManager: ShotOptionsManager
    private lateinit var versionRetentionManager: VersionRetentionManager
    private lateinit var excludePatternsManager: ExcludePatternsManager


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
        binding.btnSave.contentDescription = "保存"

        binding.btnReset.setOnClickListener {
            resetConfig()
        }

        // 初始化截图选项管理器
        shotOptionsManager = ShotOptionsManager(
            binding.includeShotOptions,
            requireContext(),
            appConfig.shotConfig
        )

        // 初始化排除模式管理器（仅应用单独配置需要）
        excludePatternsManager = ExcludePatternsManager(
            binding.includeExcludePatterns,
            requireContext(),
            packageName,
            appConfig.excludeConfig,
            childFragmentManager
        )

        // 初始化版本保留配置管理器
        versionRetentionManager = VersionRetentionManager(
            binding.includeVersionRetention, appConfig.shotConfig.versionRetentionConfig
        )

        // 设置压缩算法下拉框
        val algorithms = SnapshotApp.getInstance().fileSystem.compressor.supportedAlgorithms()
        shotOptionsManager.setCompressAlgorithmOptions(algorithms.toTypedArray())
    }

    private fun loadConfig() {
        // 使用截图选项管理器加载配置
        shotOptionsManager.loadConfig()
        // 加载排除模式列表（从ExcludeConfig加载按压缩项目分类的排除模式）
        excludePatternsManager.loadFromExcludeConfig(appConfig.excludeConfig)
        // 使用版本保留管理器加载配置
        versionRetentionManager.loadConfig()
    }

    private fun saveConfig() {
        // 使用截图选项管理器保存配置
        appConfig.shotConfig.enabled = shotOptionsManager.getEnabled()
        appConfig.shotConfig.autoSnapshot = shotOptionsManager.getAutoSnapshot()
        appConfig.shotConfig.permission = shotOptionsManager.getPermission()
        appConfig.shotConfig.uninstallArchived = shotOptionsManager.getUninstallArchived()
        appConfig.shotConfig.compressItems = shotOptionsManager.getCompressItems()
        appConfig.shotConfig.compressAlgorithm = shotOptionsManager.getCompressAlgorithm()
        // 保存排除模式列表（保存按压缩项目分类的排除模式）
        excludePatternsManager.saveToExcludeConfig(appConfig.excludeConfig)
        
        // 使用版本保留管理器保存配置
        appConfig.shotConfig.versionRetentionConfig = versionRetentionManager.saveToConfig()

        // 保存所有配置到文件
        appConfig.save()
    }

    private fun resetConfig() {
        appConfig.reset()
        loadConfig()
        Toast.makeText(requireContext(), "配置已重置", Toast.LENGTH_SHORT).show()
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