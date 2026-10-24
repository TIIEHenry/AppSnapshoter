package tiiehenry.android.app.snapshot.config

import java.io.File

/** File-backed settings, independent of the local MMKV path registration. */
internal data class GroupConfigState(
    val shot: ShotConfig,
    val exclude: ExcludeConfig,
    val action: ActionConfig,
    val group: GroupConfigData,
    val readFailed: Boolean = false,
) {
    companion object {
        fun load(rootPath: String): GroupConfigState {
            val root = File(rootPath)
            val shot = ConfigFileStore.read(File(root, ConfigFiles.SHOT_CONFIG_FILE)) { ShotConfig.fromJson(it) }
            val exclude = ConfigFileStore.read(File(root, ConfigFiles.EXCLUDE_CONFIG_FILE)) {
                ExcludeConfig.fromJson(it)
            }
            val action = ConfigFileStore.read(File(root, ConfigFiles.ACTION_CONFIG_FILE)) {
                ActionConfig.fromJson(it)
            }
            val group = ConfigFileStore.read(File(root, ConfigFiles.GROUP_CONFIG_FILE)) {
                GroupConfigData.fromJson(it)
            }
            return GroupConfigState(
                shot.value ?: ShotConfig(),
                exclude.value ?: ExcludeConfig(),
                action.value ?: ActionConfig(),
                group.value ?: GroupConfigData(),
                readFailed = shot.failed || exclude.failed || action.failed || group.failed,
            )
        }

        fun forRegistration(rootPath: String, name: String, userId: Int): GroupConfigState =
            load(rootPath).also {
                it.group.name = name
                it.group.userId = userId
            }
    }
}
