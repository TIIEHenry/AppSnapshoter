package tiiehenry.android.app.snapshot.main.apps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.ui.widget.TagsFilterLayout

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
     * 获取 FilterChipGroup 实例，由子类实现
     */
    abstract override fun getFilterChipGroup(binding: VB): ChipGroup

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