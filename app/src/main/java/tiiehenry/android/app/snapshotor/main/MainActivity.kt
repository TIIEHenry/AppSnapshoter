package tiiehenry.android.app.snapshotor.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import tiiehenry.android.app.snapshotor.R
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.databinding.ActivityMainBinding
import tiiehenry.android.app.snapshotor.main.apps.AppsFragment
import tiiehenry.android.app.snapshotor.main.launch.LauncherFragment
import tiiehenry.android.app.snapshotor.main.settings.SettingsActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

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
            SnapShotApp.getViewModel().loadData()
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

}