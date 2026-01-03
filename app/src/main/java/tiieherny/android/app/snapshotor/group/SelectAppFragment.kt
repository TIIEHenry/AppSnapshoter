package tiieherny.android.app.snapshotor.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import tiieherny.android.app.snapshotor.R
import tiieherny.android.app.snapshotor.SnapShotApp
import tiieherny.android.app.snapshotor.app.AppInfo
import tiieherny.android.app.snapshotor.databinding.FragmentSelectAppBinding

class SelectAppFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentSelectAppBinding? = null
    private val binding get() = _binding!!
    private lateinit var selectAppAdapter: SelectAppAdapter

    private var groupId: String? = null
    private var onAppsSelected: ((List<AppInfo>) -> Unit)? = null
    private var currentUserId: Int = 0
    private var allApps: List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()
    private var currentFilterType: AppFilterType = AppFilterType.ALL

    enum class AppFilterType {
        ALL,        // 全部应用
        SYSTEM_ONLY,  // 仅系统应用
        USER_ONLY   // 仅用户应用
    }

    companion object {
        private const val ARG_GROUP_ID = "group_id"

        fun newInstance(groupId: String,  onAppsSelected: (List<AppInfo>) -> Unit): SelectAppFragment {
            return SelectAppFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_GROUP_ID, groupId)
                }
                this.onAppsSelected = onAppsSelected
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = arguments?.getString(ARG_GROUP_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSelectAppBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.appsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
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

        // 设置 Spinner
        setupFilterSpinner()

        // 观察全局ViewModel的appList
        SnapShotApp.getViewModel().appsList.observe(viewLifecycleOwner) { apps ->
            allApps = apps[currentUserId] ?: emptyList()
            setupUserTabs()
            filterApps(binding.searchView.query.toString())
        }

        // 搜索功能
        binding.searchView.setOnQueryTextListener(object : android.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterApps(newText ?: "")
                return true
            }
        })
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
        if (isVisible) {
            binding.multiSelectToolbar.root.visibility = View.VISIBLE
        } else {
            binding.multiSelectToolbar.root.visibility = View.GONE
        }
    }

    private fun updateSelectedCount(count: Int) {
        binding.multiSelectToolbar.selectedCountText.text = getString(R.string.selected_count, count)
    }

    private fun setupFilterSpinner() {
        val filterOptions = arrayOf(
            getString(R.string.app_filter_all),
            getString(R.string.app_filter_system_only),
            getString(R.string.app_filter_user_only)
        )

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAppFilter.adapter = adapter

        binding.spinnerAppFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilterType = when (position) {
                    0 -> AppFilterType.ALL
                    1 -> AppFilterType.SYSTEM_ONLY
                    2 -> AppFilterType.USER_ONLY
                    else -> AppFilterType.ALL
                }
                filterApps(binding.searchView.query.toString())
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
            }
        }
    }

    private fun setupUserTabs() {
        // 获取所有不同的userId
        val userIds = allApps.map { it.userId }.distinct().sorted()

        binding.userTabLayout.removeAllTabs()
        userIds.forEach { userId ->
            val tab = binding.userTabLayout.newTab()
            tab.text = if (userId == 0) "主用户" else "用户 $userId"
            tab.tag = userId
            binding.userTabLayout.addTab(tab)
        }

        // 默认选中第一个tab
        if (binding.userTabLayout.tabCount > 0) {
            binding.userTabLayout.getTabAt(0)?.select()
        }

        // Tab切换监听
        binding.userTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                val userId = tab?.tag as? Int ?: 0
                currentUserId = userId
                filterApps(binding.searchView.query.toString())
                binding.searchView.setQuery("", false)
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun filterApps(query: String) {
        var appsForCurrentUser = allApps.filter { it.userId == currentUserId }
        
        // 根据筛选类型过滤
        appsForCurrentUser = when (currentFilterType) {
            AppFilterType.ALL -> appsForCurrentUser
            AppFilterType.SYSTEM_ONLY -> appsForCurrentUser.filter { 
                it.isSystemApp(it.appManager)
            }
            AppFilterType.USER_ONLY -> appsForCurrentUser.filter { 
                !it.isSystemApp(it.appManager)
            }
        }
        
        // 根据搜索关键词过滤
        if (query.isEmpty()) {
            filteredApps = appsForCurrentUser
        } else {
            filteredApps = appsForCurrentUser.filter {
                it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }
        selectAppAdapter.submitList(filteredApps)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}