package tiiehenry.android.app.snapshot.main.launch.batch

data class RestoreRecord(
    val restoredAt: Long,
    val archiveName: String,
    val archiveMakeTime: Long
)

data class AppRestoreKey(
    val groupId: String,
    val packageName: String,
    val userId: Int
) {
    val storageKey: String get() = "$groupId:$packageName:$userId"

    fun recordMapKey(): String = "$packageName:$userId"
}
