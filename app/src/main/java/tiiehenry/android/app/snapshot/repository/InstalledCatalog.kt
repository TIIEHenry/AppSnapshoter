package tiiehenry.android.app.snapshot.repository

internal data class CatalogPackage(
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
)

/**
 * [rows] 只在全部用户都拉成功时非空。
 * 任一用户外层拉包失败时 [failedUserIds] 非空且 [rows] 为 null，不保留部分结果。
 * 用户列表为空时 [noUsers] 为 true。
 */
internal data class InstalledCatalog<U>(
    val rows: Map<U, List<CatalogPackage>>?,
    val noUsers: Boolean,
    val failedUserIds: List<Int>,
)

/**
 * 任一用户的外层拉包失败则整次不返回 map。
 * 单个包读取失败只跳过该包。用户列表为空时 [InstalledCatalog.noUsers] 为 true。
 */
internal fun <U> scanInstalledCatalog(
    users: List<U>,
    userId: (U) -> Int,
    installedPackages: (U) -> List<String>?,
    readPackage: (U, String) -> CatalogPackage,
    onUserFailure: (Int, Exception) -> Unit = { _, _ -> },
    onPackageFailure: (Exception) -> Unit = {},
): InstalledCatalog<U> {
    if (users.isEmpty()) {
        return InstalledCatalog(rows = null, noUsers = true, failedUserIds = emptyList())
    }
    val failedUserIds = mutableListOf<Int>()
    val rows = linkedMapOf<U, List<CatalogPackage>>()
    for (user in users) {
        val id = userId(user)
        try {
            val names = installedPackages(user) ?: emptyList()
            rows[user] = names.mapNotNull { packageName ->
                try {
                    readPackage(user, packageName)
                } catch (e: Exception) {
                    onPackageFailure(e)
                    null
                }
            }
        } catch (e: Exception) {
            failedUserIds += id
            onUserFailure(id, e)
        }
    }
    if (failedUserIds.isNotEmpty()) {
        return InstalledCatalog(rows = null, noUsers = false, failedUserIds = failedUserIds)
    }
    return InstalledCatalog(rows = rows, noUsers = false, failedUserIds = emptyList())
}

/** 只有全部用户都拉成功才返回要提交的 map；否则为 null，调用方不得 post，也不得标已加载。 */
internal fun <U> installedCatalogToPublish(
    scan: InstalledCatalog<U>,
): Map<U, List<CatalogPackage>>? {
    if (scan.noUsers || scan.failedUserIds.isNotEmpty()) return null
    return scan.rows
}
