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
import tiiehenry.android.app.snapshot.ui.common.ExtraItemsManager
import tiiehenry.android.app.snapshot.ui.common.ActionConfigManager

class AppConfigFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentAppConfigBinding? = null
    private val binding get() = _binding!!
    private lateinit var packageName: String
    private lateinit var appConfig: AppConfig
    
    private var dismissListener: (() -> Unit)? = null
    private lateinit var shotOptionsManager: ShotOptionsManager
    private lateinit var versionRetentionManager: VersionRetentionManager
    private lateinit var excludePatternsManager: ExcludePatternsManager
    private lateinit var extraItemsManager: ExtraItemsManager
    private lateinit var actionConfigManager: ActionConfigManager


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
        // 初始化 TabLayout
        setupTabLayout()
        
        binding.btnSave.setOnClickListener {
            saveConfig()
            dismiss()
        }
        binding.btnSave.contentDescription = "保存"

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

        // 初始化额外项目管理器
        extraItemsManager = ExtraItemsManager(
            binding.includeExtraItems,
            requireContext(),
            appConfig,
            childFragmentManager
        )

        // 初始化动作配置管理器
        actionConfigManager = ActionConfigManager(
            binding.includeActionConfig,
            requireContext(),
            appConfig.action
        )
        
        // 设置压缩算法选项
        val algorithms = SnapshotApp.getInstance().fileSystem.compressor.supportedAlgorithms()
        actionConfigManager.setCompressAlgorithmOptions(algorithms.toTypedArray())
    }

    private fun setupTabLayout() {
        binding.tabLayout.apply {
            addTab(newTab().setText("项目"))
            addTab(newTab().setText("行为"))
            addTab(newTab().setText("保留"))
            
            addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                    when (tab.position) {
                        0 -> {
                            binding.tabBasic.visibility = View.VISIBLE
                            binding.tabAction.visibility = View.GONE
                            binding.tabRetention.visibility = View.GONE
                        }
                        1 -> {
                            binding.tabBasic.visibility = View.GONE
                            binding.tabAction.visibility = View.VISIBLE
                            binding.tabRetention.visibility = View.GONE
                        }
                        2 -> {
                            binding.tabBasic.visibility = View.GONE
                            binding.tabAction.visibility = View.GONE
                            binding.tabRetention.visibility = View.VISIBLE
                        }
                    }
                }

                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            })
        }
    }

    private fun loadConfig() {
        // 使用截图选项管理器加载配置
        shotOptionsManager.loadConfig()
        // 加载排除模式列表（从 ExcludeConfig 加载按压缩项目分类的排除模式）
        excludePatternsManager.loadFromExcludeConfig(appConfig.excludeConfig)
        // 使用版本保留管理器加载配置
        versionRetentionManager.loadConfig()
        // 加载额外项目列表
        extraItemsManager.loadConfig()
        // 加载动作配置
        actionConfigManager.loadConfig()
    }

    private fun saveConfig() {
        // 使用截图选项管理器保存配置
        appConfig.shotConfig.enabled = shotOptionsManager.getEnabled()
        appConfig.shotConfig.permission = shotOptionsManager.getPermission()
        appConfig.shotConfig.compressItems = shotOptionsManager.getCompressItems()
        // 保存排除模式列表（保存按压缩项目分类的排除模式）
        excludePatternsManager.saveToExcludeConfig(appConfig.excludeConfig)
        
        // 使用动作配置管理器保存配置
        actionConfigManager.saveToActionConfig(appConfig.action)
        
        // 使用版本保留管理器保存配置
        appConfig.shotConfig.versionRetentionConfig = versionRetentionManager.saveToConfig()
        
        // 保存额外项目列表
        extraItemsManager.saveToConfig()

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