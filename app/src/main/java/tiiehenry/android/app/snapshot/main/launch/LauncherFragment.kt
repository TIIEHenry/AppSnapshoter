package tiiehenry.android.app.snapshot.main.launch

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.view.MenuProvider
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SingletonViewModelFactory
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.SnapshotViewModel
import tiiehenry.android.app.snapshot.databinding.FragmentLauncherBinding
import tiiehenry.android.app.snapshot.main.MainActivity
import tiiehenry.android.app.snapshot.main.launch.addgroup.AddGroupBottomSheet
import tiiehenry.android.app.snapshot.main.launch.groupset.GroupSetStickyHeader
import tiiehenry.android.app.snapshot.main.launch.groupsort.GroupSortBottomSheet
import tiiehenry.android.app.snapshot.ui.widget.CollapsibleSearchController

class LauncherFragment : Fragment() {

    private var _binding: FragmentLauncherBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LauncherViewModel by activityViewModels()
    private val snapshotViewModel: SnapshotViewModel by activityViewModels {
        SingletonViewModelFactory(SnapshotApp.getViewModel())
    }
    private lateinit var groupsAdapter: GroupsAdapter
    private var stickySetHeader: GroupSetStickyHeader? = null
    private var searchController: CollapsibleSearchController? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentLauncherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.bindArchiveSources(
            snapshotViewModel.archiveList,
            snapshotViewModel.groupList,
            snapshotViewModel.groupSetList,
        )

        binding.groupsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.groupsRecyclerView.updatePadding(
            bottom = (requireActivity() as MainActivity).floatingNavContentPaddingBottom()
        )

        groupsAdapter = GroupsAdapter(viewModel, snapshotViewModel, childFragmentManager)
        binding.groupsRecyclerView.adapter = groupsAdapter
        stickySetHeader = GroupSetStickyHeader(
            binding.groupsRecyclerView,
            binding.stickySetHeader,
            groupsAdapter,
            snapshotViewModel,
            childFragmentManager,
        ).also { it.attach() }

        viewModel.searchQuery.observe(viewLifecycleOwner) { query ->
            groupsAdapter.updateSearchQuery(query.trim())
        }

        viewModel.displayedArchiveList.observe(viewLifecycleOwner) { items ->
            submitDisplayedList(items)
        }

        snapshotViewModel.isBatchRunning.observe(viewLifecycleOwner) { running ->
            groupsAdapter.isBatchRunning = running == true
        }

