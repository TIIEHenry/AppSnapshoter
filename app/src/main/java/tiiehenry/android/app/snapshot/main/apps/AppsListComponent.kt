package tiiehenry.android.app.snapshot.main.apps

import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import android.widget.ImageButton
import android.widget.ImageView
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.app.AppFilterHelper
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.app.tag.AppTagHelper
import tiiehenry.android.app.snapshot.databinding.LayoutSearchFieldBinding
import tiiehenry.android.app.snapshot.main.MainActivity
import tiiehenry.android.app.snapshot.main.settings.IgnoreAppsConfig
import tiiehenry.android.app.snapshot.ui.widget.CollapsibleSearchController
import tiiehenry.android.app.snapshot.ui.widget.TagsFilterLayout
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.app.UserInfoHide

/**
 * 应用列表 UI 组件。
 *
 * 封装用户 Tab、过滤、标签、搜索等公共逻辑，可被 Fragment / BottomSheet 复用。
 *
 * Loading 不变量：`showLoading = isAppsLoading || isLocalProcessing`。
 * [SnapshotViewModel.isAppsLoading] 为 catalog 拉取 SSOT；[appsList] 观察者只做数据绑定，
 * 禁止用其排放直接开关 loading（与 Timeline 的 `isQuerying` 模式一致）。
 */
class AppsListComponent<VB : ViewBinding>(
    private val fragment: Fragment,
    private val binding: VB,
    private val viewModel: AppsViewModel,
    private val snapshotViewModel: SnapshotViewModel,
    private val appManager: IAppManager,
    private val callbacks: Callbacks<VB>
) {

    private var userList: List<UserInfoHide> = emptyList()
    private var searchController: CollapsibleSearchController? = null
    private var isLocalProcessing = false

    interface Callbacks<VB : ViewBinding> {
        fun getRecyclerView(binding: VB): RecyclerView
        fun getUserTabLayout(binding: VB): TabLayout
        fun getFilterSystemButton(binding: VB): ImageButton
        fun getFilterUserButton(binding: VB): ImageButton
        fun getUngroupedFilterButton(binding: VB): ImageButton? = null
        fun getTagsFilterLayout(binding: VB): TagsFilterLayout
        fun getSearchFieldBinding(binding: VB): LayoutSearchFieldBinding
        fun getSearchToggle(binding: VB): ImageView
        fun getSearchTransitionHost(binding: VB): ViewGroup
        fun setupRecyclerViewAdapter(binding: VB)
        fun onAppsLoadingStateChanged(isLoading: Boolean)
        fun onFilteredAppsChanged(apps: List<AppInfo>)
        val filterIgnoredApps: Boolean
    }

    fun onViewCreated(viewLifecycleOwner: LifecycleOwner) {
        // 注入依赖
        viewModel.fileSystem = SnapshotApp.getInstance().fileSystem
        viewModel.groupsProvider = { snapshotViewModel.groupList.value ?: emptyList() }

        // 设置 RecyclerView
        val recyclerView = callbacks.getRecyclerView(binding)
        recyclerView.layoutManager = LinearLayoutManager(fragment.requireContext())
        recyclerView.clipToPadding = false
        (fragment.requireActivity() as? MainActivity)?.let { activity ->
            recyclerView.updatePadding(bottom = activity.floatingNavContentPaddingBottom())
        }
        callbacks.setupRecyclerViewAdapter(binding)

        // 设置 Filter 图标按钮
        setupFilterIconToggles()
        setupUngroupedToggle()

        // 设置 Tags Filter
        setupTagsFilter()

        // 设置用户Tab
        userList = appManager.users
        setupUserTabs()

        snapshotViewModel.isAppsLoading.observe(viewLifecycleOwner) {
            updateLoadingUi()
        }

        // 观察全局ViewModel的appList（只做数据绑定，不驱动 loading）
        snapshotViewModel.appsList.observe(viewLifecycleOwner) { apps ->
            isLocalProcessing = true
            updateLoadingUi()
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
                    isLocalProcessing = false
                    updateLoadingUi()
                }
            }
        }

        // 观察过滤后的列表
        viewModel.filteredAppList.observe(viewLifecycleOwner) { apps ->
            callbacks.onFilteredAppsChanged(apps)
        }

        // 搜索功能
        searchController = CollapsibleSearchController(
            toggle = callbacks.getSearchToggle(binding),
            searchField = callbacks.getSearchFieldBinding(binding),
            transitionHost = callbacks.getSearchTransitionHost(binding),
            onQueryChanged = { query -> viewModel.filterApps(query) },
            hint = fragment.getString(R.string.search_apps_hint)
        )
    }

    private fun updateLoadingUi() {
        callbacks.onAppsLoadingStateChanged(
            snapshotViewModel.isAppsLoading.value == true || isLocalProcessing
        )
    }

    private fun setupUserTabs() {
        val tabLayout = callbacks.getUserTabLayout(binding)
        tabLayout.removeAllTabs()

        val tabs = userList.map { userInfo ->
            val tab = tabLayout.newTab()
            tab.text = userInfo.name ?: if (userInfo.id == 0) {
                fragment.getString(R.string.user_primary)
            } else {
                fragment.getString(R.string.user_named, userInfo.id)
            }
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
        trimUserTabPadding(tabLayout)
    }

    private fun trimUserTabPadding(tabLayout: TabLayout) {
        val startPadding = tabLayout.resources.getDimensionPixelSize(R.dimen.filter_tab_start_padding)
        val endPadding = tabLayout.resources.getDimensionPixelSize(R.dimen.filter_tab_end_padding)
        tabLayout.post {
            for (index in 0 until tabLayout.tabCount) {
                tabLayout.getTabAt(index)?.view?.updatePadding(
                    left = startPadding,
                    top = 0,
                    right = endPadding,
                    bottom = 0
                )
            }
        }
    }

    private fun setupFilterIconToggles() {
        AppFilterHelper.setupFilterIconToggles(
            callbacks.getFilterSystemButton(binding),
            callbacks.getFilterUserButton(binding)
        ) { filterType ->
            viewModel.setFilterType(filterType)
        }
    }

    private fun setupUngroupedToggle() {
        val button = callbacks.getUngroupedFilterButton(binding) ?: return
        button.visibility = View.VISIBLE
        button.isSelected = false
        button.setOnClickListener {
            button.isSelected = !button.isSelected
            viewModel.setUngroupedOnly(button.isSelected)
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

