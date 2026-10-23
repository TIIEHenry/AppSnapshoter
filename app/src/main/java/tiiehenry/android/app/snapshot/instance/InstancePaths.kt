package tiiehenry.android.app.snapshot.instance

/**
 * 固定实例在 `{packageDir}/instances/{id}/`。
 * 每个 id 只保留一份备份。`.committing` / `.previous` 只在替换过程中出现，结束后删除。
 */
object InstancePaths {
    const val ROOT_DIR = "instances"
    const val META_FILE = "instance.json"
    const val COMMITTING_SUFFIX = ".committing"
    const val PREVIOUS_SUFFIX = ".previous"
}
