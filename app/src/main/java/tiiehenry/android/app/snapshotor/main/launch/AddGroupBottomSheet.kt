package tiiehenry.android.app.snapshotor.main.launch

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.databinding.BottomSheetAddGroupBinding

class AddGroupBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddGroupBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LauncherViewModel by activityViewModels()

    private val openDocumentTreeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                onPathSelected(uri)
            }
        }
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

        binding.etGroupPath.setOnClickListener {
            openPathSelector()
        }

        binding.etGroupPath.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                openPathSelector()
            }
        }

        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        binding.btnConfirm.setOnClickListener {
            val groupName = binding.etGroupName.text.toString().trim()
            val groupPath = binding.etGroupPath.text.toString().trim()

            if (groupName.isNotEmpty() && groupPath.isNotEmpty()) {
                SnapShotApp.getViewModel().addGroup(groupName, groupPath)
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

    private fun openPathSelector() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        openDocumentTreeLauncher.launch(intent)
    }

    private fun onPathSelected(uri: Uri) {
        val absolutePath = uriToAbsolutePath(uri)
        binding.etGroupPath.setText(absolutePath)

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
