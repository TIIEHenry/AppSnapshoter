package tiiehenry.android.app.snapshot.main

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.databinding.ActivityMainBinding
import tiiehenry.android.app.snapshot.databinding.DialogProviderCheckBinding
import tiiehenry.android.app.snapshot.main.apps.AppsFragment
import tiiehenry.android.app.snapshot.main.launch.LauncherFragment
import tiiehenry.android.app.snapshot.main.settings.SettingsActivity
import android.R as AndroidR

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val providers = SnapshotApp.getInstance().getProviders()

    // 检查项的状态
    private var rootPermissionOk = false
    private var allFilesAccessOk = false

    // 检查项的检查状态（用于延迟显示对话框时更新UI）
    private sealed class CheckState {
        object Idle : CheckState()
        object Loading : CheckState()
        object Success : CheckState()
        data class Failed(val message: String?) : CheckState()
    }

    private var rootCheckState: CheckState = CheckState.Idle
    private var filesAccessCheckState: CheckState = CheckState.Idle

    // 检查对话框
    private var checkDialog: AlertDialog? = null
    private var dialogBinding: DialogProviderCheckBinding? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 实现沉浸式状态栏
        setupImmersiveStatusBar()

        // 使用MenuProvider实现菜单
        setupMenuProvider()

        val launcherFragment = LauncherFragment()
        val appsFragment = AppsFragment()
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_launcher -> {
                    switchFragment(launcherFragment)
                    true
                }

                R.id.nav_apps -> {
                    switchFragment(appsFragment)
                    true
                }

                else -> false
            }
        }

        // 默认显示LauncherFragment
        if (savedInstanceState == null) {
            switchFragment(launcherFragment)
            // 先检查权限，检查通过后再加载数据
            showProviderCheckDialog()
        }
    }

    private fun setupMenuProvider() {
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.menu_settings -> {
                        // 打开设置页面
                        startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                        true
                    }

                    else -> false
                }
            }
        }, this, Lifecycle.State.CREATED)
    }

    private fun setupImmersiveStatusBar() {
        // 启用内容延伸到状态栏和导航栏下方
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 获取系统窗口控制器
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        // 设置状态栏图标为深色（在浅色背景下）
        windowInsetsController.isAppearanceLightStatusBars = true
    }

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun showProviderCheckDialog() {
        // 先开始后台检查
        checkAll()

        // 1秒后检查还没有成功才显示dialog
        scope.launch {
            delay(1000)
            if (!rootPermissionOk || !allFilesAccessOk) {
                // 还没完成，显示对话框
                dialogBinding = DialogProviderCheckBinding.inflate(layoutInflater)
                checkDialog = AlertDialog.Builder(this@MainActivity)
                    .setView(dialogBinding!!.root)
                    .setCancelable(false)
                    .create()
                checkDialog?.show()

                // 设置点击重试
                dialogBinding?.itemRootPermission?.setOnClickListener {
                    if (!rootPermissionOk) checkRootPermission()
                }
                dialogBinding?.itemFileSystem?.setOnClickListener {
                    if (!allFilesAccessOk) checkAllFilesAccess()
                }

                // 根据当前状态更新对话框UI
                updateDialogUI()
            }
        }
    }

    private fun updateDialogUI() {
        dialogBinding?.let { binding ->
            // 更新Root权限检查状态
            when (rootCheckState) {
                is CheckState.Loading -> setItemLoading(
                    binding.iconRootPermission,
                    binding.statusRootPermission,
                    binding.progressRootPermission
                )

                is CheckState.Success -> setItemSuccess(
                    binding.iconRootPermission,
                    binding.statusRootPermission,
                    binding.progressRootPermission
                )

                is CheckState.Failed -> setItemFailed(
                    binding.iconRootPermission,
                    binding.statusRootPermission,
                    binding.progressRootPermission,
                    (rootCheckState as CheckState.Failed).message
                )

                else -> {}
            }

            // 更新文件访问权限检查状态
            when (filesAccessCheckState) {
                is CheckState.Loading -> setItemLoading(
                    binding.iconFileSystem,
                    binding.statusFileSystem,
                    binding.progressFileSystem
                )

                is CheckState.Success -> setItemSuccess(
                    binding.iconFileSystem,
                    binding.statusFileSystem,
                    binding.progressFileSystem
                )

                is CheckState.Failed -> setItemFailed(
                    binding.iconFileSystem,
                    binding.statusFileSystem,
                    binding.progressFileSystem,
                    (filesAccessCheckState as CheckState.Failed).message
                )

                else -> {}
            }
        }
    }

    private fun checkAll() {
        checkRootPermission()
        checkAllFilesAccess()
    }

    private fun checkRootPermission() {
        rootCheckState = CheckState.Loading
        dialogBinding?.let { binding ->
            setItemLoading(
                binding.iconRootPermission,
                binding.statusRootPermission,
                binding.progressRootPermission
            )
        }
        scope.launch {
            try {
                val isRoot = Shell.getShell().isRoot
                if (isRoot) {
                    // 验证 IFileSystem 和 IAppManager 是否可用
                    try {
                        // 在 IO 线程等待 RootService 连接完成并获取服务
                        withContext(Dispatchers.IO) {
                            providers.appManager
                            providers.fileSystem
                        }
                        rootPermissionOk = true
                        rootCheckState = CheckState.Success
                        dialogBinding?.let { binding ->
                            setItemSuccess(
                                binding.iconRootPermission,
                                binding.statusRootPermission,
                                binding.progressRootPermission
                            )
                        }
                    } catch (e: Exception) {
                        rootPermissionOk = false
                        rootCheckState = CheckState.Failed("服务连接失败: ${e.message}")
                        dialogBinding?.let { binding ->
                            setItemFailed(
                                binding.iconRootPermission,
                                binding.statusRootPermission,
                                binding.progressRootPermission,
                                "服务连接失败: ${e.message}"
                            )
                        }
                        providers.bindRootService()
                    }
                } else {
                    rootPermissionOk = false
                    rootCheckState = CheckState.Failed("未获取到Root权限")
                    dialogBinding?.let { binding ->
                        setItemFailed(
                            binding.iconRootPermission,
                            binding.statusRootPermission,
                            binding.progressRootPermission,
                            "未获取到Root权限"
                        )
                    }
                }
            } catch (e: Exception) {
                rootPermissionOk = false
                rootCheckState = CheckState.Failed(e.message)
                dialogBinding?.let { binding ->
                    setItemFailed(
                        binding.iconRootPermission,
                        binding.statusRootPermission,
                        binding.progressRootPermission,
                        e.message
                    )
                }
            }
            checkAllDone()
        }
    }

    private fun checkAllFilesAccess() {
        filesAccessCheckState = CheckState.Loading
        dialogBinding?.let { binding ->
            setItemLoading(
                binding.iconFileSystem,
                binding.statusFileSystem,
                binding.progressFileSystem
            )
        }
        // API 30+ 才需要 MANAGE_EXTERNAL_STORAGE 权限
        val hasAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // API 30 以下默认已有存储权限
            true
        }
        if (hasAccess) {
            allFilesAccessOk = true
            filesAccessCheckState = CheckState.Success
            dialogBinding?.let { binding ->
                setItemSuccess(
                    binding.iconFileSystem,
                    binding.statusFileSystem,
                    binding.progressFileSystem
                )
            }
        } else {
            allFilesAccessOk = false
            filesAccessCheckState = CheckState.Failed("未授权所有文件访问权限")
            dialogBinding?.let { binding ->
                setItemFailed(
                    binding.iconFileSystem,
                    binding.statusFileSystem,
                    binding.progressFileSystem,
                    "未授权所有文件访问权限"
                )
            }
            // 打开所有文件访问权限设置页面
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent =
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
        }
        checkAllDone()
    }

    private fun checkAllDone() {
        if (rootPermissionOk && allFilesAccessOk) {
            // 全部成功，关闭对话框并加载数据
            checkDialog?.dismiss()
            SnapshotApp.getViewModel().loadData()
            return
        }

        // 显示错误信息
        dialogBinding?.let { binding ->
            if (!rootPermissionOk) {
                binding.tvErrorMessage.text = "没有Root权限，请授予Root权限后点击重试"
                binding.tvErrorMessage.visibility = View.VISIBLE
            } else if (!allFilesAccessOk) {
                binding.tvErrorMessage.text = "未授权所有文件访问权限，请授权后点击重试"
                binding.tvErrorMessage.visibility = View.VISIBLE
            } else {
                binding.tvErrorMessage.visibility = View.GONE
            }
        }
    }

    private fun setItemLoading(icon: ImageView, status: TextView, progress: ProgressBar) {
        icon.setImageResource(AndroidR.drawable.ic_popup_sync)
        icon.clearColorFilter()
        status.text = "检查中..."
        status.setTextColor(currentThemeTextColor())
        progress.visibility = View.VISIBLE
    }

    private fun setItemSuccess(icon: ImageView, status: TextView, progress: ProgressBar) {
        icon.setImageResource(R.drawable.check_success)
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
        icon.setImageResource(R.drawable.check_error)
        icon.setColorFilter(0xFFF44336.toInt()) // red
        status.text = "失败 (点击重试)"
        status.setTextColor(0xFFF44336.toInt())
        progress.visibility = View.GONE
    }

    private fun currentThemeTextColor(): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
        return typedValue.data
    }

    override fun onDestroy() {
        super.onDestroy()
        checkDialog?.dismiss()
    }
}