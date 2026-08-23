package tiiehenry.android.app.snapshot.main.apps

/**
 * 应用 catalog 的 UI 契约。
 *
 * `isAppsLoading=false` + 空 `appsList` 不能表示「尚未拉取」：那是 LiveData 初值，
 * 与「已加载且设备无应用」无法区分。未成功拉取前必须显示 loading。
 */
object AppsCatalogUi {

    fun shouldShowLoading(
        catalogLoaded: Boolean,
        isAppsLoading: Boolean,
        isLocalProcessing: Boolean,
    ): Boolean = !catalogLoaded || isAppsLoading || isLocalProcessing

    /** 无用户 key 的空 Map 是 LiveData 初值/失败态，不是「设备没有应用」。 */
    fun shouldBindCatalog(apps: Map<*, *>): Boolean = apps.isNotEmpty()

    fun isSuccessfulCatalog(userCount: Int, loadSucceeded: Boolean): Boolean =
        loadSucceeded && userCount > 0

    const val MAX_VISIBLE_ATTEMPTS = 5

    fun shouldRequestCatalog(
        catalogLoaded: Boolean,
        isAppsLoading: Boolean,
        attemptsUsed: Int,
        maxAttempts: Int = MAX_VISIBLE_ATTEMPTS,
    ): Boolean = !catalogLoaded && !isAppsLoading && attemptsUsed < maxAttempts

    fun retryDelayMs(completedAttempts: Int): Long = when {
        completedAttempts <= 0 -> 200L
        completedAttempts == 1 -> 400L
        completedAttempts == 2 -> 800L
        completedAttempts == 3 -> 1_600L
        else -> 3_200L
    }
}