        snapshotViewModel.navigateToGroup.observe(viewLifecycleOwner) {
            onNavigatePending()
        }
        snapshotViewModel.navigateToGroupSet.observe(viewLifecycleOwner) {
            onNavigatePending()
        }

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_launcher, menu)
                bindSearchToggle(menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.menu_search -> true
                    R.id.menu_add_group -> {
                        AddGroupBottomSheet.newInstance()
                            .show(childFragmentManager, AddGroupBottomSheet.TAG)
                        true
                    }
                    R.id.menu_collapse_all -> {
                        if (viewModel.searchQuery.value.orEmpty().isBlank()) {
                            snapshotViewModel.collapseAllArchive()
                        }
                        true
                    }
                    R.id.menu_sort_groups -> {
                        showSortGroupsDialog()
                        true
                    }
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    /**
     * MenuProvider 挂 RESUMED：进设置会拆 menu。只重绑 toggle，禁止再 new Controller。
     */
    private fun bindSearchToggle(menu: Menu) {
        val actionView = menu.findItem(R.id.menu_search)?.actionView ?: return
        val toggle = (actionView as? ImageView)
            ?: actionView.findViewById(R.id.btn_archive_search)
            ?: return
        val existing = searchController
        if (existing == null) {
            searchController = CollapsibleSearchController(
                toggle = toggle,
                searchField = binding.searchField,
                transitionHost = binding.root,
                onQueryChanged = { query -> viewModel.searchQuery.value = query },
                hint = getString(R.string.archive_search_hint),
                initialQuery = viewModel.searchQuery.value.orEmpty(),
            )
        } else {
            existing.rebindToggle(toggle)
        }
    }

    private fun submitDisplayedList(items: List<ArchiveListItem>) {
        val query = viewModel.searchQuery.value.orEmpty().trim()
        groupsAdapter.updateSearchQuery(query)
        if (query.isNotEmpty()) {
            groupsAdapter.exitActiveSortModes(binding.groupsRecyclerView)
        }
        val empty = query.isNotEmpty() && items.isEmpty()
        binding.searchEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.groupsRecyclerView.visibility = if (empty) View.GONE else View.VISIBLE
        Log.d("LauncherFragment", "displayedArchiveList size=${items.size} queryBlank=${query.isEmpty()}")
        groupsAdapter.submitList(items) {
            stickySetHeader?.update()
            if (canConsumeNavigateAfterSubmit(
                    viewModel.searchQuery.value.orEmpty(),
                    items,
                    snapshotViewModel.archiveList.value,
                )
            ) {
                tryConsumeNavigate()
            }
        }
    }

    /**
     * 有 pending 且 query 非空：只 clearSearch，本拍不 consume。
     * query 已空白时也不对本拍 currentList consume（刚清空搜索时 raw submitList 可能未 commit）。
     * tryConsumeNavigate 仅在未过滤 archiveList 的 submitList commit 里。
     */
    private fun onNavigatePending() {
        val hasPending = snapshotViewModel.navigateToGroup.value != null ||
            snapshotViewModel.navigateToGroupSet.value != null
        if (!hasPending) return
        if (viewModel.searchQuery.value.orEmpty().isNotBlank()) {
            viewModel.clearSearch()
            binding.searchField.searchInput.setText("")
            searchController?.collapse()
            return
        }
        val items = viewModel.displayedArchiveList.value ?: return
        submitDisplayedList(items)
    }

    /**
     * 仅在 [androidx.recyclerview.widget.ListAdapter.submitList] commit 后读 currentList；禁止 observe 里立刻 indexOfFirst。
     */
    private fun tryConsumeNavigate() {
        val setId = snapshotViewModel.navigateToGroupSet.value
        if (setId != null) {
            val index = groupsAdapter.currentList.indexOfFirst {
                it is ArchiveListItem.SetHeader && it.set.id == setId
            }
            if (index >= 0) {
                binding.groupsRecyclerView.scrollToPosition(index)
                snapshotViewModel.navigateToGroupSet.value = null
            }
            return
        }
        val groupId = snapshotViewModel.navigateToGroup.value ?: return
        val index = groupsAdapter.currentList.indexOfFirst {
            it is ArchiveListItem.GroupCard && it.group.id == groupId
        }
        if (index >= 0) {
            binding.groupsRecyclerView.scrollToPosition(index)
            val packageName = snapshotViewModel.consumePendingNavigatePackage()
            snapshotViewModel.navigateToGroup.value = null
            if (!packageName.isNullOrBlank()) {
                binding.groupsRecyclerView.post {
                    val holder = binding.groupsRecyclerView
                        .findViewHolderForAdapterPosition(index) as? GroupsAdapter.GroupViewHolder
                    holder?.scrollToPackage(packageName)
                }
            }
        }
    }

    private fun showSortGroupsDialog() {
        val bottomSheet = GroupSortBottomSheet.newInstance()
        bottomSheet.setOnSortSavedListener {
            snapshotViewModel.loadGroups()
        }
        bottomSheet.show(childFragmentManager, GroupSortBottomSheet.TAG)
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            snapshotViewModel.loadGroups()
        }
    }

    override fun onDestroyView() {
        stickySetHeader?.detach()
        stickySetHeader = null
        searchController = null
        super.onDestroyView()
        _binding = null
    }
}

/**
 * 仅当提交的是未过滤 [archiveList] 且 query 空白才允许 consume。
 * 身份比较必须是引用（`===`），不能用 currentList（ListAdapter 会包一层 unmodifiable）。
 */
internal fun canConsumeNavigateAfterSubmit(
    query: String,
    submittedItems: List<ArchiveListItem>,
    archiveList: List<ArchiveListItem>?,
): Boolean = query.isBlank() && archiveList != null && submittedItems === archiveList
