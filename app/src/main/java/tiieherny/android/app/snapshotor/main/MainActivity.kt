package tiieherny.android.app.snapshotor.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import tiieherny.android.app.snapshotor.R
import tiieherny.android.app.snapshotor.SnapShotApp
import tiieherny.android.app.snapshotor.main.apps.AppsFragment
import tiieherny.android.app.snapshotor.main.launch.LauncherFragment

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottom_navigation)
        bottomNav.setOnItemSelectedListener { item ->
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

    private fun switchFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
