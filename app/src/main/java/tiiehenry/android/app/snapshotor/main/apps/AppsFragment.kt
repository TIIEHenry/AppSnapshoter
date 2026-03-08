package tiiehenry.android.app.snapshotor.main.apps

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.SearchView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import tiiehenry.android.app.snapshotor.app.AppConfigFragment
import tiiehenry.android.app.snapshotor.app.AppInfo
import tiiehenry.android.app.snapshotor.databinding.FragmentAppsBinding
import tiiehenry.android.app.snapshotor.ui.common.TagsFilterLayout

class AppsFragment : BaseAppsFragment<FragmentAppsBinding>() {

    private lateinit var appsAdapter: AppsAdapter

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAppsBinding {
        return FragmentAppsBinding.inflate(inflater, container, false)
    }

    override fun getRecyclerView(binding: FragmentAppsBinding): RecyclerView = binding.appsRecyclerView

    override fun getUserTabLayout(binding: FragmentAppsBinding): TabLayout = binding.userTabLayout

    override fun getFilterChipGroup(binding: FragmentAppsBinding): ChipGroup = binding.chipGroupAppFilter

    override fun getTagsFilterLayout(binding: FragmentAppsBinding): TagsFilterLayout = binding.tagsFilterLayout

    override fun getSearchView(binding: FragmentAppsBinding): SearchView = binding.searchView

    override fun setupRecyclerViewAdapter(binding: FragmentAppsBinding) {
        appsAdapter = AppsAdapter { appInfo ->
            // 显示AppConfigFragment作为BottomSheet
            val fragment = AppConfigFragment.newInstance(appInfo.packageName)
            fragment.show(parentFragmentManager, fragment.tag)
        }
        binding.appsRecyclerView.adapter = appsAdapter
    }

    override fun onAppsLoadingStateChanged(isLoading: Boolean) {
        if (isLoading) {
            binding.progressBar.visibility = android.view.View.VISIBLE
            binding.appsRecyclerView.visibility = android.view.View.GONE
        } else {
            binding.progressBar.visibility = android.view.View.GONE
            binding.appsRecyclerView.visibility = android.view.View.VISIBLE
        }
    }

    override fun onFilteredAppsChanged(apps: List<AppInfo>) {
        appsAdapter.submitList(apps)
    }
}