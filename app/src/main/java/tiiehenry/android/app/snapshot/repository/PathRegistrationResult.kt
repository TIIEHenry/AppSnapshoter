package tiiehenry.android.app.snapshot.repository

/**
 * 分组 / 分组集路径登记结果（path 全局唯一）。
 */
sealed class PathRegistrationResult {
    /** @param discoveredCount 仅 [addGroupSet] 有意义：新扫到的子分组数 */
    data class Ok(val discoveredCount: Int = 0) : PathRegistrationResult()

    data object OccupiedByGroup : PathRegistrationResult()
    data object OccupiedBySet : PathRegistrationResult()
    data class Error(val message: String) : PathRegistrationResult()
}
