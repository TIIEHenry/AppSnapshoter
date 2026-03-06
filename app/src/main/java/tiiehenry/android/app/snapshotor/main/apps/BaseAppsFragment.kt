package tiiehenry.android.app.snapshotor.main.apps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.app.AppFilterHelper
import tiiehenry.android.app.snapshotor.app.AppInfo
import tiiehenry.android.app.snapshotor.app.AppTagHelper
import tiiehenry.android.app.snapshotor.config.IgnoreAppsConfig
import tiiehenry.android.app.snapshotor.ui.common.TagsFilterLayout
import tiiehenry.android.snapshotor.app.UserInfoParcelable

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

    private var userList: List<UserInfoParcelable> = emptyList()

    interface Callbacks<VB : ViewBinding> {
        fun getRecyclerView(binding: VB): RecyclerView
        fun getUserTabLayout(binding: VB): TabLayout
        fun getFilterSpinner(binding: VB): android.widget.Spinner
        fun getTagsFilterLayout(binding: VB): TagsFilterLayout
        fun getSearchView(binding: VB): SearchView
        fun setupRecyclerViewAdapter(binding: VB)
        fun onAppsLoadingStateChanged(isLoading: Boolean)
        fun onFilteredAppsChanged(apps: List<AppInfo>)
        val filterIgnoredApps: Boolean
    }

    fun onViewCreated(viewLifecycleOwner: LifecycleOwner) {
        // 设置 RecyclerView
        callbacks.getRecyclerView(binding).layoutManager = LinearLayoutManager(fragment.requireContext())
        callbacks.setupRecyclerViewAdapter(binding)

        // 设置 Spinner
        setupFilterSpinner()

        // 设置 Tags Filter
        setupTagsFilter()

        // 设置用户Tab
        userList = SnapShotApp.getInstance().appManager.users
        setupUserTabs()

        // 观察全局ViewModel的appList
        SnapShotApp.getViewModel().appsList.observe(viewLifecycleOwner) { apps ->
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
        callbacks.getSearchView(binding).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
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
            tab.text =
                userInfo.name.ifEmpty { if (userInfo.id == 0) "主用户" else "用户 ${userInfo.id}" }
            tab.tag = userInfo
            tab
        }
        tabs.forEach { tab ->
            tabLayout.addTab(tab)
        }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val userInfo = tab.tag as UserInfoParcelable
                viewModel.setUserFilter(userInfo.id)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        tabs.firstOrNull()?.select()
    }

    private fun setupFilterSpinner() {
        AppFilterHelper.setupFilterSpinner(
            callbacks.getFilterSpinner(binding),
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

/**
 * 应用列表 Fragment 的抽象基类（适用于普通 Fragment）
 */
abstract class BaseAppsFragment<VB : ViewBinding> : Fragment(), AppsListComponent.Callbacks<VB> {

    protected var _binding: VB? = null
    protected val binding get() = _binding!!
    protected val viewModel: AppsViewModel by activityViewModels()

    /**
     * 是否过滤已忽略的应用，子类可覆盖此属性
     */
    override val filterIgnoredApps: Boolean = false

    /**
     * 创建 ViewBinding 实例，由子类实现
     */
    protected abstract fun createBinding(inflater: LayoutInflater, container: ViewGroup?): VB

    /**
     * 获取 RecyclerView 实例，由子类实现
     */
    abstract override fun getRecyclerView(binding: VB): RecyclerView

    /**
     * 获取 UserTabLayout 实例，由子类实现
     */
    abstract override fun getUserTabLayout(binding: VB): TabLayout

    /**
     * 获取 FilterSpinner 实例，由子类实现
     */
    abstract override fun getFilterSpinner(binding: VB): android.widget.Spinner

    /**
     * 获取 TagsFilterLayout 实例，由子类实现
     */
    abstract override fun getTagsFilterLayout(binding: VB): TagsFilterLayout

    /**
     * 获取 SearchView 实例，由子类实现
     */
    abstract override fun getSearchView(binding: VB): SearchView

    /**
     * 设置 RecyclerView 的 Adapter，由子类实现
     */
    abstract override fun setupRecyclerViewAdapter(binding: VB)

    /**
     * 应用列表加载状态变化回调，子类可覆盖
     */
    abstract override fun onAppsLoadingStateChanged(isLoading: Boolean)

    /**
     * 过滤后的应用列表变化回调，子类可覆盖
     */
    abstract override fun onFilteredAppsChanged(apps: List<AppInfo>)

    private lateinit var appsListComponent: AppsListComponent<VB>

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = createBinding(inflater, container)
        appsListComponent = AppsListComponent(this, binding, viewModel, this)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appsListComponent.onViewCreated(viewLifecycleOwner)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
