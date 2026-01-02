package tiieherny.android.app.snapshotor.main.apps

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import tiieherny.android.app.snapshotor.R
import tiieherny.android.app.snapshotor.SnapShotApp
import tiieherny.android.app.snapshotor.app.AppConfigActivity

class AppsFragment : Fragment() {

    private val viewModel: AppsViewModel by activityViewModels()
    private lateinit var searchView: SearchView
    private lateinit var appsRecyclerView: RecyclerView
    private lateinit var appsAdapter: AppsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_apps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        searchView = view.findViewById(R.id.search_view)
        appsRecyclerView = view.findViewById(R.id.apps_recycler_view)

        appsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        appsAdapter = AppsAdapter { appInfo ->
            // 点击item进入AppConfigActivity
            val intent = Intent(requireContext(), AppConfigActivity::class.java)
            intent.putExtra("packageName", appInfo.packageName)
            startActivity(intent)
        }
        appsRecyclerView.adapter = appsAdapter

        // 观察全局ViewModel的appList
        SnapShotApp.getViewModel().appList.observe(viewLifecycleOwner) { apps ->
            viewModel.setAppList(apps.map { it.appInfo })
        }

        // 观察过滤后的列表
        viewModel.filteredAppList.observe(viewLifecycleOwner) { apps ->
            appsAdapter.submitList(apps)
        }

        // 搜索功能
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.filterApps(newText ?: "")
                return true
            }
        })
    }
}
