package tiiehenry.android.app.snapshotor

import android.R
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.Shell.GetShellCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshotor.databinding.ActivityProviderCheckBinding
import tiiehenry.android.app.snapshotor.main.MainActivity
import tiiehenry.android.snapshotor.provider.appmanager.AppManagerProviderImpl
import tiiehenry.android.snapshotor.provider.datasyncer.DataSyncerProviderImpl
import tiiehenry.android.snapshotor.provider.filesystem.FileSystemProviderImpl

class ProviderCheckActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProviderCheckBinding

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    //四检查项的状态
    private var rootPermissionOk = false
    private var fileSystemOk = false
    private var appManagerOk = false
    private var dataSyncerOk = false

    val context: Context get() = applicationContext

    val fileSystemProviderImpl by lazy { FileSystemProviderImpl(context, context) }
    val appManagerProviderImpl by lazy { AppManagerProviderImpl(context, context) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProviderCheckBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupImmersiveStatusBar()

        //点击重试
        binding.itemRootPermission.setOnClickListener {
            if (!rootPermissionOk) checkRootPermission()
        }
        binding.itemFileSystem.setOnClickListener {
            if (!fileSystemOk) provideFileSystem()
        }
        binding.itemAppManager.setOnClickListener {
            if (!appManagerOk) provideAppManager()
        }
        binding.itemDataSyncer.setOnClickListener {
            if (!dataSyncerOk) provideDataSyncer()
        }

        Shell.getShell(GetShellCallback { shell: Shell? ->
            provideAll()
        })
    }

    private fun setupImmersiveStatusBar() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = true
    }

    private fun provideAll() {
        checkRootPermission()
        provideFileSystem()
        provideAppManager()
        provideDataSyncer()
    }

    private fun checkRootPermission() {
        setItemLoading(
            binding.iconRootPermission,
            binding.statusRootPermission,
            binding.progressRootPermission
        )
        scope.launch {
            try {
                val isRoot = withContext(Dispatchers.IO) {
                    Shell.getShell().isRoot
                }
                if (isRoot) {
                    rootPermissionOk = true
                    setItemSuccess(
                        binding.iconRootPermission,
                        binding.statusRootPermission,
                        binding.progressRootPermission
                    )
                } else {
                    rootPermissionOk = false
                    setItemFailed(
                        binding.iconRootPermission,
                        binding.statusRootPermission,
                        binding.progressRootPermission,
                        "未获取到Root权限"
                    )
                }
            } catch (e: Exception) {
                rootPermissionOk = false
                setItemFailed(
                    binding.iconRootPermission,
                    binding.statusRootPermission,
                    binding.progressRootPermission,
                    e.message
                )
            }
            checkAllDone()
        }
    }

    private fun provideFileSystem() {
        fileSystemProviderImpl.onInstall()
        setItemLoading(binding.iconFileSystem, binding.statusFileSystem, binding.progressFileSystem)
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    fileSystemProviderImpl.provide()
                }
                SnapShotApp.getInstance().fileSystem = result
                fileSystemOk = true
                setItemSuccess(
                    binding.iconFileSystem,
                    binding.statusFileSystem,
                    binding.progressFileSystem
                )
            } catch (e: Exception) {
                e.printStackTrace()
                fileSystemOk = false
                setItemFailed(
                    binding.iconFileSystem,
                    binding.statusFileSystem,
                    binding.progressFileSystem,
                    e.message
                )
            }
            checkAllDone()
        }
    }

    private fun provideAppManager() {
        appManagerProviderImpl.onInstall()
        setItemLoading(binding.iconAppManager, binding.statusAppManager, binding.progressAppManager)
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    appManagerProviderImpl.provide()
                }
                SnapShotApp.getInstance().appManager = result
                appManagerOk = true
                setItemSuccess(
                    binding.iconAppManager,
                    binding.statusAppManager,
                    binding.progressAppManager
                )
            } catch (e: Exception) {
                e.printStackTrace()
                appManagerOk = false
                setItemFailed(
                    binding.iconAppManager,
                    binding.statusAppManager,
                    binding.progressAppManager,
                    e.message
                )
            }
            checkAllDone()
        }
    }

    private fun provideDataSyncer() {
        setItemLoading(binding.iconDataSyncer, binding.statusDataSyncer, binding.progressDataSyncer)
        scope.launch {
            try {
                val context = applicationContext
                val result = withContext(Dispatchers.IO) {
                    DataSyncerProviderImpl(context, context).provide()
                }
                SnapShotApp.getInstance().dataSyncer = result
                dataSyncerOk = true
                setItemSuccess(
                    binding.iconDataSyncer,
                    binding.statusDataSyncer,
                    binding.progressDataSyncer
                )
            } catch (e: Exception) {
                dataSyncerOk = false
                setItemFailed(
                    binding.iconDataSyncer,
                    binding.statusDataSyncer,
                    binding.progressDataSyncer,
                    e.message
                )
            }
            checkAllDone()
        }
    }

    private fun checkAllDone() {
        if (rootPermissionOk && fileSystemOk && appManagerOk && dataSyncerOk) {
            // 全部成功，跳转 MainActivity
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        // 如果 Root权限检查失败，提示没有 Root权限
        if (!rootPermissionOk) {
            binding.tvErrorMessage.text = "没有Root权限，请授予Root权限后点击重试"
            binding.tvErrorMessage.visibility = View.VISIBLE
        } else {
            binding.tvErrorMessage.visibility = View.GONE
        }
    }

    private fun setItemLoading(icon: ImageView, status: TextView, progress: ProgressBar) {
        icon.setImageResource(R.drawable.ic_popup_sync)
        status.text = "检查中..."
        progress.visibility = View.VISIBLE
    }

    private fun setItemSuccess(icon: ImageView, status: TextView, progress: ProgressBar) {
        icon.setImageResource(R.drawable.ic_input_add)
        icon.setColorFilter(0xFF4CAF50.toInt()) // green
        status.text = "已连接"
        status.setTextColor(0xFF4CAF50.toInt())
        progress.visibility = View.GONE
    }

    private fun setItemFailed(
        icon: ImageView,
        status: TextView,
        progress: ProgressBar,
        errorMsg: String?
    ) {
        icon.setImageResource(R.drawable.ic_delete)
        icon.setColorFilter(0xFFF44336.toInt()) // red
        status.text = "失败 (点击重试)"
        status.setTextColor(0xFFF44336.toInt())
        progress.visibility = View.GONE
    }
}
