package tiiehenry.android.app.snapshotor.main.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshotor.R
import tiiehenry.android.app.snapshotor.databinding.ActivitySettingsBinding
import tiiehenry.android.app.snapshotor.databinding.ItemSettingBinding

/**
 * 设置页面 Activity
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupImmersiveStatusBar()
        // 设置 RecyclerView
        binding.settingsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.settingsRecyclerView.adapter = SettingsAdapter(getSettingsItems())
    }

    private fun setupImmersiveStatusBar() {
        // 启用内容延伸到状态栏和导航栏下方
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // 获取系统窗口控制器
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        // 设置状态栏图标为深色（在浅色背景下）
        windowInsetsController.isAppearanceLightStatusBars = true
    }


    private fun getSettingsItems(): List<SettingItem> {
        return listOf(
            SettingItem(
                title = getString(R.string.ignore_apps),
                description = getString(R.string.ignore_apps_description),
                onClick = {
                    // 以 BottomSheet 样式显示忽略应用页面
                    IgnoreAppsFragment().show(supportFragmentManager, "ignore_apps")
                }
            )
        )
    }

    /**
     * 设置项数据类
     */
    data class SettingItem(
        val title: String,
        val description: String,
        val onClick: () -> Unit
    )

    /**
     * 设置列表适配器
     */
    inner class SettingsAdapter(
        private val items: List<SettingItem>
    ) : RecyclerView.Adapter<SettingsAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemSettingBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(
            private val binding: ItemSettingBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(item: SettingItem) {
                binding.tvTitle.text = item.title
                binding.tvDescription.text = item.description

                binding.root.setOnClickListener {
                    item.onClick()
                }
            }
        }
    }
}
