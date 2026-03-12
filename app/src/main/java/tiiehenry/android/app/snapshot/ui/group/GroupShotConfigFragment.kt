package tiiehenry.android.app.snapshot.ui.group

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.config.GroupConfig
import tiiehenry.android.app.snapshot.databinding.FragmentGroupShotConfigBinding
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.ui.common.ShotOptionsManager

class GroupShotConfigFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentGroupShotConfigBinding? = null
    private val binding get() = _binding!!
    private lateinit var groupId: String
    private lateinit var groupConfig: GroupConfig
    private lateinit var shotOptionsManager: ShotOptionsManager
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
        binding.btnSave.setOnClickListener {
            saveConfig()
            onConfigSavedListener?.invoke()
            dismiss()
        }

        binding.btnReset.setOnClickListener {
            loadConfig()
        }

        // 初始化截图选项管理器
        shotOptionsManager = ShotOptionsManager(
            binding.includeShotOptions,
            requireContext(),
            groupConfig.shotConfig
        )

        // 设置压缩算法下拉框
        val algorithms = SnapshotApp.getInstance().fileSystem.compressor.supportedAlgorithms()
        shotOptionsManager.setCompressAlgorithmOptions(algorithms.toTypedArray())
    }

    private fun loadConfig() {
        shotOptionsManager.loadConfig()
    }

    private fun saveConfig() {
        groupConfig.shotConfig.autoSnapshot = shotOptionsManager.getAutoSnapshot()
        groupConfig.shotConfig.permission = shotOptionsManager.getPermission()
        groupConfig.shotConfig.uninstallArchived = shotOptionsManager.getUninstallArchived()
        groupConfig.shotConfig.compressItems = shotOptionsManager.getCompressItems()
        groupConfig.shotConfig.compressAlgorithm = shotOptionsManager.getCompressAlgorithm()

        groupConfig.save()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
