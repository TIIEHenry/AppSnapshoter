package tiiehenry.android.app.snapshot.archieve.make

import tiiehenry.android.snapshot.task.ITaskHandler

data class SnapshotTasks(val dir: String, val tasks: LinkedHashMap<String, ITaskHandler>) {
}