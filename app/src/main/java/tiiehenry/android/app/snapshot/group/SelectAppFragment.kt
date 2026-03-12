package tiiehenry.android.app.snapshot.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.databinding.FragmentSelectAppBinding
import tiiehenry.android.app.snapshot.main.apps.AppsListComponent
import tiiehenry.android.app.snapshot.main.apps.AppsViewModel
import tiiehenry.android.app.snapshot.ui.common.TagsFilterLayout

class SelectAppFragment : BottomSheetDialogFragment(), AppsListComponent.Callbacks<FragmentSelectAppBinding> {

    private var _binding: FragmentSelectAppBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppsViewModel by activityViewModels()
    private lateinit var selectAppAdapter: SelectAppAdapter
    private lateinit var appsListComponent: AppsListComponent<FragmentSelectAppBinding>

    private var groupId: String? = null
    private var onAppsSelected: ((List<AppInfo>) -> Unit)? = null

    override var filterIgnoredApps: Boolean = false
        private set

    companion object {
        private const val ARG_GROUP_ID = "group_id"
        private const val ARG_FILTER_IGNORED = "filter_ignored"

        fun newInstance(
            groupId: String,
            onAppsSelected: (List<AppInfo>) -> Unit
        ): SelectAppFragment {
            return SelectAppFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_GROUP_ID, groupId)
                    putBoolean(ARG_FILTER_IGNORED, false)
                }
                this.onAppsSelected = onAppsSelected
            }
        }

        /**
         * 创建用于选择要忽略的应用的 Fragment（会过滤掉已忽略的应用）
         */
        fun newInstanceForIgnoreApps(
            groupId: String,
            onAppsSelected: (List<AppInfo>) -> Unit
        ): SelectAppFragment {
            return SelectAppFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_GROUP_ID, groupId)
                    putBoolean(ARG_FILTER_IGNORED, true)
                }
                this.onAppsSelected = onAppsSelected
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getString(ARG_GROUP_ID)
        filterIgnoredApps = arguments?.getBoolean(ARG_FILTER_IGNORED, false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectAppBinding.inflate(inflater, container, false)
        appsListComponent = AppsListComponent(this, binding, viewModel, this)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        appsListComponent.onViewCreated(viewLifecycleOwner)
    }

    override fun getRecyclerView(binding: FragmentSelectAppBinding): RecyclerView = binding.appsRecyclerView

    override fun getUserTabLayout(binding: FragmentSelectAppBinding): TabLayout = binding.userTabLayout

    override fun getFilterChipGroup(binding: FragmentSelectAppBinding): ChipGroup = binding.chipGroupAppFilter

    override fun getTagsFilterLayout(binding: FragmentSelectAppBinding): TagsFilterLayout = binding.tagsFilterLayout

    override fun getSearchView(binding: FragmentSelectAppBinding): SearchView = binding.searchView

    override fun setupRecyclerViewAdapter(binding: FragmentSelectAppBinding) {
        selectAppAdapter = SelectAppAdapter(
            onItemClick = { appInfo ->
                onAppsSelected?.invoke(listOf(appInfo))
                dismiss()
            },
            onMultiSelectModeChanged = { isMultiSelectMode ->
                updateMultiSelectToolbarVisibility(isMultiSelectMode)
            },
            onMultiSelectedAppsChanged = { selectedApps ->
                updateSelectedCount(selectedApps.size)
            }
        )
        binding.appsRecyclerView.adapter = selectAppAdapter

        // 设置多选工具栏按钮事件
        setupMultiSelectToolbar()
    }

    private fun setupMultiSelectToolbar() {
        binding.multiSelectToolbar.confirmButton.setOnClickListener {
            val selectedApps = selectAppAdapter.getSelectedApps()
            if (selectedApps.isNotEmpty()) {
                onAppsSelected?.invoke(selectedApps)
                dismiss()
            }
        }

        binding.multiSelectToolbar.cancelButton.setOnClickListener {
            selectAppAdapter.clearSelection()
            selectAppAdapter.toggleMultiSelectMode() // 退出多选模式
        }

        binding.multiSelectToolbar.selectAllButton.setOnClickListener {
            selectAppAdapter.selectAll()
        }
    }

    private fun updateMultiSelectToolbarVisibility(isVisible: Boolean) {
        binding.multiSelectToolbar.root.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun updateSelectedCount(count: Int) {
        binding.multiSelectToolbar.selectedCountText.text =
            getString(R.string.selected_count, count)
    }

    override fun onAppsLoadingStateChanged(isLoading: Boolean) {
        // SelectAppFragment 不需要显示加载状态
    }

    override fun onFilteredAppsChanged(apps: List<AppInfo>) {
        selectAppAdapter.submitList(apps)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}