package tiiehenry.android.app.snapshot.data

import android.util.Log
import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.config.AppConfig
import tiiehenry.android.app.snapshot.config.GroupConfig
import tiiehenry.android.app.snapshot.config.VersionRetentionConfig
import tiiehenry.android.app.snapshot.group.SnapedApp

/**
 * 保留策略执行器
 * 负责根据配置的保留策略自动清理过期存档
 */
object RetentionPolicyExecutor {

    private const val TAG = "RetentionPolicyExecutor"

    /**
     * 保留策略执行结果
     */
    data class RetentionResult(
        val success: Boolean,
        val deletedCount: Int,
        val skipped: Boolean = false,
        val errorMessage: String? = null
    ) {
        companion object {
            fun skipped(): RetentionResult = RetentionResult(true, 0, true)
            fun success(deletedCount: Int): RetentionResult = RetentionResult(true, deletedCount)
            fun error(message: String): RetentionResult = RetentionResult(false, 0, false, message)
        }
    }

    /**
     * 应用保留策略
     * @param item 应用快照项
     * @param groupConfig 组配置（包含默认保留策略）
     * @param appConfig 应用配置（可能包含单独保留策略）
     * @return 执行结果
     */
    suspend fun applyPolicy(
        item: SnapedApp,
        groupConfig: GroupConfig,
        appConfig: AppConfig
    ): RetentionResult {
        return try {
            // 1. 确定使用哪个配置
            val config = resolveRetentionConfig(groupConfig, appConfig)

            // 2. 如果未启用，直接返回
            if (!config.enabled) {
                Log.d(TAG, "保留策略未启用，跳过清理: ${item.appInfo.packageName}")
                return RetentionResult.skipped()
            }

            Log.d(TAG, "开始执行保留策略: ${item.appInfo.packageName}")

            // 3. 获取待删除列表
            val toDelete = analyzeArchivesToDelete(item, config)

            if (toDelete.isEmpty()) {
                Log.d(TAG, "没有需要删除的存档: ${item.appInfo.packageName}")
                return RetentionResult.success(0)
            }

            Log.d(TAG, "准备删除 ${toDelete.size} 个存档: ${item.appInfo.packageName}")

            // 4. 执行删除
            var deletedCount = 0
            for (archive in toDelete) {
                try {
                    if (ArchiveManager.deleteArchive(item, archive)) {
                        deletedCount++
                        Log.d(TAG, "已删除存档: ${archive.name}")
                    } else {
                        Log.w(TAG, "删除存档失败: ${archive.name}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "删除存档异常: ${archive.name}", e)
                }
            }

            // 5. 重新加载存档列表
            ArchiveManager.reloadArchives(item, true)

            Log.d(TAG, "保留策略执行完成，删除 $deletedCount 个存档: ${item.appInfo.packageName}")
            RetentionResult.success(deletedCount)

        } catch (e: Exception) {
            Log.e(TAG, "执行保留策略失败: ${item.appInfo.packageName}", e)
            RetentionResult.error(e.message ?: "未知错误")
        }
    }

    /**
     * 解析最终使用的保留策略配置
     * 优先级：应用单独配置（如果启用）> 组默认配置
     */
    private fun resolveRetentionConfig(
        groupConfig: GroupConfig,
        appConfig: AppConfig
    ): VersionRetentionConfig {
        // 应用单独配置优先（如果启用）
        val appRetention = appConfig.shotConfig.getVersionRetentionConfig()
        if (appRetention.enabled) {
            Log.d(TAG, "使用应用单独保留策略配置")
            return appRetention
        }

        // 否则使用组配置
        Log.d(TAG, "使用组默认保留策略配置")
        return groupConfig.shotConfig.getVersionRetentionConfig()
    }

    /**
     * 分析需要删除的存档列表
     */
    fun analyzeArchivesToDelete(
        item: SnapedApp,
        config: VersionRetentionConfig
    ): List<ArchiveItem> {
        val sortedArchives = ArchiveManager.getSortedArchives(item)
        if (sortedArchives.isEmpty()) {
            return emptyList()
        }

        val toDelete = mutableSetOf<ArchiveItem>()

        // 获取已锁定存档（不参与清理）
        val lockedArchives = sortedArchives.filter { it.metaInfo.isLocked }
        val unlockedArchives = sortedArchives.filter { !it.metaInfo.isLocked }

        Log.d(TAG, "存档总数: ${sortedArchives.size}, 已锁定: ${lockedArchives.size}, 未锁定: ${unlockedArchives.size}")

        // 条件A: 最大保留版本数限制
        if (config.isMaxVersionCountEnabled) {
            val maxCount = config.maxVersionCount!!
            // 锁定存档不计入最大保留数限制
            val effectiveMax = maxCount

            if (unlockedArchives.size > effectiveMax) {
                // 跳过最新的 effectiveMax 个，其余加入删除列表
                val excess = unlockedArchives.drop(effectiveMax)
                val deletable = excess.filter { !it.metaInfo.isLocked }
                toDelete.addAll(deletable)
                Log.d(TAG, "条件A触发: 最大保留 $maxCount 个，将删除 ${deletable.size} 个存档")
            }
        }

        // 条件B+条件C: 同版本保留策略
        // 同版本保留数量 = 最低保留数 + 额外保留数
        val minSameVersionCount = if (config.isMinSameVersionCountEnabled()) {
            config.minSameVersionCount!!
        } else {
            0
        }
        val extraRetentionCount = if (config.isExtraRetentionEnabled()) {
            config.extraRetentionCount ?: 0
        } else {
            0
        }

        // 计算该版本应保留的总数
        val totalRetentionPerVersion = minSameVersionCount + extraRetentionCount

        if (totalRetentionPerVersion > 0) {
            // 按版本号分组（只考虑未锁定且未被条件A标记删除的存档）
            val remainingArchives = unlockedArchives.filter { it !in toDelete }
            val versionGroups = remainingArchives.groupBy {
                it.metaInfo.packageInfo.versionCode
            }

            versionGroups.forEach { (versionCode, archives) ->
                if (archives.size > totalRetentionPerVersion) {
                    // 保留最新的 totalRetentionPerVersion 个，其余加入删除列表
                    // archives 已经是有序的（因为 sortedArchives 是按时间降序）
                    val toRemove = archives.drop(totalRetentionPerVersion)
                    toDelete.addAll(toRemove)
                    Log.d(TAG, "同版本保留触发: 版本 $versionCode 保留 $totalRetentionPerVersion 个（最低 $minSameVersionCount + 额外 $extraRetentionCount），将删除 ${toRemove.size} 个")
                }
            }
        }

        // 额外保留的过期检查（仅当有过期时间时）
        if (config.isExtraRetentionEnabled() && config.hasExtraRetentionExpiry()) {
            val extraCount = config.extraRetentionCount ?: 0
            val expiryDays = config.extraRetentionDays!!
            val expiryTime = System.currentTimeMillis() - (expiryDays * 24 * 60 * 60 * 1000L)

            // 排除已被标记删除的存档
            val remainingArchives = unlockedArchives.filter { it !in toDelete }

            // 按版本分组
            val versionGroups = remainingArchives.groupBy {
                it.metaInfo.packageInfo.versionCode
            }

            versionGroups.forEach { (_, archives) ->
                // 跳过最低保留数，检查额外保留区域是否过期
                val extraArchives = archives.drop(minSameVersionCount)

                extraArchives.forEach { archive ->
                    if (archive.metaInfo.makeTime < expiryTime) {
                        toDelete.add(archive)
                        Log.d(TAG, "额外保留过期检查: 存档 ${archive.name} 已过期 (${formatDays(expiryDays)}前)")
                    }
                }
            }
        }

        return toDelete.toList()
    }

    private fun formatDays(days: Int): String {
        return when {
            days >= 365 -> "${days / 365}年"
            days >= 30 -> "${days / 30}个月"
            days >= 7 -> "${days / 7}周"
            else -> "${days}天"
        }
    }
}
