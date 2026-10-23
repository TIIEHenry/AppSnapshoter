package tiiehenry.android.app.snapshot.archive.make

import tiiehenry.android.snapshot.task.ITaskHandler

data class SnapshotTasks(
    val dir: String,
    val tasks: LinkedHashMap<String, ITaskHandler>,
    val finalDir: String = dir
)

/** Destructive post-backup actions are reachable only after a verified commit. */
internal fun commitSnapshot(
    writeMetadata: () -> Unit,
    validate: () -> Unit,
    publish: () -> Unit,
    afterCommit: () -> Unit
) {
    writeMetadata()
    validate()
    publish()
    afterCommit()
}
