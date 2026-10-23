package tiiehenry.android.app.snapshot.config

import java.io.File

/** File-backed settings, independent of the local MMKV path registration. */
internal data class GroupConfigState(
    val shot: ShotConfig,
    val exclude: ExcludeConfig,
    val action: ActionConfig,
    val group: GroupConfigData,
) {
    companion object {
        fun load(rootPath: String): GroupConfigState {
            val root = File(rootPath)
            return GroupConfigState(
                read(File(root, ConfigFiles.SHOT_CONFIG_FILE), ShotConfig::fromJson) ?: ShotConfig(),
                read(File(root, ConfigFiles.EXCLUDE_CONFIG_FILE), ExcludeConfig::fromJson) ?: ExcludeConfig(),
                read(File(root, ConfigFiles.ACTION_CONFIG_FILE), ActionConfig::fromJson) ?: ActionConfig(),
                read(File(root, ConfigFiles.GROUP_CONFIG_FILE), GroupConfigData::fromJson) ?: GroupConfigData(),
            )
        }

        fun forRegistration(rootPath: String, name: String, userId: Int): GroupConfigState =
            load(rootPath).also {
                it.group.name = name
                it.group.userId = userId
            }

        private fun <T> read(file: File, parser: (String) -> T): T? = try {
            if (file.exists()) parser(file.readText()) else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
