package tiiehenry.android.app.snapshot.main.launch.batch

/** 批量恢复范围 */
enum class GroupRestoreScope {
    /** 未安装且有快照 */
    NOT_INSTALLED,
    /** 全部有快照的应用 */
    ALL,
    /** 自上次恢复以来需要再次恢复的应用 */
    SINCE_LAST_RESTORE
}
