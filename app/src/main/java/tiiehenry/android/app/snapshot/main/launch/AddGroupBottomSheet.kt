package tiiehenry.android.app.snapshot.main.launch

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.databinding.BottomSheetAddGroupBinding
import tiiehenry.android.app.snapshot.util.GroupPathPickerHelper

class AddGroupBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddGroupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LauncherViewModel by activityViewModels()

    private lateinit var userIdSpinner: Spinner
    private val userInfoList = mutableListOf<tiiehenry.android.snapshot.app.UserInfoParcelable>()

    private val pathPickerHelper = GroupPathPickerHelper(this) { absolutePath, uri ->
        binding.etGroupPath.setText(absolutePath)
        GroupPathPickerHelper.takePersistablePermission(this, uri)
        GroupPathPickerHelper.autoFillGroupName(this, uri, absolutePath, binding.etGroupName)
        // 尝试从 group.json 自动解析 userId 并选中对应项
        val configData = GroupPathPickerHelper.readGroupConfigData(this, uri)
        if (configData != null) {
            val idx = userInfoList.indexOfFirst { it.id == configData.userId }
            if (idx >= 0) userIdSpinner.setSelection(idx)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pathPickerHelper.register()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAddGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        userIdSpinner = binding.spinnerUserId
        lifecycleScope.launch {
            val users = withContext(Dispatchers.IO) {
                try {
                    SnapshotApp.getInstance().appManager.users ?: emptyList()
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
        }

        // 为 TextInputLayout 的 endIcon 设置点击事件
        binding.tilGroupPath.setEndIconOnClickListener {
            pathPickerHelper.launch()
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnConfirm.setOnClickListener {
            val groupName = binding.etGroupName.text.toString().trim()
            val groupPath = binding.etGroupPath.text.toString().trim()

            if (groupName.isNotEmpty() && groupPath.isNotEmpty()) {
                val selectedIndex = userIdSpinner.selectedItemPosition
                val userId = if (selectedIndex >= 0 && selectedIndex < userInfoList.size) {
                    userInfoList[selectedIndex].id
                } else 0
                SnapshotApp.getViewModel().addGroup(groupName, groupPath, userId)
                dismiss()
            } else {
                if (groupName.isEmpty()) {
                    binding.etGroupName.error = "请输入分组名称"
                }
                if (groupPath.isEmpty()) {
                    binding.etGroupPath.error = "请选择分组路径"
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "AddGroupBottomSheet"

        fun newInstance(): AddGroupBottomSheet {
            return AddGroupBottomSheet()
        }
    }
}
