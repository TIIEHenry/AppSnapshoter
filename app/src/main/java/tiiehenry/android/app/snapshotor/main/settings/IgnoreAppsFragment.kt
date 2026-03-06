package tiiehenry.android.app.snapshotor.main.settings

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import tiiehenry.android.app.snapshotor.R
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.app.AppFilterHelper
import tiiehenry.android.app.snapshotor.app.AppFilterType
import tiiehenry.android.app.snapshotor.app.AppInfo
import tiiehenry.android.app.snapshotor.config.IgnoreAppsConfig
import tiiehenry.android.app.snapshotor.databinding.FragmentIgnoreAppsBinding
import tiiehenry.android.app.snapshotor.databinding.ItemIgnoreAppBinding
import tiiehenry.android.app.snapshotor.group.SelectAppFragment

/**
 * 忽略应用管理界面
 * 显示已忽略的应用列表，支持添加和移除
 */
class IgnoreAppsFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentIgnoreAppsBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: IgnoredAppsAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var filteredApps: List<AppInfo> = emptyList()
    private var currentFilterType: AppFilterType = AppFilterType.ALL

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIgnoreAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupFilterSpinner()
        setupSearchView()
        setupFab()

        // 加载已安装应用列表
        loadAllApps()

        // 加载忽略的应用列表
        loadIgnoredApps()
    }

    private fun setupRecyclerView() {
        binding.appsRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = IgnoredAppsAdapter(
            onItemClick = { appInfo ->
                // 点击不处理，或者可以显示应用详情
            },
            onItemLongClick = { appInfo ->
                showRemoveConfirmDialog(appInfo)
                true
            }
        )
        binding.appsRecyclerView.adapter = adapter
    }

    private fun setupFilterSpinner() {
        AppFilterHelper.setupFilterSpinner(binding.spinnerAppFilter, requireContext()) { filterType ->
            currentFilterType = filterType
            filterApps(binding.searchView.query.toString())
        }
    }

    private fun setupSearchView() {
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

    private fun setupFab() {
        binding.fabAdd.setOnClickListener {
            showSelectAppDialog()
        }
    }

    private fun loadAllApps() {
        // 从全局 ViewModel 获取所有应用
        val appsMap = SnapShotApp.getViewModel().appsList.value
        allApps = appsMap?.values?.flatten() ?: emptyList()
    }

    private fun loadIgnoredApps() {
        val ignoredPackageNames = IgnoreAppsConfig.getIgnoredPackageNames()

        if (ignoredPackageNames.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
            adapter.submitList(emptyList())
        } else {
            binding.tvEmpty.visibility = View.GONE
            filterApps(binding.searchView.query.toString())
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun filterApps(query: String) {
        val ignoredSet = IgnoreAppsConfig.getIgnoredPackageNames().toSet()

        // 从所有应用中过滤出已忽略的应用，并按 packageName 去重（只保留第一个）
        val appsToShow = allApps
            .filter { it.packageName in ignoredSet }
            .distinctBy { it.packageName }

        // 应用筛选器和搜索
        filteredApps = AppFilterHelper.filterApps(appsToShow, query, currentFilterType)
        adapter.submitList(filteredApps)

        // 更新空视图
        if (filteredApps.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
        } else {
            binding.tvEmpty.visibility = View.GONE
        }
    }

    private fun showSelectAppDialog() {
        // 显示 SelectAppFragment 作为 BottomSheet，过滤掉已忽略的应用
        val selectAppFragment = SelectAppFragment.newInstanceForIgnoreApps(
            groupId = "ignore_apps"
        ) { selectedApps ->
            // 重新加载应用列表（确保获取最新数据）
            loadAllApps()
            // 添加选中的应用到忽略列表
            for (app in selectedApps) {
                IgnoreAppsConfig.addIgnoredApp(app)
            }
            // 刷新列表
            loadIgnoredApps()
        }

        selectAppFragment.show(parentFragmentManager, selectAppFragment.tag)
    }

    private fun showRemoveConfirmDialog(appInfo: AppInfo) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.remove_ignore_app)
            .setMessage(getString(R.string.remove_ignore_app_confirm, appInfo.label))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                IgnoreAppsConfig.removeIgnoredApp(appInfo.packageName, appInfo.userId)
                loadIgnoredApps()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * 已忽略应用列表的 Adapter
     */
    class IgnoredAppsAdapter(
        private val onItemClick: (AppInfo) -> Unit,
        private val onItemLongClick: (AppInfo) -> Boolean
    ) : ListAdapter<AppInfo, IgnoredAppsAdapter.ViewHolder>(AppDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemIgnoreAppBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
            return ViewHolder(binding, onItemClick, onItemLongClick)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }

        class ViewHolder(
            private val binding: ItemIgnoreAppBinding,
            private val onItemClick: (AppInfo) -> Unit,
            private val onItemLongClick: (AppInfo) -> Boolean
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(appInfo: AppInfo) {
                // 如果未安装，label 会返回 packageName
                binding.appName.text = appInfo.label
                binding.appPackage.text = appInfo.packageName

                // 使用 Glide 加载图标
                Glide.with(binding.root.context)
                    .load(appInfo.icon)
                    .into(binding.appIcon)

                binding.root.setOnClickListener {
                    onItemClick(appInfo)
                }

                binding.root.setOnLongClickListener {
                    onItemLongClick(appInfo)
                }
            }
        }

        private class AppDiffCallback : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem.packageName == newItem.packageName && oldItem.userId == newItem.userId
            }

            override fun areContentsTheSame(oldItem: AppInfo, newItem: AppInfo): Boolean {
                return oldItem == newItem
            }
        }
    }
}
