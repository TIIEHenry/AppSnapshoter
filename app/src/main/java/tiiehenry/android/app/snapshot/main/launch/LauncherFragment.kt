package tiiehenry.android.app.snapshot.main.launch

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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

class LauncherFragment : Fragment() {

    private var _binding: FragmentLauncherBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LauncherViewModel by activityViewModels()
    private val snapshotViewModel: SnapshotViewModel by activityViewModels {
        SingletonViewModelFactory(SnapshotApp.getViewModel())
    }
    private lateinit var groupsAdapter: GroupsAdapter
    private var stickySetHeader: GroupSetStickyHeader? = null

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

        snapshotViewModel.archiveList.observe(viewLifecycleOwner) { items ->
            Log.d("LauncherFragment", "archiveList changed size=${items.size}")
            groupsAdapter.submitList(items) {
                stickySetHeader?.update()
                tryConsumeNavigate()
            }
        }

        snapshotViewModel.isBatchRunning.observe(viewLifecycleOwner) { running ->
            groupsAdapter.isBatchRunning = running == true
        }

        snapshotViewModel.navigateToGroup.observe(viewLifecycleOwner) {
            tryConsumeNavigate()
        }
        snapshotViewModel.navigateToGroupSet.observe(viewLifecycleOwner) {
            tryConsumeNavigate()
        }

        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_launcher, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.menu_add_group -> {
                        AddGroupBottomSheet.newInstance()
                            .show(childFragmentManager, AddGroupBottomSheet.TAG)
                        true
                    }
                    R.id.menu_collapse_all -> {
                        snapshotViewModel.collapseAllArchive()
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
     * 仅在 [ListAdapter.submitList] commit 后读 currentList；禁止 observe 里立刻 indexOfFirst。
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
            snapshotViewModel.navigateToGroup.value = null
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
        super.onDestroyView()
        _binding = null
    }
}
