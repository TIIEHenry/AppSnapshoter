package tiiehenry.android.app.snapshot.utils

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.EditText
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import tiiehenry.android.app.snapshot.config.GroupConfigData
import java.io.File

/**
 * 分组路径选择公共帮助类。
 *
 * 封装了以下公共逻辑：
 * - 打开系统文件夹选择器
 * - URI 转绝对路径（委托 PathHelper）
 * - 读取 group.json 自动填充分组名称
 * - 路径为目录名时回退填充分组名称
 *
 * 使用方式：
 * 1. 在 Fragment.onCreate() 之前（或 registerForActivityResult 允许的时机）调用 [register]
 * 2. 调用 [launch] 打开选择器
 */
class GroupPathPickerHelper(
    private val fragment: Fragment,
    /** 路径选定后的回调，参数为绝对路径字符串 */
    private val onPathPicked: (absolutePath: String, uri: Uri) -> Unit
) {
    private lateinit var launcher: ActivityResultLauncher<Intent>

    /**
     * 必须在 Fragment.onCreate() 中调用，完成 ActivityResultLauncher 注册。
     */
    fun register() {
        launcher = fragment.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    val absolutePath = PathHelper.uriToAbsolutePath(fragment.requireContext(), uri)
                    onPathPicked(absolutePath, uri)
                }
            }
        }
    }

    /**
     * 启动系统文件夹选择器。
     */
    fun launch() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        launcher.launch(intent)
    }

    companion object {
        /**
         * 申请持久化 URI 权限。
         */
        fun takePersistablePermission(fragment: Fragment, uri: Uri) {
            try {
                fragment.requireContext().contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }

        /**
         * 尝试读取路径下的 group.json，若分组名称非空且 [nameEdit] 当前为空，则自动填充。
         * 读取完成后，若 [nameEdit] 仍为空，则用目录名回退填充。
         */
        fun autoFillGroupName(fragment: Fragment, uri: Uri, absolutePath: String, nameEdit: EditText) {
            tryReadGroupConfig(fragment, uri, nameEdit)
            if (nameEdit.text.toString().trim().isEmpty()) {
                nameEdit.setText(File(absolutePath).name)
            }
        }

        /**
         * 尝试从 group.json 读取分组名称并填充到 [nameEdit]（仅在 nameEdit 为空时生效）。
         */
        fun tryReadGroupConfig(fragment: Fragment, uri: Uri, nameEdit: EditText) {
            val data = readGroupConfigData(fragment, uri) ?: return
            if (data.name != null && data.name.isNotEmpty() &&
                nameEdit.text.toString().trim().isEmpty()
            ) {
                nameEdit.setText(data.name)
            }
        }

        /**
         * 尝试从 group.json 读取并返回 [GroupConfigData]（包含 name、userId 等字段）。
         * 如果文件不存在或解析失败则返回 null。
         */
        fun readGroupConfigData(fragment: Fragment, uri: Uri): GroupConfigData? {
            return try {
                val documentFile = DocumentFile.fromTreeUri(fragment.requireContext(), uri)
                val groupConfigFile = documentFile?.findFile("group.json")
                if (groupConfigFile != null && groupConfigFile.exists()) {
                    fragment.requireContext().contentResolver
                        .openInputStream(groupConfigFile.uri)
                        ?.use { inputStream ->
                            val jsonString = inputStream.bufferedReader().readText()
                            GroupConfigData.fromJson(jsonString)
                        }
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
