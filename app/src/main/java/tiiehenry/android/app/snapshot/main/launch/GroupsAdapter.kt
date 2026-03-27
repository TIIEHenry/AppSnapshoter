package tiiehenry.android.app.snapshot.main.launch

import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.config.AppConfigManager
import tiiehenry.android.app.snapshot.config.SortConfig
import tiiehenry.android.app.snapshot.archieve.MetaInfoHelper
import tiiehenry.android.app.snapshot.databinding.ItemGroupBinding
import tiiehenry.android.app.snapshot.databinding.ItemSuccessAppBinding
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.main.selectapp.SelectAppFragment
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.main.launch.makearchive.SnapshotCreator
import tiiehenry.android.app.snapshot.main.launch.makearchive.SuccessSnapshotInfo
import tiiehenry.android.app.snapshot.main.launch.makearchive.progress.GroupItemsProgressDialog
import tiiehenry.android.app.snapshot.main.launch.group.GroupSettingFragment
import tiiehenry.android.app.snapshot.main.launch.group.GroupConfigFragment
import tiiehenry.android.app.snapshot.utils.AppStatusHelper
import java.util.concurrent.atomic.AtomicBoolean

class GroupsAdapter(
    private val viewModel: LauncherViewModel,
    private val fragmentManager: FragmentManager
) : ListAdapter<SnapGroup, GroupsAdapter.GroupViewHolder>(GroupDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding, viewModel, fragmentManager)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        holder.bind(this, getItem(position))
    }

    class GroupViewHolder(
        val binding: ItemGroupBinding,
        private val viewModel: LauncherViewModel,
        val fragmentManager: FragmentManager
    ) : RecyclerView.ViewHolder(binding.root) {

        var isSortMode = false
        private var itemTouchHelper: ItemTouchHelper? = null

        fun addNewApp(group: SnapGroup) {
            // 显示SelectAppFragment选择应用
            SelectAppFragment.newInstance(group.id) { appInfos ->
                SnapshotApp.getViewModel().addAppsToGroup(group.id, appInfos) {
                    // 从ViewModel获取最新的group对象，确保使用的是已更新的实例
                    val updatedGroup =
                        SnapshotApp.getViewModel().groupList.value?.find { it.id == group.id }
                    if (updatedGroup != null) {
                        refresh(updatedGroup, binding.groupRecyclerView)
                    } else {
                        refresh(group, binding.groupRecyclerView)
                    }
                }
            }.show(fragmentManager, "SelectAppFragment")
        }

        fun bind(groupsAdapter: GroupsAdapter, group: SnapGroup) {
            binding.groupTitle.text = group.name

            // 初始化折叠状态
            updateCollapseState(group.isCollapsed)

            binding.groupTitle.setOnClickListener {
                // 点击标题切换折叠状态
                group.isCollapsed = !group.isCollapsed
                updateCollapseState(group.isCollapsed)
            }
            binding.groupTitle.setOnLongClickListener {
                // 显示GroupConfigFragment，保存后刷新列表
                GroupSettingFragment.newInstance(group) {
                    refresh(group, binding.groupRecyclerView)
                }.show(fragmentManager, "GroupConfigFragment")
                true
            }

            binding.expandGroup.setOnClickListener {
                // 点击展开区域展开分组
                group.isCollapsed = false
                updateCollapseState(group.isCollapsed)
            }

            // 设置GridLayout，4列
            binding.groupRecyclerView.layoutManager = GridLayoutManager(binding.root.context, 4)

            val adapter = GroupItemAdapter(this, groupsAdapter, viewModel, group) { adapter, item ->
                // 当存档被删除后，更新列表
                val currentList = ArrayList(group.apps)
                val index =
                    currentList.indexOfFirst { it.appInfo.packageName == item.appInfo.packageName }
                if (index != -1) {
                    currentList[index] = item
                    adapter.submitList(currentList)
                }
            }
            binding.groupRecyclerView.adapter = adapter
            binding.btnRefresh.setOnClickListener {
                viewModel.viewModelScope.launch {
                    group.loadApps(
                        SnapshotApp.getContext(),
                        SnapshotApp.getInstance().fileSystem,
                        SnapshotApp.getInstance().appManager,
                        reload = true
                    )
                    withContext(Dispatchers.Main) {
                        // 控制加载状态
                        refresh(group, binding.groupRecyclerView)
                    }
                }
            }
            binding.btnRefresh.setOnLongClickListener {
                Toast.makeText(it.context, "刷新分组中的应用列表", Toast.LENGTH_SHORT).show()
                true
            }
            binding.btnRefresh.setOnLongClickListener {
                showGroupStatistics(group)
                true
            }
            refresh(group, binding.groupRecyclerView)

            binding.btnAdd.setOnClickListener {
                addNewApp(group)
            }
            binding.btnAdd.setOnLongClickListener {
                Toast.makeText(it.context, "添加新应用到分组", Toast.LENGTH_SHORT).show()
                true
            }

            binding.emptyLayout.setOnClickListener {
                addNewApp(group)
            }

            // 点击 btnMove 显示排序类型 PopupMenu，长按进入自定义拖拽排序模式
            binding.btnMove.setOnClickListener { v ->
                showSortTypePopupMenu(v, group, adapter)
            }
            val sortType = group.config.sortConfig.sortType
            if (sortType == SortConfig.SORT_TYPE_CUSTOM) { // 自定义排序模式下长按进入拖拽排序
                binding.btnMove.setOnLongClickListener {
                    toggleSortMode(group, adapter)
                    true
                }
            }
            binding.btnConfirm.setOnClickListener {
                toggleSortMode(group, adapter)
            }

            binding.btnTune.setOnClickListener {
                // 显示 GroupConfigFragment，保存后刷新列表
                GroupConfigFragment.newInstance(group) {
                    refresh(group, binding.groupRecyclerView)
                }.show(fragmentManager, "GroupShotConfigFragment")
            }
            binding.btnTune.setOnLongClickListener {
                Toast.makeText(it.context, "设置分组配置", Toast.LENGTH_SHORT).show()
                true
            }

            binding.btnArchiveAll.setOnClickListener {
                archiveAllApps(group, adapter)
            }
            binding.btnArchiveAll.setOnLongClickListener {
                Toast.makeText(it.context, "一键存档", Toast.LENGTH_SHORT).show()
                true
            }

            updateButtonVisibility(!isSortMode)
        }

        private fun showSortTypePopupMenu(
            anchor: View,
            group: SnapGroup,
            adapter: GroupItemAdapter
        ) {
            val popup = PopupMenu(anchor.context, anchor)
            val menu = popup.menu
            val sortTypes = listOf(
                SortConfig.SORT_TYPE_DEFAULT to "默认排序",
                SortConfig.SORT_TYPE_NAME_ASC to "按名称升序",
                SortConfig.SORT_TYPE_NAME_DESC to "按名称降序",
                SortConfig.SORT_TYPE_INSTALL_TIME_ASC to "按安装时间升序",
                SortConfig.SORT_TYPE_INSTALL_TIME_DESC to "按安装时间降序",
                SortConfig.SORT_TYPE_CUSTOM to "自定义排序"
            )
            val currentSortType = group.config.sortConfig.sortType
            sortTypes.forEachIndexed { index, (type, label) ->
                val item = menu.add(0, type, index, label)
                item.isCheckable = true
                item.isChecked = (type == currentSortType)
            }
            popup.setOnMenuItemClickListener { menuItem ->
                val newSortType = menuItem.itemId
                group.config.sortConfig.sortType = newSortType
                group.config.save()
                // 更新长按监听：只有自定义排序时才支持长按拖拽
                if (newSortType == SortConfig.SORT_TYPE_CUSTOM) {
                    binding.btnMove.setOnLongClickListener {
                        toggleSortMode(group, adapter)
                        true
                    }
                } else {
                    binding.btnMove.setOnLongClickListener(null)
                    // 如果当前在排序模式下，退出
                    if (isSortMode) {
                        toggleSortMode(group, adapter)
                    }
                }
                refresh(group, binding.groupRecyclerView)
                true
            }
            popup.show()
        }

        private fun toggleSortMode(group: SnapGroup, adapter: GroupItemAdapter) {
            isSortMode = !isSortMode
            if (isSortMode) {
                // 进入排序模式：显示确认按钮，隐藏其他按钮，不修改 btnMove
                startDragSortMode(adapter, group)
                binding.groupTitle.text = "${group.name} (排序模式)"
                updateButtonVisibility(false)
            } else {
                // 退出排序模式：隐藏确认按钮，恢复其他按钮
                binding.groupTitle.text = group.name
                stopDragSortMode(adapter)
                updateButtonVisibility(true)
            }
        }

        private fun updateButtonVisibility(show: Boolean) {
            binding.btnMove.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnAdd.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnTune.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnRefresh.visibility = if (show) View.VISIBLE else View.GONE
            binding.btnArchiveAll.visibility = if (show) View.VISIBLE else View.GONE
            // 排序模式下显示确认按钮，普通模式下隐藏
            binding.btnConfirm.visibility = if (show) View.GONE else View.VISIBLE
        }

        /**
         * 批量归档组内所有已安装应用
         */
        private fun archiveAllApps(group: SnapGroup, adapter: GroupItemAdapter) {
            val installedApps = group.apps.filter { AppStatusHelper.isAppInstalled(it) }.filter {
                val appConfig = AppConfigManager.getInstance().getConfig(it.appInfo.packageName)
                val groupConfig = group.config
                val actionConfig = if (appConfig.actionConfig.enabled) {
                    appConfig.actionConfig
                } else {
                    groupConfig.actionConfig
                }
                actionConfig.isAutoSnapshot
            }
            if (installedApps.isEmpty()) {
                Toast.makeText(binding.root.context, "没有已安装的应用可归档", Toast.LENGTH_SHORT)
                    .show()
                return
            }

            // 显示确认对话框
            MaterialAlertDialogBuilder(binding.root.context)
                .setTitle("全部归档")
                .setMessage("确定为 ${group.name} 中的 ${installedApps.size}/${group.apps.size} 个应用创建快照？")
                .setPositiveButton("确认") { _, _ ->
                    val loadingDialog = GroupItemsProgressDialog(binding.root.context)
                    loadingDialog.setTotalProgress(installedApps.size)
                    val erroredList = mutableMapOf<ArchivedApp, Exception>()
                    val succeedList = mutableListOf<SuccessSnapshotInfo>()
                    val isCancelled = AtomicBoolean(false)
                    val isForceCancelled = AtomicBoolean(false)
                    val startTime = System.currentTimeMillis()
                    loadingDialog.setOnCancelListener {
                        isCancelled.set(true)
                        loadingDialog.setFinishButtonAsForceCancel {
                            isForceCancelled.set(true)
                        }
                        loadingDialog.setLabel("正在停止")
                    }
                    loadingDialog.setOnFailListener {
                        if (erroredList.isNotEmpty()) {
                            showErroredAppsDialog(erroredList)
                        } else {
                            Toast.makeText(binding.root.context, "暂无错误", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    loadingDialog.setOnSuccessListener {
                        if (succeedList.isNotEmpty()) {
                            showSuccessAppsDialog(succeedList)
                        } else {
                            Toast.makeText(binding.root.context, "暂无成功", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    loadingDialog.setOnSuccessLongClickListener {
                        if (succeedList.isNotEmpty()) {
                            showSuccessStatistics(succeedList)
                        } else {
                            Toast.makeText(binding.root.context, "暂无成功", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    createSnapshotsSequentially(
                        loadingDialog,
                        installedApps,
                        group,
                        adapter,
                        erroredList,
                        succeedList,
                        isCancelled,
                        isForceCancelled,
                        0,
                        startTime
                    )
                    loadingDialog.show()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        /**
         * 顺序创建快照
         */
        private fun createSnapshotsSequentially(
            loadingDialog: GroupItemsProgressDialog,
            apps: List<ArchivedApp>,
            group: SnapGroup,
            adapter: GroupItemAdapter,
            erroredList: MutableMap<ArchivedApp, Exception>,
            succeedList: MutableList<SuccessSnapshotInfo>,
            isCancelled: AtomicBoolean,
            isForceCancelled: AtomicBoolean,
            currentIndex: Int,
            totalStartTime: Long
        ) {
            if (isCancelled.get()) {
                return
            }
            if (currentIndex >= apps.size) {
                // 所有快照创建完成
                refresh(group, binding.groupRecyclerView)
                SnapshotApp.getViewModel().loadGroups()
                Toast.makeText(binding.root.context, "全部归档完成", Toast.LENGTH_SHORT).show()
                val totalTime = System.currentTimeMillis() - totalStartTime
                updateDialogFinishState(
                    loadingDialog,
                    totalTime,
                    succeedList.size,
                    erroredList.size,
                    false
                )
                return
            }
            val item = apps[currentIndex]
            loadingDialog.setProgress(currentIndex + 1)
            loadingDialog.setLabel(item.appInfo.label)
            loadingDialog.setPackageName(item.appInfo.packageName)
            val startTime = System.currentTimeMillis()
            val snapshotCreator = SnapshotCreator(binding.root.context, viewModel.viewModelScope)
            snapshotCreator.createSnapshot(
                loadingDialog,
                item,
                group,
                isForceCancelled,
                object : SnapshotCreator.Callback {
                    override fun onSuccess() {
                        val endTime = System.currentTimeMillis()
                        val timeMillis = endTime - startTime
                        val archiveSize = calculateArchiveSize(item)
                        synchronized(succeedList) {
                            succeedList.add(SuccessSnapshotInfo(item, timeMillis, archiveSize))
                        }
                    }

                    override fun onError(e: Exception) {
                        erroredList[item] = e
                    }

                    override fun onFinish() {
                        if (isCancelled.get() && currentIndex < apps.size) {
                            val totalTime = System.currentTimeMillis() - startTime
                            updateDialogFinishState(
                                loadingDialog,
                                totalTime,
                                succeedList.size,
                                erroredList.size,
                                true
                            )
                            Toast.makeText(binding.root.context, "已中止归档", Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            createSnapshotsSequentially(
                                loadingDialog,
                                apps,
                                group,
                                adapter,
                                erroredList,
                                succeedList,
                                isCancelled,
                                isForceCancelled,
                                currentIndex + 1,
                                totalStartTime
                            )
                        }
                    }
                })
        }

        private fun calculateArchiveSize(item: ArchivedApp): Long {
            return try {
                item.latestArchive?.let { archive ->
                    archive.dataItems.sumOf { it.targetSize } +
                            archive.extraItems.keys.sumOf { it.targetSize }
                } ?: 0L
            } catch (e: Exception) {
                0L
            }
        }

        private fun showErroredAppsDialog(erroredList: Map<ArchivedApp, Exception>) {
            val context = binding.root.context
            val items = erroredList.entries.toList()

            val adapter = object : android.widget.BaseAdapter() {
                override fun getCount(): Int = items.size

                override fun getItem(position: Int): Any = items[position]

                override fun getItemId(position: Int): Long = position.toLong()

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val itemBinding = if (convertView != null) {
                        tiiehenry.android.app.snapshot.databinding.ItemErrorAppBinding.bind(
                            convertView
                        )
                    } else {
                        tiiehenry.android.app.snapshot.databinding.ItemErrorAppBinding.inflate(
                            LayoutInflater.from(context), parent, false
                        )
                    }
                    val entry = items[position]
                    val snapedApp = entry.key
                    val exception = entry.value

                    itemBinding.appIcon.setImageBitmap(snapedApp.appInfo.icon)
                    itemBinding.appLabel.text = snapedApp.appInfo.label
                    itemBinding.packageName.text = snapedApp.appInfo.packageName

                    itemBinding.errorIcon.setOnClickListener {
                        MaterialAlertDialogBuilder(context)
                            .setTitle("Error: ${snapedApp.appInfo.label}")
                            .setMessage(exception.toString())
                            .setPositiveButton("OK", null)
                            .show()
                    }

                    return itemBinding.root
                }
            }

            MaterialAlertDialogBuilder(context)
                .setTitle("创建快照失败 (${items.size}个)")
                .setAdapter(adapter) { _, _ -> }
                .setPositiveButton("OK", null)
                .show()
        }

        private fun updateDialogFinishState(
            loadingDialog: GroupItemsProgressDialog,
            totalTime: Long,
            succeedCount: Int,
            errorCount: Int,
            isCancelled: Boolean
        ) {
            val timeSeconds = totalTime / 1000
            val timeStr = if (timeSeconds < 60) {
                "${timeSeconds}秒"
            } else {
                "${timeSeconds / 60}分${timeSeconds % 60}秒"
            }
            val statusText = if (isCancelled) {
                "已中止"
            } else {
                "已完成"
            }
            loadingDialog.setLabel(statusText)
            loadingDialog.setCurrentItem("总耗时: $timeStr")
            loadingDialog.setItemMessage("成功: $succeedCount")
            loadingDialog.setItemStatus("失败: $errorCount")
            loadingDialog.setPackageName("")
            loadingDialog.setFinishButtonAsClose {
                loadingDialog.dismiss()
            }
        }

        private fun showSuccessAppsDialog(successedList: List<SuccessSnapshotInfo>) {
            val context = binding.root.context
            val items = successedList.toList()

            val adapter = object : android.widget.BaseAdapter() {
                override fun getCount(): Int = items.size

                override fun getItem(position: Int): Any = items[position]

                override fun getItemId(position: Int): Long = position.toLong()

                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val itemBinding = ItemSuccessAppBinding.inflate(
                        LayoutInflater.from(context), parent, false
                    )
                    val info = items[position]
                    val snapedApp = info.archivedApp

                    itemBinding.appIcon.setImageBitmap(snapedApp.appInfo.icon)
                    itemBinding.appLabel.text = snapedApp.appInfo.label
                    itemBinding.packageName.text = snapedApp.appInfo.packageName

                    val timeSeconds = info.timeMillis / 1000
                    val timeStr = if (timeSeconds < 60) {
                        "${timeSeconds}秒"
                    } else {
                        "${timeSeconds / 60}分${timeSeconds % 60}秒"
                    }
                    val sizeStr = Formatter.formatFileSize(context, info.archiveSize)
                    itemBinding.successInfo.text = "耗时: $timeStr, 数据: $sizeStr"

                    return itemBinding.root
                }
            }

            MaterialAlertDialogBuilder(context)
                .setTitle("创建快照成功 (${items.size}个)")
                .setAdapter(adapter) { _, _ -> }
                .setPositiveButton("OK", null)
                .show()
        }

        private fun showSuccessStatistics(successedList: List<SuccessSnapshotInfo>) {
            val context = binding.root.context
            val totalCount = successedList.size
            val totalTimeMillis = successedList.sumOf { it.timeMillis }
            val totalSize = successedList.sumOf { it.archiveSize }
            val avgTimeMillis = if (totalCount > 0) totalTimeMillis / totalCount else 0L
            val avgSize = if (totalCount > 0) totalSize / totalCount else 0L

            val totalTimeSeconds = totalTimeMillis / 1000
            val totalTimeStr = if (totalTimeSeconds < 60) {
                "${totalTimeSeconds}秒"
            } else {
                "${totalTimeSeconds / 60}分${totalTimeSeconds % 60}秒"
            }

            val avgTimeSeconds = avgTimeMillis / 1000
            val avgTimeStr = if (avgTimeSeconds < 60) {
                "${avgTimeSeconds}秒"
            } else {
                "${avgTimeSeconds / 60}分${avgTimeSeconds % 60}秒"
            }

            val message = buildString {
                appendLine("成功项数: $totalCount")
                appendLine("总耗时: $totalTimeStr")
                appendLine("平均耗时: $avgTimeStr")
                appendLine("总数据大小: ${Formatter.formatFileSize(context, totalSize)}")
                appendLine("平均数据大小: ${Formatter.formatFileSize(context, avgSize)}")
            }

            MaterialAlertDialogBuilder(context)
                .setTitle("成功项统计数据")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }

        private fun showGroupStatistics(group: SnapGroup) {
            val context = binding.root.context
            val totalApps = group.apps.size
            val installedApps = group.apps.count { AppStatusHelper.isAppInstalled(it) }
            val archivedApps = group.apps.count { it.archives.isNotEmpty() }
            val totalArchives = group.apps.sumOf { it.archives.size }
            val totalSize = group.apps.flatMap { it.archives.values }.sumOf { archive ->
                try {
                    MetaInfoHelper.getTotalSize(archive.metaInfo, archive.path)
                } catch (e: Exception) {
                    0L
                }
            }
            val avgArchives = if (archivedApps > 0) totalArchives.toDouble() / archivedApps else 0.0

            val message = buildString {
                appendLine("总应用数: $totalApps")
                appendLine("已安装应用: $installedApps")
                appendLine("已存档应用: $archivedApps")
                appendLine("总存档数: $totalArchives")
                appendLine("平均存档数: ${String.format("%.1f", avgArchives)}")
                appendLine("总存档大小: ${Formatter.formatFileSize(context, totalSize)}")
            }

            MaterialAlertDialogBuilder(context)
                .setTitle("${group.name} - 统计数据")
                .setMessage(message)
                .setPositiveButton("OK", null)
                .show()
        }

        private fun startDragSortMode(adapter: GroupItemAdapter, group: SnapGroup) {
            val callback = object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
                0
            ) {
                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val fromPosition = viewHolder.adapterPosition
                    val toPosition = target.adapterPosition

                    // 交换数据
                    val currentList = adapter.currentList.toMutableList()
                    val item = currentList[fromPosition]
                    currentList.removeAt(fromPosition)
                    currentList.add(toPosition, item)

                    // 更新适配器
                    adapter.submitList(currentList)

                    // 立即保存排序后的顺序（submitList是异步的，currentList可能还未更新）
                    saveSortOrderToConfig(currentList, group)

                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    // 不处理滑动删除
                }

                override fun isLongPressDragEnabled(): Boolean {
                    return false // 禁用长按拖拽，我们通过触摸事件控制
                }

                override fun isItemViewSwipeEnabled(): Boolean {
                    return false // 禁用滑动
                }

                override fun onMoved(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                    fromPos: Int,
                    target: RecyclerView.ViewHolder,
                    toPos: Int,
                    x: Int,
                    y: Int
                ) {
                    super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y)
                    // 排序已在 onMove 中保存，这里可以添加其他拖拽完成的逻辑
                }
            }

            itemTouchHelper = ItemTouchHelper(callback)
            itemTouchHelper?.attachToRecyclerView(binding.groupRecyclerView)

            // 将 ItemTouchHelper 设置给 adapter，以便在 onBindViewHolder 中使用
            adapter.itemTouchHelper = itemTouchHelper
            // 刷新列表以应用拖拽触摸监听
            adapter.notifyDataSetChanged()
        }

        private fun stopDragSortMode(adapter: GroupItemAdapter) {
            itemTouchHelper?.attachToRecyclerView(null)
            itemTouchHelper = null
            // 清除 adapter 的 ItemTouchHelper 引用
            adapter.itemTouchHelper = null
            // 刷新列表以清除拖拽触摸监听
            adapter.notifyDataSetChanged()
        }

        private fun saveSortOrderToConfig(sortedList: List<ArchivedApp>, group: SnapGroup) {
            // 获取排序后的包名列表
            val sortedPackageNames = sortedList.map { it.appInfo.packageName }

            // 保存到SortConfig
            val sortConfig = group.config.sortConfig
            sortConfig.sortOrder = sortedPackageNames.toMutableList()

            // 也可以设置排序类型为自定义
            sortConfig.sortType = SortConfig.SORT_TYPE_CUSTOM

            // 保存所有配置到文件
            group.config.save()
        }

        fun refresh(
            group: SnapGroup,
            recyclerView: RecyclerView
        ) {
            // 更新分组标题
            binding.groupTitle.text = group.name

            if (group.apps.isEmpty()) {
                binding.progressBar.visibility = View.GONE
                binding.groupRecyclerView.visibility = View.GONE
                binding.emptyLayout.visibility = View.VISIBLE
                binding.btnAdd.visibility = View.GONE
            } else {
                binding.progressBar.visibility = View.GONE
                binding.groupRecyclerView.visibility = View.VISIBLE
                binding.emptyLayout.visibility = View.GONE
                binding.btnAdd.visibility = View.VISIBLE
            }
            val sortedApps = synchronized(group.apps) {
                // 应用排序
                applySorting(group.apps, group.config.sortConfig, group)
            }
            Log.i("GroupsAdapter", "refresh " + sortedApps)
            val adapter = recyclerView.adapter as GroupItemAdapter
            adapter.submitList(sortedApps)
            adapter.notifyDataSetChanged()
            recyclerView.invalidate()
            recyclerView.requestLayout()

            // 更新折叠状态（保留当前折叠状态）
            updateCollapseState(group.isCollapsed)
        }

        private fun updateCollapseState(isCollapsed: Boolean) {
            if (isCollapsed) {
                // 折叠状态下：隐藏app_layout，显示expand_group
                binding.appLayout.visibility = View.GONE
                binding.expandGroup.visibility = View.VISIBLE
            } else {
                // 展开状态下：显示app_layout，隐藏expand_group
                binding.appLayout.visibility = View.VISIBLE
                binding.expandGroup.visibility = View.GONE
            }
        }

        private fun applySorting(
            apps: List<ArchivedApp>,
            sortConfig: SortConfig,
            group: SnapGroup
        ): List<ArchivedApp> {
            return when (sortConfig.sortType) {
                SortConfig.SORT_TYPE_CUSTOM -> { // 自定义排序
                    val sortOrder = sortConfig.sortOrder.toMutableList()
                    val appPackageNames = apps.map { it.appInfo.packageName }

                    // 1. 移除 sortOrder 中已不存在的应用（被删除的）
                    sortOrder.removeAll { it !in appPackageNames }

                    // 2. 将新添加的应用（不在 sortOrder 中的）添加到末尾
                    val newApps = appPackageNames.filter { it !in sortOrder }
                    sortOrder.addAll(newApps)

                    // 3. 如果 sortOrder 有变化，保存更新后的配置
                    if (sortOrder != sortConfig.sortOrder) {
                        sortConfig.sortOrder = sortOrder
                        group.config.save()
                    }

                    // 4. 按 sortOrder 排序应用
                    apps.sortedBy { sortOrder.indexOf(it.appInfo.packageName) }
                }

                SortConfig.SORT_TYPE_NAME_DESC -> { // 按名称排序（降序）
                    apps.sortedByDescending { it.appInfo.label }
                }

                SortConfig.SORT_TYPE_NAME_ASC -> { // 按名称排序（升序）
                    apps.sortedBy { it.appInfo.label }
                }

                SortConfig.SORT_TYPE_INSTALL_TIME_ASC -> { // 按安装时间排序（升序）
                    apps.sortedBy { it.appInfo.packageInfo?.firstInstallTime ?: 0L }
                }

                SortConfig.SORT_TYPE_INSTALL_TIME_DESC -> { // 按安装时间排序（降序）
                    apps.sortedByDescending { it.appInfo.packageInfo?.firstInstallTime ?: 0L }
                }

                else -> { // 默认排序
                    apps.toList()
                }
            }
        }
    }

    private class GroupDiffCallback : DiffUtil.ItemCallback<SnapGroup>() {
        override fun areItemsTheSame(oldItem: SnapGroup, newItem: SnapGroup): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SnapGroup, newItem: SnapGroup): Boolean {
            return oldItem == newItem
        }
    }
}