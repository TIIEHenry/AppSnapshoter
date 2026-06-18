package tiiehenry.android.app.snapshot.main

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navOptions
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
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
import tiiehenry.android.app.snapshot.main.settings.SettingsActivity
import tiiehenry.android.app.snapshot.main.timeline.TimelineFragment
import tiiehenry.android.app.snapshot.ui.widget.FloatingBottomNav
import android.R as AndroidR

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
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

    private var navigationBarInsetBottom = 0

    private data class BottomNavTab(
        val button: ImageButton,
        @IdRes val destinationId: Int,
    )

    private lateinit var bottomNavTabs: List<BottomNavTab>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
        setupWindowInsets()
        setSupportActionBar(binding.toolbar)

        // 使用MenuProvider实现菜单
        setupMenuProvider()

        // 设置 Navigation
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            ?: throw IllegalStateException("NavHostFragment not found")
        navController = navHostFragment.navController

        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.launcherFragment,
                R.id.timelineFragment,
                R.id.appsFragment,
            )
        )
        binding.toolbar.setupWithNavController(navController, appBarConfiguration)
        setupBottomNavigation()
        FloatingBottomNav.setup(this, binding.bottomNavigationContainer)
        applyToolbarStyle()

        // 先检查权限，检查通过后再加载数据
        if (savedInstanceState == null) {
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

    private fun setupBottomNavigation() {
        bottomNavTabs = listOf(
            BottomNavTab(binding.bottomNavArchive, R.id.launcherFragment),
            BottomNavTab(binding.bottomNavTimeline, R.id.timelineFragment),
            BottomNavTab(binding.bottomNavApps, R.id.appsFragment),
        )
        val navOptions = bottomNavNavOptions()
        bottomNavTabs.forEach { tab ->
            tab.button.setOnClickListener {
                navigateToBottomNavTab(tab.destinationId, navOptions)
            }
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            updateBottomNavSelection(destination.id)
            applyToolbarStyle()
            binding.navHostFragment.post { applyFloatingNavContentPadding() }
        }
        updateBottomNavSelection(navController.currentDestination?.id)
    }

    fun selectBottomNavTab(@IdRes destinationId: Int) {
        navigateToBottomNavTab(destinationId, bottomNavNavOptions())
    }

    private fun navigateToBottomNavTab(@IdRes destinationId: Int, navOptions: NavOptions) {
        if (navController.currentDestination?.id == destinationId) return
        navController.navigate(destinationId, null, navOptions)
    }

    private fun bottomNavNavOptions(): NavOptions {
        return navOptions {
            launchSingleTop = true
            restoreState = true
            popUpTo(navController.graph.startDestinationId) {
                saveState = true
            }
        }
    }

    private fun updateBottomNavSelection(@IdRes currentDestinationId: Int?) {
        bottomNavTabs.forEach { tab ->
            tab.button.isSelected = tab.destinationId == currentDestinationId
        }
    }

    private fun applyToolbarStyle() {
        val surfaceColor = ContextCompat.getColor(this, R.color.surface)
        binding.toolbar.setBackgroundColor(surfaceColor)
        binding.toolbar.backgroundTintList = null
        binding.toolbarHeader.setBackgroundColor(surfaceColor)
    }

    private fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        val isLightTheme = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) !=
            Configuration.UI_MODE_NIGHT_YES
        windowInsetsController.isAppearanceLightStatusBars = isLightTheme
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarHeader) { view, windowInsets ->
            val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInsets.top)
            windowInsets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.coordinator) { _, windowInsets ->
            val navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            navigationBarInsetBottom = navInsets.bottom
            val baseMargin = resources.getDimensionPixelSize(R.dimen.floating_nav_bottom_margin)
            val lp = binding.bottomNavigationContainer.layoutParams as
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            lp.bottomMargin = baseMargin + navInsets.bottom
            lp.marginStart = 0
            lp.marginEnd = 0
            binding.bottomNavigationContainer.layoutParams = lp
            applyFloatingNavContentPadding()
            windowInsets
        }
        ViewCompat.requestApplyInsets(binding.coordinator)
    }

    fun floatingNavContentPaddingBottom(): Int {
        val margin = resources.getDimensionPixelSize(R.dimen.floating_nav_bottom_margin)
        val navHeight = resources.getDimensionPixelSize(R.dimen.floating_nav_height)
        val gap = resources.getDimensionPixelSize(R.dimen.floating_nav_content_gap)
        return navigationBarInsetBottom + margin + navHeight + gap
    }

    private fun applyFloatingNavContentPadding() {
        val fragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
            ?.childFragmentManager?.primaryNavigationFragment ?: return
        if (fragment is TimelineFragment) {
            fragment.updateBottomContentPadding()
            return
        }
        fragment.view?.findRecyclerView()?.updatePadding(bottom = floatingNavContentPaddingBottom())
    }

    private fun View.findRecyclerView(): RecyclerView? {
        if (this is RecyclerView) return this
        if (this is ViewGroup) {
            for (index in 0 until childCount) {
                getChildAt(index).findRecyclerView()?.let { return it }
            }
        }
        return null
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
                        val message = getString(R.string.provider_check_service_connect_failed, e.message)
                        rootCheckState = CheckState.Failed(message)
                        dialogBinding?.let { binding ->
                            setItemFailed(
                                binding.iconRootPermission,
                                binding.statusRootPermission,
                                binding.progressRootPermission,
                                message
                            )
                        }
                        providers.bindRootService()
                    }
                } else {
                    rootPermissionOk = false
                    val message = getString(R.string.provider_check_no_root)
                    rootCheckState = CheckState.Failed(message)
                    dialogBinding?.let { binding ->
                        setItemFailed(
                            binding.iconRootPermission,
                            binding.statusRootPermission,
                            binding.progressRootPermission,
                            message
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
            val message = getString(R.string.provider_check_no_all_files_access)
            filesAccessCheckState = CheckState.Failed(message)
            dialogBinding?.let { binding ->
                setItemFailed(
                    binding.iconFileSystem,
                    binding.statusFileSystem,
                    binding.progressFileSystem,
                    message
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
                binding.tvErrorMessage.text = getString(R.string.provider_check_error_no_root)
                binding.tvErrorMessage.visibility = View.VISIBLE
            } else if (!allFilesAccessOk) {
                binding.tvErrorMessage.text = getString(R.string.provider_check_error_no_files_access)
                binding.tvErrorMessage.visibility = View.VISIBLE
            } else {
                binding.tvErrorMessage.visibility = View.GONE
            }
        }
    }

    private fun setItemLoading(icon: ImageView, status: TextView, progress: ProgressBar) {
        icon.setImageResource(AndroidR.drawable.ic_popup_sync)
        icon.clearColorFilter()
        status.text = getString(R.string.provider_check_in_progress)
        status.setTextColor(currentThemeTextColor())
        progress.visibility = View.VISIBLE
    }

    private fun setItemSuccess(icon: ImageView, status: TextView, progress: ProgressBar) {
        icon.setImageResource(R.drawable.check_success)
        icon.setColorFilter(ContextCompat.getColor(this, R.color.status_success))
        status.text = getString(R.string.provider_check_connected)
        status.setTextColor(ContextCompat.getColor(this, R.color.status_success))
        progress.visibility = View.GONE
    }

    private fun setItemFailed(
        icon: ImageView,
        status: TextView,
        progress: ProgressBar,
        errorMsg: String?
    ) {
        icon.setImageResource(R.drawable.check_error)
        icon.setColorFilter(ContextCompat.getColor(this, R.color.status_error))
        status.text = getString(R.string.provider_check_failed_retry)
        status.setTextColor(ContextCompat.getColor(this, R.color.status_error))
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