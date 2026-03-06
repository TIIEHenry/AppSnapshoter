package tiiehenry.android.app.snapshotor.app

import android.content.Context
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import tiiehenry.android.app.snapshotor.R

/**
 * 应用筛选类型
 */
enum class AppFilterType {
    ALL,        // 全部应用
    SYSTEM_ONLY, // 仅系统应用
    USER_ONLY   // 仅用户应用
}

/**
 * 应用过滤辅助类
 * 统一处理应用列表的筛选和搜索逻辑
 */
object AppFilterHelper {

    /**
     * 设置筛选 Spinner
     * @param spinner Spinner 控件
     * @param context Context
     * @param onFilterTypeSelected 筛选类型选择回调
     */
    fun setupFilterSpinner(
        spinner: Spinner,
        context: Context,
        onFilterTypeSelected: (AppFilterType) -> Unit
    ) {
        val filterOptions = arrayOf(
            context.getString(R.string.app_filter_all),
            context.getString(R.string.app_filter_system_only),
            context.getString(R.string.app_filter_user_only)
        )

        val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, filterOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val filterType = when (position) {
                    0 -> AppFilterType.ALL
                    1 -> AppFilterType.SYSTEM_ONLY
                    2 -> AppFilterType.USER_ONLY
                    else -> AppFilterType.ALL
                }
                onFilterTypeSelected(filterType)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                // Do nothing
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
        filterType: AppFilterType
    ): List<AppInfo> {
        // 根据筛选类型过滤
        var filtered = when (filterType) {
            AppFilterType.ALL -> apps
            AppFilterType.SYSTEM_ONLY -> apps.filter { it.isSystemApp }
            AppFilterType.USER_ONLY -> apps.filter { !it.isSystemApp}
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
        filterType: AppFilterType
    ): List<AppInfo> {
        val appsForUser = apps.filter { it.userId == userId }
        return filterApps(appsForUser, query, filterType)
    }
}
