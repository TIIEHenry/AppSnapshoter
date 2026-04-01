package tiiehenry.android.app.snapshot.main.apps

import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.app.AppFilterHelper
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.app.tag.AppTagHelper
import tiiehenry.android.app.snapshot.main.settings.IgnoreAppsConfig
import tiiehenry.android.app.snapshot.ui.widget.TagsFilterLayout
import tiiehenry.android.snapshot.app.UserInfoHide

/**
 * 应用列表 UI 组件的封装类
 * 封装了用户Tab切换、应用过滤、标签过滤、搜索等公共逻辑
 * 可被 Fragment 或 BottomSheetDialogFragment 复用
 */
class AppsListComponent<VB : ViewBinding>(
    private val fragment: Fragment,
    private val binding: VB,
    private val viewModel: AppsViewModel,
    private val callbacks: Callbacks<VB>
) {

    private var userList: List<UserInfoHide> = emptyList()

    interface Callbacks<VB : ViewBinding> {
        fun getRecyclerView(binding: VB): RecyclerView
        fun getUserTabLayout(binding: VB): TabLayout
        fun getFilterChipGroup(binding: VB): ChipGroup
        fun getTagsFilterLayout(binding: VB): TagsFilterLayout
        fun getSearchView(binding: VB): SearchView
        fun setupRecyclerViewAdapter(binding: VB)
        fun onAppsLoadingStateChanged(isLoading: Boolean)
        fun onFilteredAppsChanged(apps: List<AppInfo>)
        val filterIgnoredApps: Boolean
    }

    fun onViewCreated(viewLifecycleOwner: LifecycleOwner) {
        // 设置 RecyclerView
        callbacks.getRecyclerView(binding).layoutManager =
            LinearLayoutManager(fragment.requireContext())
        callbacks.setupRecyclerViewAdapter(binding)

        // 设置 Filter ChipGroup
        setupFilterChips()

        // 设置 Tags Filter
        setupTagsFilter()

        // 设置用户Tab
        userList = SnapshotApp.getInstance().appManager.users
        setupUserTabs()

        // 观察全局ViewModel的appList
        SnapshotApp.getViewModel().appsList.observe(viewLifecycleOwner) { apps ->
            callbacks.onAppsLoadingStateChanged(true)
            fragment.lifecycleScope.launch(Dispatchers.Default) {
                // 过滤已忽略的应用并排序
                val filteredAppsMap = apps.mapValues {
                    if (callbacks.filterIgnoredApps) {
                        IgnoreAppsConfig.filterIgnoredApps(it.value)
                    } else {
                        it.value
                    }.sortedBy { app -> app.label.lowercase() }
                }
                // 重置标签过滤状态并使用新的setAppsMap方法
                viewModel.clearTagFilter()
                viewModel.setAppsMap(filteredAppsMap)
                withContext(Dispatchers.Main) {
                    // 更新标签过滤器
                    updateTagsFilter()
                    callbacks.onAppsLoadingStateChanged(false)
                }
            }
        }

        // 观察过滤后的列表
        viewModel.filteredAppList.observe(viewLifecycleOwner) { apps ->
            callbacks.onFilteredAppsChanged(apps)
        }

        // 搜索功能
        callbacks.getSearchView(binding)
            .setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    return false
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    viewModel.filterApps(newText ?: "")
                    return true
                }
            })
    }

    private fun setupUserTabs() {
        val tabLayout = callbacks.getUserTabLayout(binding)
        tabLayout.removeAllTabs()

        val tabs = userList.map { userInfo ->
            val tab = tabLayout.newTab()
            tab.text = userInfo.name ?: if (userInfo.id == 0) "主用户" else "用户 ${userInfo.id}"
            tab.tag = userInfo
            tab
        }
        tabs.forEach { tab ->
            tabLayout.addTab(tab)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val userInfo = tab.tag as UserInfoHide
                viewModel.setUserFilter(userInfo.id)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        tabs.firstOrNull()?.select()
    }

    private fun setupFilterChips() {
        AppFilterHelper.setupFilterChips(
            callbacks.getFilterChipGroup(binding),
            fragment.requireContext()
        ) { filterType ->
            viewModel.setFilterType(filterType)
        }
    }

    /**
     * 设置标签过滤器
     */
    private fun setupTagsFilter() {
        callbacks.getTagsFilterLayout(binding).setOnTagSelectionChangedListener { selectedTagIds ->
            viewModel.setSelectedTags(selectedTagIds)
        }
    }

    /**
     * 更新标签过滤器显示
     */
    private fun updateTagsFilter() {
        val allTags = AppTagHelper.getAllAvailableTags()
        // setTags默认会清除选中状态
        callbacks.getTagsFilterLayout(binding).setTags(allTags)
    }
}

