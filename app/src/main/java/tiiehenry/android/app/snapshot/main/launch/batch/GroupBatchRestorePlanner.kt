package tiiehenry.android.app.snapshot.main.launch.batch

import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.SnapGroup

data class GroupRestoreTask(
    val app: ArchivedApp,
    val archive: tiiehenry.android.app.snapshot.archive.ArchiveItem,
    val fallbackToNewest: Boolean = false
)

object GroupBatchRestorePlanner {

    data class Preview(
        val tasks: List<GroupRestoreTask>,
        val skippedNoArchive: Int,
        val fallbackCount: Int
    )

    data class ScopeCounts(
        val notInstalled: Int,
        val all: Int,
        val sinceLastRestore: Int
    )

    fun countByScope(
        group: SnapGroup,
        records: Map<String, RestoreRecord>,
        isInstalled: (ArchivedApp) -> Boolean
    ): ScopeCounts {
        var notInstalled = 0
        var all = 0
        var sinceLastRestore = 0
        for (app in group.apps) {
            if (app.archives.isEmpty()) continue
            val record = recordFor(app, records)
            val installed = isInstalled(app)
            if (matchesScope(app, GroupRestoreScope.NOT_INSTALLED, record, installed)) notInstalled++
            if (matchesScope(app, GroupRestoreScope.ALL, record, installed)) all++
            if (matchesScope(app, GroupRestoreScope.SINCE_LAST_RESTORE, record, installed)) sinceLastRestore++
        }
        return ScopeCounts(notInstalled, all, sinceLastRestore)
    }

    fun preview(
        group: SnapGroup,
        scope: GroupRestoreScope,
        strategy: ArchivePickStrategy,
        records: Map<String, RestoreRecord>,
        isInstalled: (ArchivedApp) -> Boolean
    ): Preview {
        var skippedNoArchive = 0
        var fallbackCount = 0
        val tasks = mutableListOf<GroupRestoreTask>()

        for (app in group.apps) {
            val archives = synchronized(app.archives) { app.archives.values.toList() }
            if (archives.isEmpty()) {
                skippedNoArchive++
                continue
            }
            val record = recordFor(app, records)
            val installed = isInstalled(app)
            if (!matchesScope(app, scope, record, installed)) continue

            val pick = ArchiveResolver.pick(archives, strategy, record)
            if (pick.fallbackToNewest) fallbackCount++
            tasks.add(GroupRestoreTask(app, pick.archive, pick.fallbackToNewest))
        }

        return Preview(tasks, skippedNoArchive, fallbackCount)
    }

    fun resolveTaskAt(task: GroupRestoreTask, groups: List<SnapGroup>): GroupRestoreTask? {
        val group = groups.find { it.id == task.app.group.id } ?: return null
        val app = group.apps.find {
            it.appInfo.packageName == task.app.appInfo.packageName &&
                it.appInfo.userId == task.app.appInfo.userId
        } ?: return null
        val archive = synchronized(app.archives) {
            app.archives[task.archive.name]
        } ?: return null
        return task.copy(app = app, archive = archive)
    }

    fun matchesScope(
        app: ArchivedApp,
        scope: GroupRestoreScope,
        record: RestoreRecord?,
        isInstalled: Boolean
    ): Boolean {
        if (app.archives.isEmpty()) return false
        return when (scope) {
            GroupRestoreScope.NOT_INSTALLED -> !isInstalled
            GroupRestoreScope.ALL -> true
            GroupRestoreScope.SINCE_LAST_RESTORE -> needsRestoreSinceLast(app, record)
        }
    }

    fun needsRestoreSinceLast(app: ArchivedApp, record: RestoreRecord?): Boolean {
        val latest = app.latestArchive ?: return false
        if (record == null) return true
        return latest.metaInfo.makeTime > record.archiveMakeTime
    }

    private fun recordFor(app: ArchivedApp, records: Map<String, RestoreRecord>): RestoreRecord? =
        records["${app.appInfo.packageName}:${app.appInfo.userId}"]
}
