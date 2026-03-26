package tiiehenry.android.app.snapshot.ui.group

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshot.config.GroupConfig
import tiiehenry.android.app.snapshot.databinding.FragmentGroupShotConfigBinding
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.ui.common.ShotOptionsManager
import tiiehenry.android.app.snapshot.ui.common.VersionRetentionManager
import tiiehenry.android.app.snapshot.ui.common.ExcludePatternsManager
import tiiehenry.android.app.snapshot.ui.common.ActionConfigManager

class GroupShotConfigFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentGroupShotConfigBinding? = null
    private val binding get() = _binding!!
    private lateinit var groupId: String
    private lateinit var groupConfig: GroupConfig
    private lateinit var shotOptionsManager: ShotOptionsManager
    private lateinit var versionRetentionManager: VersionRetentionManager
    private lateinit var excludePatternsManager: ExcludePatternsManager
    private lateinit var actionConfigManager: ActionConfigManager
    private var onConfigSavedListener: (() -> Unit)? = null

    companion object {
        private const val ARG_GROUP_ID = "group_id"

        fun newInstance(group: SnapGroup, onConfigSaved: (() -> Unit)? = null): GroupShotConfigFragment {
            val fragment = GroupShotConfigFragment()
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
        _binding = FragmentGroupShotConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initViews()
        loadConfig()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme).apply {
            setOnShowListener {
                val bottomSheet =
                    findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.let {
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
            onConfigSavedListener?.invoke()
            dismiss()
        }
        binding.btnSave.contentDescription = "保存"

        // 初始化截图选项管理器（分组配置不需要"启用单独控制"开关，始终启用）
        shotOptionsManager = ShotOptionsManager(
            binding.includeShotOptions,
            requireContext(),
            groupConfig.shotConfig,
            showEnabledSwitch = false
        )

        // 初始化排除模式管理器（分组配置需要）
        excludePatternsManager = ExcludePatternsManager(
            binding.includeExcludePatterns,
            requireContext(),
            "", // 分组配置不需要包名作为根路径
            groupConfig.excludeConfig,
            childFragmentManager
        )

        // 初始化版本保留配置管理器（分组配置不需要"启用单独控制"开关，始终启用）
        versionRetentionManager = VersionRetentionManager(
            binding.includeVersionRetention,
            groupConfig.shotConfig.versionRetentionConfig,
            showEnabledSwitch = false
        )


        // 初始化动作配置管理器（分组配置不需要"启用单独控制"开关，始终启用）
        actionConfigManager = ActionConfigManager(
            binding.includeActionConfig,
            requireContext(),
            groupConfig.actionConfig,
            showEnabledSwitch = false
        )
        
        // 设置压缩算法选项
        val algorithms = tiiehenry.android.app.snapshot.SnapshotApp.getInstance().fileSystem.compressor.supportedAlgorithms()
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
        shotOptionsManager.loadConfig()
        // 加载排除模式列表（从 ExcludeConfig 加载按压缩项目分类的排除模式）
        excludePatternsManager.loadFromExcludeConfig(groupConfig.excludeConfig)
        // 使用版本保留管理器加载配置
        versionRetentionManager.loadConfig()
        // 加载动作配置
        actionConfigManager.loadConfig()
    }

    private fun saveConfig() {
        // 使用截图选项管理器保存配置
        groupConfig.shotConfig.permission = shotOptionsManager.getPermission()
        groupConfig.shotConfig.items = shotOptionsManager.getCompressItems()
        // 保存排除模式列表（保存按压缩项目分类的排除模式）
        excludePatternsManager.saveToExcludeConfig(groupConfig.excludeConfig)

        // 使用动作配置管理器保存配置
        actionConfigManager.saveToActionConfig(groupConfig.actionConfig)

        // 使用版本保留管理器保存配置
        groupConfig.shotConfig.versionRetentionConfig = versionRetentionManager.saveToConfig()

        groupConfig.save()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
