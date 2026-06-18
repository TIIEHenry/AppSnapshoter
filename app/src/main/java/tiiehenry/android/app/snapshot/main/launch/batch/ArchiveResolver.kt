package tiiehenry.android.app.snapshot.main.launch.batch

import tiiehenry.android.app.snapshot.archive.ArchiveItem
import tiiehenry.android.app.snapshot.main.timeline.RestoreStrategy

object ArchiveResolver {

    fun pick(archives: Collection<ArchiveItem>, strategy: RestoreStrategy): ArchiveItem =
        when (strategy) {
            RestoreStrategy.NEWEST_FIRST -> archives.maxBy { it.metaInfo.makeTime }
            RestoreStrategy.OLDEST_FIRST -> archives.minBy { it.metaInfo.makeTime }
        }

    data class PickResult(
        val archive: ArchiveItem,
        val fallbackToNewest: Boolean = false
    )

    fun pick(
        archives: Collection<ArchiveItem>,
        strategy: ArchivePickStrategy,
        record: RestoreRecord?
    ): PickResult {
        require(archives.isNotEmpty())
        return when (strategy) {
            ArchivePickStrategy.NEWEST -> PickResult(archives.maxBy { it.metaInfo.makeTime })
            ArchivePickStrategy.OLDEST -> PickResult(archives.minBy { it.metaInfo.makeTime })
            ArchivePickStrategy.LAST_RESTORED -> {
                if (record == null) {
                    PickResult(archives.maxBy { it.metaInfo.makeTime }, fallbackToNewest = true)
                } else {
                    val matched = archives.find { it.name == record.archiveName }
                    if (matched != null) {
                        PickResult(matched)
                    } else {
                        PickResult(archives.maxBy { it.metaInfo.makeTime }, fallbackToNewest = true)
                    }
                }
            }
        }
    }
}
