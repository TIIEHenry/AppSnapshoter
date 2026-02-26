package tiiehenry.android.app.snapshotor.main.launch

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import tiiehenry.android.app.snapshotor.R
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.databinding.FragmentLauncherBinding

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
        SnapShotApp.getViewModel().groupList.observe(viewLifecycleOwner) { groups ->
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
                    else -> false
                }
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun showAddGroupDialog() {
        val inputView = EditText(requireContext()).apply {
            hint = "请输入分组名称"
            setPadding(50, 30, 50, 30)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("添加分组")
            .setView(inputView)
            .setPositiveButton("确定") { _, _ ->
                val groupName = inputView.text.toString().trim()
                if (groupName.isNotEmpty()) {
                    SnapShotApp.getViewModel().addGroup(groupName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}