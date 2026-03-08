package tiiehenry.android.app.snapshotor.app

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.config.AppConfig
import tiiehenry.android.app.snapshotor.databinding.FragmentAppConfigBinding
import tiiehenry.android.app.snapshotor.ui.common.ShotOptionsManager

class AppConfigFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentAppConfigBinding? = null
    private val binding get() = _binding!!
    private lateinit var packageName: String
    private lateinit var appConfig: AppConfig
    
    private var dismissListener: (() -> Unit)? = null
    private lateinit var shotOptionsManager: ShotOptionsManager


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
    }

    private fun loadConfig() {
        // 使用截图选项管理器加载配置
        shotOptionsManager.loadConfig()
    }

    private fun saveConfig() {
        // 使用截图选项管理器保存配置
        appConfig.shotConfig.autoSnapshot = shotOptionsManager.getAutoSnapshot()
        appConfig.shotConfig.permission = shotOptionsManager.getPermission()
        appConfig.shotConfig.uninstallArchived = shotOptionsManager.getUninstallArchived()
        appConfig.shotConfig.compressItems = shotOptionsManager.getCompressItems()
        appConfig.shotConfig.compressAlgorithm = shotOptionsManager.getCompressAlgorithm()

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