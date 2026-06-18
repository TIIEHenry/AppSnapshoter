package tiiehenry.android.app.snapshot.main.settings

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.databinding.ActivitySettingsBinding
import tiiehenry.android.app.snapshot.databinding.ItemSettingBinding

/**
 * 设置页面 Activity
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSystemBars()
        setupWindowInsets()
        setupToolbar()

        binding.settingsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.settingsRecyclerView.adapter = SettingsAdapter(getSettingsItems())
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.settings)
        }
        applyToolbarStyle()
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
        ViewCompat.setOnApplyWindowInsetsListener(binding.settingsRoot) { view, windowInsets ->
            val navInsets = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navInsets.bottom)
            windowInsets
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
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
            ),
            SettingItem(
                title = getString(R.string.about),
                description = getString(R.string.about_description),
                onClick = {
                    AboutFragment().show(supportFragmentManager, "about")
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
