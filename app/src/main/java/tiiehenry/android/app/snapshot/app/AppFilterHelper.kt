package tiiehenry.android.app.snapshot.app

import android.content.Context
import android.view.ContextThemeWrapper
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.R as MaterialR
import tiiehenry.android.app.snapshot.R

/**
 * 应用过滤辅助类
 * 统一处理应用列表的筛选和搜索逻辑
 */
object AppFilterHelper {

    /**
     * 设置筛选 ChipGroup
     * @param chipGroup ChipGroup 控件
     * @param context Context
     * @param onFilterTypeSelected 筛选类型选择回调
     */
    fun setupFilterChips(
        chipGroup: ChipGroup,
        context: Context,
        onFilterTypeSelected: (Set<AppFilterType>) -> Unit
    ) {
        val filterOptions = listOf(
            context.getString(R.string.app_filter_system) to AppFilterType.SYSTEM,
            context.getString(R.string.app_filter_user) to AppFilterType.USER
        )

        chipGroup.removeAllViews()
        chipGroup.isSingleSelection = false

        val chips = mutableListOf<Chip>()
        filterOptions.forEach { (label, _) ->
            val contextWithStyle = ContextThemeWrapper(context, MaterialR.style.Widget_Material3_Chip_Filter)
            val chip = Chip(contextWithStyle).apply {
                text = label
                isCheckable = true
                isCheckedIconVisible = true
                isChecked = true // 默认全部选中
                id = android.view.View.generateViewId()
            }
            chipGroup.addView(chip)
            chips.add(chip)
        }

        // 防止取消最后一个选中，并通知回调
        var updating = false
        chips.forEach { chip ->
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (updating) return@setOnCheckedChangeListener
                if (!isChecked && chips.none { it.isChecked }) {
                    // 阻止取消最后一个选中项
                    updating = true
                    chip.isChecked = true
                    updating = false
                    return@setOnCheckedChangeListener
                }
                val selected = chips.mapIndexedNotNull { i, c ->
                    if (c.isChecked) filterOptions[i].second else null
                }.toSet()
                onFilterTypeSelected(selected)
            }
        }
    }

    /**
     * 过滤应用列表
     * @param apps 原始应用列表
     * @param query 搜索关键词
     * @param filterType 筛选类型
     * @return 过滤后的应用列表（按名称排序）
     */
    fun filterApps(
        apps: List<AppInfo>,
        query: String,
        filterTypes: Set<AppFilterType>
    ): List<AppInfo> {
        // 根据筛选类型过滤（多选：包含对应类型）
        var filtered = apps.filter { app ->
            (AppFilterType.SYSTEM in filterTypes && app.isSystemApp) ||
            (AppFilterType.USER in filterTypes && !app.isSystemApp)
        }

        // 根据搜索关键词过滤
        if (query.isNotEmpty()) {
            filtered = filtered.filter {
                it.label.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }

        // 按应用名称排序
        return filtered.sortedBy { it.label.lowercase() }
    }

    /**
     * 按用户ID过滤应用列表
     * @param apps 原始应用列表
     * @param userId 用户ID
     * @param query 搜索关键词
     * @param filterType 筛选类型
     * @return 过滤后的应用列表
     */
    fun filterAppsByUser(
        apps: List<AppInfo>,
        userId: Int,
        query: String,
        filterTypes: Set<AppFilterType>
    ): List<AppInfo> {
        val appsForUser = apps.filter { it.userId == userId }
        return filterApps(appsForUser, query, filterTypes)
    }
}
