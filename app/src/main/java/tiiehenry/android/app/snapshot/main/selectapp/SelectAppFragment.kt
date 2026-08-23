package tiiehenry.android.app.snapshot.main.selectapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.widget.ImageButton
import android.widget.ImageView
import com.google.android.material.tabs.TabLayout
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SingletonViewModelFactory
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.databinding.FragmentSelectAppBinding
import tiiehenry.android.app.snapshot.databinding.LayoutSearchFieldBinding
import tiiehenry.android.app.snapshot.main.apps.AppsListComponent
import tiiehenry.android.app.snapshot.main.apps.AppsViewModel
import tiiehenry.android.app.snapshot.ui.widget.TagsFilterLayout
import tiiehenry.android.snapshot.app.IAppManager

class SelectAppFragment : BottomSheetDialogFragment(), AppsListComponent.Callbacks<FragmentSelectAppBinding> {

    private var _binding: FragmentSelectAppBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppsViewModel by viewModels()
    private val snapshotViewModel: SnapshotViewModel by activityViewModels {
        SingletonViewModelFactory(SnapshotApp.getViewModel())
    }
    private val appManager: IAppManager get() = SnapshotApp.getInstance().appManager
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
        appsListComponent = AppsListComponent(this, binding, viewModel, snapshotViewModel, appManager, this)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.resetFilters()
        appsListComponent.onViewCreated(viewLifecycleOwner)
    }

    override fun onResume() {
        super.onResume()
        if (::appsListComponent.isInitialized) {
            appsListComponent.onResume()
        }
    }

    override fun getRecyclerView(binding: FragmentSelectAppBinding): RecyclerView = binding.appsRecyclerView

    override fun getUserTabLayout(binding: FragmentSelectAppBinding): TabLayout =
        binding.appsFilterRow.userTabLayout

    override fun getFilterSystemButton(binding: FragmentSelectAppBinding): ImageButton =
        binding.appsFilterRow.btnFilterSystem

    override fun getFilterUserButton(binding: FragmentSelectAppBinding): ImageButton =
        binding.appsFilterRow.btnFilterUser

    override fun getUngroupedFilterButton(binding: FragmentSelectAppBinding): ImageButton =
        binding.appsFilterRow.btnFilterUngrouped

    override fun getGroupedFilterButton(binding: FragmentSelectAppBinding): ImageButton =
        binding.appsFilterRow.btnFilterGrouped

    override fun getTagsFilterLayout(binding: FragmentSelectAppBinding): TagsFilterLayout = binding.tagsFilterLayout

    override fun getSearchFieldBinding(binding: FragmentSelectAppBinding): LayoutSearchFieldBinding =
        binding.searchField

    override fun getSearchToggle(binding: FragmentSelectAppBinding): ImageView =
        binding.appsFilterRow.btnSearchToggle

    override fun getSearchTransitionHost(binding: FragmentSelectAppBinding): ViewGroup =
        binding.appsFilterHeader

    override fun setupRecyclerViewAdapter(binding: FragmentSelectAppBinding) {
        selectAppAdapter = SelectAppAdapter(
            membershipIndexProvider = {
                tiiehenry.android.app.snapshot.group.GroupMembershipResolver.buildMembershipIndex(
                    snapshotViewModel.groupList.value.orEmpty()
                )
            },
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
            } else {
                // 提示用户选择应用
                Toast.makeText(requireContext(), R.string.please_select_apps, Toast.LENGTH_SHORT).show()
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
        if (isLoading) {
            binding.progressBar.visibility = View.VISIBLE
            binding.appsRecyclerView.visibility = View.GONE
        } else {
            binding.progressBar.visibility = View.GONE
            binding.appsRecyclerView.visibility = View.VISIBLE
        }
    }

    override fun onFilteredAppsChanged(apps: List<AppInfo>) {
        selectAppAdapter.submitList(apps)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}