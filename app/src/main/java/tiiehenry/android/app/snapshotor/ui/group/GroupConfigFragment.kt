package tiiehenry.android.app.snapshotor.ui.group

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.config.GroupConfig
import tiiehenry.android.app.snapshotor.databinding.FragmentGroupConfigBinding
import tiiehenry.android.app.snapshotor.group.SnapGroup
import tiiehenry.android.app.snapshotor.util.GroupPathPickerHelper

class GroupConfigFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentGroupConfigBinding? = null
    private val binding get() = _binding!!
    private lateinit var groupId: String
    private lateinit var groupConfig: GroupConfig
    private var groupName: String = ""
    private lateinit var userIdSpinner: Spinner
    private val userInfoList = mutableListOf<tiiehenry.android.snapshotor.app.UserInfoParcelable>()
    private var onConfigSavedListener: (() -> Unit)? = null

    private val pathPickerHelper = GroupPathPickerHelper(this) { absolutePath, uri ->
        binding.etRootPath.setText(absolutePath)
        GroupPathPickerHelper.takePersistablePermission(this, uri)
        GroupPathPickerHelper.autoFillGroupName(this, uri, absolutePath, binding.etGroupName)
        // 尝试从 group.json 自动解析 userId 并选中对应项
        val configData = GroupPathPickerHelper.readGroupConfigData(this, uri)
        if (configData != null) {
            val idx = userInfoList.indexOfFirst { it.id == configData.userId }
            if (idx >= 0) userIdSpinner.setSelection(idx)
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
            fragment.groupName = group.name
            fragment.onConfigSavedListener = onConfigSaved
            return fragment
        }
    }

    fun setOnConfigSavedListener(listener: () -> Unit) {
        onConfigSavedListener = listener
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pathPickerHelper.register()
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


    private fun initViews() {
        binding.btnSave.setOnClickListener {
            saveConfig()
            onConfigSavedListener?.invoke()
            dismiss()
        }

        binding.btnDeleteGroup.setOnClickListener {
            showDeleteConfirmDialog()
        }

        // 设置 userIdSpinner

        userIdSpinner = binding.spinnerUserId
        lifecycleScope.launch {
            val users = withContext(Dispatchers.IO) {
                try {
                    SnapShotApp.getInstance().appManager.users ?: emptyList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }
            userInfoList.clear()
            userInfoList.addAll(users)
            val userLabels = userInfoList.map { "${it.name} (${it.id})" }.toTypedArray()
            val userAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, userLabels)
            userAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            userIdSpinner.adapter = userAdapter
            // 填充完成后再同步选中项
            val savedUserId = groupConfig.groupConfigData.userId
            val userIndex = userInfoList.indexOfFirst { it.id == savedUserId }
            if (userIndex >= 0) userIdSpinner.setSelection(userIndex)
        }

        // 设置路径选择器点击监听
        binding.etRootPath.setOnClickListener {
            pathPickerHelper.launch()
        }

        binding.etRootPath.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                pathPickerHelper.launch()
            }
        }
    }

    private fun loadConfig() {
        binding.etGroupName.setText(groupName)
        binding.etRootPath.setText(groupConfig.rootPath)

        // 设置 userIdSpinner 的选中项
        val savedUserId = groupConfig.groupConfigData.userId
        val userIndex = userInfoList.indexOfFirst { it.id == savedUserId }
        if (userIndex >= 0) {
            userIdSpinner.setSelection(userIndex)
        }
    }

    private fun saveConfig() {
        groupName = binding.etGroupName.text.toString()
        groupConfig.groupConfigData.name = groupName
        groupConfig.rootPath = binding.etRootPath.text.toString()

        // 保存 userId
        val selectedUserIndex = userIdSpinner.selectedItemPosition
        if (selectedUserIndex >= 0 && selectedUserIndex < userInfoList.size) {
            groupConfig.groupConfigData.userId = userInfoList[selectedUserIndex].id
        }

        // 保存所有配置到文件
        groupConfig.save()
    }

    private fun showDeleteConfirmDialog() {
        val context = requireContext()
        val group = SnapShotApp.getViewModel().groupList.value?.find { it.id == groupId }

        if (group != null) {
            AlertDialog.Builder(context)
                .setTitle("删除组")
                .setMessage("确定要删除组 ${group.name}[${groupId}] 吗？\n此操作不可恢复。")
                .setPositiveButton("仅删除组") { _, _ ->
                    // 仅删除组配置，不删除文件
                    SnapShotApp.getViewModel().deleteGroup(groupId, deleteFiles = false)
                    dismiss()
                }
                .setNeutralButton("包括文件") { _, _ ->
                    // 删除组配置及关联文件
                    SnapShotApp.getViewModel().deleteGroup(groupId, deleteFiles = true)
                    dismiss()
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