package tiiehenry.android.app.snapshotor.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import tiiehenry.android.app.snapshotor.R
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.databinding.ActivityMainBinding
import tiiehenry.android.app.snapshotor.main.apps.AppsFragment
import tiiehenry.android.app.snapshotor.main.launch.LauncherFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // 实现沉浸式状态栏
        setupImmersiveStatusBar()

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_launcher -> {
                    switchFragment(LauncherFragment())
                    true
                }
                R.id.nav_apps -> {
                    switchFragment(AppsFragment())
                    true
                }
                else -> false
            }
        }

        // 默认显示LauncherFragment
        if (savedInstanceState == null) {
            switchFragment(LauncherFragment())
            SnapShotApp.getViewModel().loadData()
        }
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