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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.databinding.FragmentLauncherBinding

class LauncherFragment : Fragment() {

    private var _binding: FragmentLauncherBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LauncherViewModel by activityViewModels()
    private lateinit var groupsAdapter: GroupsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLauncherBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.groupsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        groupsAdapter = GroupsAdapter(viewModel, childFragmentManager)
        binding.groupsRecyclerView.adapter = groupsAdapter

        // 观察数据
        SnapshotApp.getViewModel().groupList.observe(viewLifecycleOwner) { groups ->
            Log.d("LauncherFragment", "groupList changed")
            groupsAdapter.submitList(groups)
        }

        // 添加菜单
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_launcher, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.menu_add_group -> {
                        showAddGroupDialog()
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

    private fun showAddGroupDialog() {
        val bottomSheet = AddGroupBottomSheet.newInstance()
        bottomSheet.show(childFragmentManager, AddGroupBottomSheet.TAG)
    }

    private fun showSortGroupsDialog() {
        val bottomSheet = GroupSortBottomSheet.newInstance()
        bottomSheet.setOnSortSavedListener {
            // 保存后刷新 groupsAdapter
            SnapshotApp.getViewModel().groupList.value?.let { groups ->
                groupsAdapter.submitList(groups)
            }
        }
        bottomSheet.show(childFragmentManager, GroupSortBottomSheet.TAG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}