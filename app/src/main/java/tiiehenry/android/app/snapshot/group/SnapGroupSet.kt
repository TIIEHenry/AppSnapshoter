package tiiehenry.android.app.snapshot.group

import tiiehenry.android.app.snapshot.config.GroupSetConfig
import java.nio.file.Paths

/**
 * 分组集：同一父目录下多个 [SnapGroup] 的组织容器。
 * 成员由 path 派生，不另存 ID 列表当真源。
 */
data class SnapGroupSet(
    val id: String,
) {
    val config by lazy { GroupSetConfig(id) }

    var name: String
        get() {
            val configName = config.data.name
            if (!configName.isNullOrEmpty()) {
                return configName
            }
            val pathName = Paths.get(path).fileName?.toString()
            if (!pathName.isNullOrEmpty()) {
                return pathName
            }
            return id
        }
        set(value) {
            config.data.name = value
        }

    var path: String
        get() = config.rootPath
        set(value) {
            config.rootPath = value
        }

    var isCollapsed: Boolean
        get() = config.isCollapsed
        set(value) {
            config.isCollapsed = value
        }

    /** 集内顺序：直接子目录 basename */
    var groupOrder: List<String>
        get() = config.data.groupOrder?.toList() ?: emptyList()
        set(value) {
            config.data.groupOrder = ArrayList(value)
        }

    fun save() {
        config.save()
    }
}
