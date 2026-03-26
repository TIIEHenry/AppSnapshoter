package tiiehenry.android.app.snapshot.data.archive

import tiiehenry.android.snapshot.task.ITaskHandler

data class SnapshotTasks(val dir: String, val tasks: LinkedHashMap<String, ITaskHandler>) {
}