package tiieherny.android.app.snapshotor.main.apps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import tiieherny.android.app.snapshotor.R
import tiieherny.android.app.snapshotor.SnapShotApp
import tiieherny.android.app.snapshotor.app.AppConfigFragment
import tiieherny.android.app.snapshotor.databinding.FragmentAppsBinding

class AppsFragment : Fragment() {

    private var _binding: FragmentAppsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AppsViewModel by activityViewModels()
    private lateinit var appsAdapter: AppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.appsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        appsAdapter = AppsAdapter { appInfo ->
            // 显示AppConfigFragment作为BottomSheet
            val fragment = AppConfigFragment.newInstance(appInfo.packageName)
            fragment.show(parentFragmentManager, fragment.tag)
        }
        binding.appsRecyclerView.adapter = appsAdapter

        // 观察全局ViewModel的appList
        SnapShotApp.getViewModel().appsList.observe(viewLifecycleOwner) { apps ->
            viewModel.setAppList(apps.flatMap { it.value }.distinctBy { it.packageName })
        }

        // 观察过滤后的列表
        viewModel.filteredAppList.observe(viewLifecycleOwner) { apps ->
            appsAdapter.submitList(apps)
        }

        // 搜索功能
        binding.searchView.setOnQueryTextListener(object : android.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.filterApps(newText ?: "")
                return true
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}