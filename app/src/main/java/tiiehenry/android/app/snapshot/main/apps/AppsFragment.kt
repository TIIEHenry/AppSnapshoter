package tiiehenry.android.app.snapshot.main.apps

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import com.google.android.material.tabs.TabLayout
import androidx.recyclerview.widget.RecyclerView
import tiiehenry.android.app.snapshot.main.launch.app.AppConfigFragment
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.databinding.FragmentAppsBinding
import tiiehenry.android.app.snapshot.databinding.LayoutSearchFieldBinding
import tiiehenry.android.app.snapshot.ui.widget.TagsFilterLayout

class AppsFragment : BaseAppsFragment<FragmentAppsBinding>() {

    private lateinit var appsAdapter: AppsAdapter

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentAppsBinding {
        return FragmentAppsBinding.inflate(inflater, container, false)
    }

    override fun getRecyclerView(binding: FragmentAppsBinding): RecyclerView = binding.appsRecyclerView

    override fun getUserTabLayout(binding: FragmentAppsBinding): TabLayout =
        binding.appsFilterRow.userTabLayout

    override fun getFilterSystemButton(binding: FragmentAppsBinding): ImageButton =
        binding.appsFilterRow.btnFilterSystem

    override fun getFilterUserButton(binding: FragmentAppsBinding): ImageButton =
        binding.appsFilterRow.btnFilterUser

    override fun getTagsFilterLayout(binding: FragmentAppsBinding): TagsFilterLayout = binding.tagsFilterLayout

    override fun getSearchFieldBinding(binding: FragmentAppsBinding): LayoutSearchFieldBinding =
        binding.searchField

    override fun getSearchToggle(binding: FragmentAppsBinding): ImageView =
        binding.appsFilterRow.btnSearchToggle

    override fun getSearchTransitionHost(binding: FragmentAppsBinding): ViewGroup =
        binding.appsFilterHeader

    override fun setupRecyclerViewAdapter(binding: FragmentAppsBinding) {
        appsAdapter = AppsAdapter { appInfo ->
            // 显示AppConfigFragment作为BottomSheet
            val fragment = AppConfigFragment.newInstance(appInfo.packageName, appInfo.userId)
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