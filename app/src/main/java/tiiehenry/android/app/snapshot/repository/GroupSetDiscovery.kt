package tiiehenry.android.app.snapshot.repository

import tiiehenry.android.app.snapshot.config.ConfigFiles
import tiiehenry.android.snapshot.fs.IFileType
import java.nio.file.Paths
import java.util.UUID
import kotlin.io.path.absolutePathString

internal data class DiscoveredDirectory(
    val basename: String,
    val path: String,
    val hasGroupJson: Boolean,
)

/**
 * [committed] 为 false 时，[groups]、[groupOrder] 保持入参，[removedIds] 为空。
 * 调用方不得据此改登记、清 MMKV 或 save 集配置。
 */
internal data class GroupSetDiscoveryResult(
    val committed: Boolean,
    val groups: List<String>,
    val removedIds: List<String>,
    val added: List<Pair<String, DiscoveredDirectory>>,
    val groupOrder: List<String>,
    val newCount: Int,
    val failure: String? = null,
)

/**
 * 列目录或任一子项 stat 失败则整次放弃。
 * 名字以 `.` 开头的真实子目录与普通目录一样看类型和 group.json。
 * 像包名且没有 group.json 的目录不进名单。
 */
internal fun discoverGroupSetMembers(
    setPath: String,
    groups: List<String>,
    pathsById: Map<String, String>,
    groupOrder: List<String>,
    listDir: (String) -> List<String>?,
    fileType: (String) -> Int,
    groupJsonExists: (String) -> Boolean,
    allocateId: () -> String = { UUID.randomUUID().toString().substring(0, 7) },
): GroupSetDiscoveryResult {
    fun aborted(reason: String) = GroupSetDiscoveryResult(
        committed = false,
        groups = groups,
        removedIds = emptyList(),
        added = emptyList(),
        groupOrder = groupOrder,
        newCount = 0,
        failure = reason,
    )

    val normalizedSet = GroupSetMembership.normalizePath(setPath)
    val names = try {
        listDir(normalizedSet)
    } catch (e: Exception) {
        return aborted("listDir failed: ${e.message}")
    } ?: return aborted("listDir returned null")

    val found = mutableListOf<DiscoveredDirectory>()
    for (name in names) {
        if (name == "." || name == "..") continue
        val childPath = Paths.get(normalizedSet, name).absolutePathString()
        val type = try {
            fileType(childPath)
        } catch (e: Exception) {
            return aborted("fileType failed: $childPath: ${e.message}")
        }
        if (type != IFileType.TYPE_DIR) continue
        val jsonPath = Paths.get(childPath, ConfigFiles.GROUP_CONFIG_FILE).absolutePathString()
        val hasGroupJson = try {
            groupJsonExists(jsonPath)
        } catch (e: Exception) {
            return aborted("group.json stat failed: $jsonPath: ${e.message}")
        }
        if (GroupSetMembership.looksLikePackageName(name) && !hasGroupJson) continue
        found += DiscoveredDirectory(name, childPath, hasGroupJson)
    }

    val foundBases = found.map { it.basename }
    val foundBaseSet = foundBases.toSet()
    val idByPath = pathsById.entries.associate { (id, path) ->
        GroupSetMembership.normalizePath(path) to id
    }
    val added = mutableListOf<Pair<String, DiscoveredDirectory>>()
    for (dir in found) {
        val normalizedChild = GroupSetMembership.normalizePath(dir.path)
        if (normalizedChild in idByPath) continue
        added += allocateId() to dir
    }

    val removedIds = groups.filter { id ->
        val path = pathsById[id] ?: return@filter false
        val normalized = GroupSetMembership.normalizePath(path)
        GroupSetMembership.isMemberOf(normalized, normalizedSet) &&
            GroupSetMembership.basename(normalized) !in foundBaseSet
    }

    val order = groupOrder.toMutableList()
    for (base in foundBases) {
        if (base !in order) order += base
    }
    order.removeAll { it !in foundBaseSet }

    val nextGroups = groups.toMutableList()
    for ((id, _) in added) nextGroups += id
    for (id in removedIds) nextGroups.remove(id)

    return GroupSetDiscoveryResult(
        committed = true,
        groups = nextGroups.distinct(),
        removedIds = removedIds,
        added = added,
        groupOrder = order,
        newCount = added.size,
    )
}
