package tiieherny.android.app.snapshotor.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import tiieherny.android.app.snapshotor.R

class AppConfigActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val packageName = intent.getStringExtra("packageName") ?: ""
        if (packageName.isEmpty()) {
            finish()
            return
        }

        // 显示AppConfigFragment作为BottomSheet
        val fragment = AppConfigFragment.newInstance(packageName)
        fragment.show(supportFragmentManager, fragment.tag)
        
        // 设置fragment监听器，在fragment关闭时结束Activity
        fragment.setOnDismissListener {
            finish()
        }
    }
}