package tiiehenry.android.app.snapshot.instance

import android.os.ParcelFileDescriptor
import com.alibaba.fastjson2.JSON
import tiiehenry.android.app.snapshot.archive.MetaInfoHelper
import tiiehenry.android.app.snapshot.archive.bean.MetaInfo
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.IFileType
import java.io.FileReader
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

/**
 * 固定实例放在应用目录的 instances/ 下。
 * 这里的备份不进存档列表，保留策略不会清掉它；删掉实例才会删掉这份备份。
 */
class InstanceCatalog(
    private val fs: IFileSystem,
    private val packageDir: String,
    private val readTextFile: (String) -> String? = { path -> readTextFromFs(fs, path) },
) {
    fun list(): List<AppInstance> {
        val root = rootPath()
        if (!fs.exists(root)) return emptyList()
        return fs.listDir(root)
            .filter { InstanceNames.isSlotId(it) }
            .mapNotNull { read(it) }
            .sortedBy { it.createdAt }
    }

    /**
     * Whether `instances/` contains any slot, including one whose metadata is
     * unreadable. Callers that delete shared APKs must treat an unreadable
     * catalog as "still referenced" rather than deleting data they cannot see.
     */
    fun hasSlots(): Boolean {
        val root = rootPath()
        return try {
            if (!fs.exists(root)) return false
            fs.listDir(root).any { InstanceNames.baseId(it) != null }
        } catch (e: Exception) {
            true
        }
    }

    /**
     * 只在持有 packageOpGuard 时调用；纯读取路径不能清理提交目录。
     * @return false 表示至少一个槽仍未收成，调用方应保留重试机会。
     */
    fun reconcileAll(): Boolean {
        val root = rootPath()
        val ids = try {
            if (!fs.exists(root)) return true
            fs.listDir(root).mapNotNull { InstanceNames.baseId(it) }.distinct()
        } catch (e: Exception) {
            return false
        }
        // One unreadable slot must not stop recovery of the others.
        var allReconciled = true
        ids.forEach { id ->
            if (!runCatching { reconcile(id) }.getOrDefault(false)) {
                allReconciled = false
            }
        }
        return allReconciled
    }

    fun read(id: String): AppInstance? {
        if (!InstanceNames.isSlotId(id)) return null
        val path = metaPath(id)
        // A crash between moving the old file to `.bak` and publishing the new
        // file leaves only the backup readable. Try it before declaring the slot
        // corrupt.
        for (candidate in listOf(
            path,
            path + InstancePaths.META_BACKUP_SUFFIX,
            path + InstancePaths.META_TEMP_SUFFIX,
        )) {
            val text = runCatching { readTextFile(candidate) }.getOrNull() ?: continue
            val decoded = InstanceMeta.decode(text) ?: continue
            return decoded.copy(id = id)
        }
        return null
    }

    fun hasSlot(id: String): Boolean =
        InstanceNames.isSlotId(id) && runCatching { fs.exists(slotPath(id)) }.getOrDefault(false)

    fun writeCommitted(dir: String, instance: AppInstance) {
        writeText(Paths.get(dir, InstancePaths.META_FILE).absolutePathString(), InstanceMeta.encode(instance))
    }

    fun deleteSlot(id: String): Boolean {
        if (!InstanceNames.isSlotId(id)) return false
        return try {
            val slotGone = !fs.exists(slotPath(id)) || fs.delete(slotPath(id))
            fs.delete(committingPath(id))
            fs.delete(previousPath(id))
            slotGone && !fs.exists(slotPath(id)) &&
                !fs.exists(committingPath(id)) && !fs.exists(previousPath(id))
        } catch (e: Exception) {
            false
        }
    }

    fun committingPath(id: String): String = child(id + InstancePaths.COMMITTING_SUFFIX)

    fun previousPath(id: String): String = child(id + InstancePaths.PREVIOUS_SUFFIX)

    fun slotPath(id: String): String = child(id)

    /**
     * 用刚写好的提交目录替换 `{id}`。成功后 `instances/` 里这个 id 只剩一份备份。
     */
    fun publishCommit(id: String): Boolean {
        return try {
            val committing = committingPath(id)
            val finalSlot = slotPath(id)
            val previous = previousPath(id)
            if (!fs.exists(committing) || backupReady(committing) != SlotReadiness.Ready) {
                return false
            }
            if (fs.exists(previous)) {
                if (!fs.exists(finalSlot)) {
                    // A crash left only `.previous`; restore it before replacing
                    // the slot so a failed move can still roll back.
                    if (backupReady(previous) != SlotReadiness.Ready) return false
                    if (!fs.move(previous, finalSlot)) return false
                } else {
                    when (backupReady(finalSlot)) {
                        SlotReadiness.Ready -> if (!fs.delete(previous)) return false
                        SlotReadiness.Unavailable -> {
                            // The published slot is confirmed unusable; promote the
                            // previous copy before staging the new commit so a failed
                            // move can still roll back to a complete slot.
                            if (!fs.delete(finalSlot)) return false
                            if (!fs.move(previous, finalSlot)) return false
                        }
                        // A read failure is not proof of corruption. Keep both the
                        // published slot and `.previous` for the next reconcile.
                        SlotReadiness.Unknown -> return false
                    }
                }
            }
            if (fs.exists(finalSlot) && backupReady(finalSlot) == SlotReadiness.Unknown) {
                return false
            }
            if (fs.exists(finalSlot) && !fs.move(finalSlot, previous)) return false
            if (!fs.move(committing, finalSlot)) {
                if (fs.exists(previous)) fs.move(previous, finalSlot)
                return false
            }
            // The new slot is already published. A leftover `.previous` is cleanup debt,
            // not a failed update; the next guarded reconcile removes it.
            if (fs.exists(previous)) fs.delete(previous)
            fs.exists(finalSlot) && !fs.exists(committing)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 把同一 id 收成一个目录：有正式槽就删掉临时副本，没有就从临时目录恢复那一份。
     * 正式槽的元数据损坏时，先用 `.bak`/`.tmp` 恢复；在元数据可读之前不删除任何恢复副本。
     * @return false 表示仍有未处理的恢复副本，下次收成应重试。
     */
    fun reconcile(id: String): Boolean = reconcileDetailed(id) == ReconcileResult.Done

    internal fun reconcileDetailed(id: String): ReconcileResult {
        if (!InstanceNames.isSlotId(id)) return ReconcileResult.Failed
        val slot = slotPath(id)
        val committing = committingPath(id)
        val previous = previousPath(id)
        val meta = Paths.get(slot, InstancePaths.META_FILE).absolutePathString()
        val metaBackup = meta + InstancePaths.META_BACKUP_SUFFIX
        val metaTemp = meta + InstancePaths.META_TEMP_SUFFIX
        return try {
            if (fs.exists(slot)) {
                // A crash during metadata replacement can leave the new temp file,
                // the old backup, or both. Restore the best readable copy first.
                when (metaReadiness(slot)) {
                    SlotReadiness.Unknown -> return ReconcileResult.CleanupPending
                    SlotReadiness.Unavailable -> {
                        return replaceWithFallback(slot, previous, committing)
                    }
                    SlotReadiness.Ready -> Unit
                }
                if (!recoverMeta(slot)) {
                    // A readable recovery copy that could not be moved is not a
                    // reason to delete the slot; retry on the next reconcile.
                    if (hasReadableMetaCopy(slot)) return ReconcileResult.CleanupPending
                    val replaced = replaceWithFallback(slot, previous, committing)
                    if (replaced != ReconcileResult.Done) return replaced
                }
                // A readable instance.json is not enough: the payload manifest
                // must also be present before recovery copies can be discarded.
                when (backupReady(slot)) {
                    SlotReadiness.Unknown -> return ReconcileResult.CleanupPending
                    SlotReadiness.Unavailable -> {
                        val replaced = replaceWithFallback(slot, previous, committing)
                        if (replaced != ReconcileResult.Done) return replaced
                    }
                    SlotReadiness.Ready -> Unit
                }
                // Only discard recovery copies once a readable published file exists.
                var cleaned = true
                if (fs.exists(metaBackup) && !fs.delete(metaBackup)) cleaned = false
                if (fs.exists(metaTemp) && !fs.delete(metaTemp)) cleaned = false
                if (fs.exists(previous) && !fs.delete(previous)) cleaned = false
                if (fs.exists(committing) && !fs.delete(committing)) cleaned = false
                return if (cleaned) ReconcileResult.Done else ReconcileResult.CleanupPending
            }
            if (fs.exists(previous)) {
                when (backupReady(previous)) {
                    SlotReadiness.Unknown -> return ReconcileResult.CleanupPending
                    SlotReadiness.Ready -> {
                        if (!fs.move(previous, slot)) return ReconcileResult.Failed
                        if (!recoverMeta(slot)) return ReconcileResult.Failed
                        return if (!fs.exists(committing) || fs.delete(committing)) {
                            ReconcileResult.Done
                        } else {
                            ReconcileResult.CleanupPending
                        }
                    }
                    SlotReadiness.Unavailable -> Unit
                }
                // The old slot is unusable; a complete staged copy is the only
                // recoverable data left. Publish it and drop the corrupt slot.
                if (fs.exists(committing)) {
                    when (backupReady(committing)) {
                        SlotReadiness.Unknown -> return ReconcileResult.CleanupPending
                        SlotReadiness.Ready -> {
                            if (!fs.move(committing, slot)) return ReconcileResult.Failed
                            if (!recoverMeta(slot)) return ReconcileResult.Failed
                            return if (!fs.exists(previous) || fs.delete(previous)) {
                                ReconcileResult.Done
                            } else {
                                ReconcileResult.CleanupPending
                            }
                        }
                        SlotReadiness.Unavailable -> Unit
                    }
                }
                return ReconcileResult.Failed
            }
            if (fs.exists(committing)) {
                // A staging directory without a readable manifest is an
                // incomplete backup, not a recoverable slot. Delete it so it
                // does not trigger a reconcile retry on every group load.
                when (backupReady(committing)) {
                    SlotReadiness.Unknown -> return ReconcileResult.CleanupPending
                    SlotReadiness.Unavailable -> {
                        // A transient read failure must not destroy a complete
                        // staging copy; only a manifest that is truly absent is
                        // known to be an incomplete backup.
                        if (manifestExists(committing)) return ReconcileResult.CleanupPending
                        return if (!fs.exists(committing) || fs.delete(committing)) {
                            ReconcileResult.Done
                        } else {
                            ReconcileResult.CleanupPending
                        }
                    }
                    SlotReadiness.Ready -> Unit
                }
                if (!fs.move(committing, slot)) return ReconcileResult.Failed
                return if (recoverMeta(slot)) ReconcileResult.Done else ReconcileResult.Failed
            }
            ReconcileResult.Done
        } catch (e: Exception) {
            ReconcileResult.Failed
        }
    }

    private fun readableMeta(path: String): Boolean = metaState(path) == SlotReadiness.Ready

    private fun metaReadiness(dir: String): SlotReadiness {
        val meta = Paths.get(dir, InstancePaths.META_FILE).absolutePathString()
        when (val primary = metaState(meta)) {
            SlotReadiness.Ready -> return SlotReadiness.Ready
            // A read failure on the published file is not proof that it is
            // corrupt; never replace it just because a backup is readable.
            SlotReadiness.Unknown -> return SlotReadiness.Unknown
            SlotReadiness.Unavailable -> Unit
        }
        val copies = listOf(
            meta + InstancePaths.META_BACKUP_SUFFIX,
            meta + InstancePaths.META_TEMP_SUFFIX,
        ).map(::metaState)
        if (copies.any { it == SlotReadiness.Ready }) return SlotReadiness.Ready
        if (copies.any { it == SlotReadiness.Unknown }) return SlotReadiness.Unknown
        return SlotReadiness.Unavailable
    }

    private fun metaState(path: String): SlotReadiness {
        val type = try {
            fs.fileType(path)
        } catch (e: Exception) {
            return SlotReadiness.Unknown
        }
        when (type) {
            IFileType.TYPE_NONE -> return SlotReadiness.Unavailable
            IFileType.TYPE_FILE -> Unit
            else -> return SlotReadiness.Unknown
        }
        val text = try {
            readTextFile(path)
        } catch (e: Exception) {
            return SlotReadiness.Unknown
        }
        if (text == null) return SlotReadiness.Unknown
        return if (InstanceMeta.decode(text) != null) {
            SlotReadiness.Ready
        } else {
            SlotReadiness.Unavailable
        }
    }

    /** Ensure `dir/instance.json` is readable, recovering `.bak` then `.tmp` if needed. */
    private fun recoverMeta(dir: String): Boolean {
        val meta = Paths.get(dir, InstancePaths.META_FILE).absolutePathString()
        when (metaState(meta)) {
            SlotReadiness.Ready -> return true
            // A transient read failure must not be treated as a corrupt file
            // that is safe to delete.
            SlotReadiness.Unknown -> return false
            SlotReadiness.Unavailable -> Unit
        }
        val source = listOf(
            meta + InstancePaths.META_BACKUP_SUFFIX,
            meta + InstancePaths.META_TEMP_SUFFIX,
        ).firstOrNull { readableMeta(it) } ?: return false
        if (fs.exists(meta) && !fs.delete(meta)) return false
        return fs.move(source, meta)
    }

    private fun hasReadableMetaCopy(dir: String): Boolean {
        val meta = Paths.get(dir, InstancePaths.META_FILE).absolutePathString()
        return readableMeta(meta + InstancePaths.META_BACKUP_SUFFIX) ||
            readableMeta(meta + InstancePaths.META_TEMP_SUFFIX)
    }

    /** Replace an unusable slot with the previous or staged copy, if either is complete. */
    private fun replaceWithFallback(slot: String, previous: String, committing: String): ReconcileResult {
        val previousState = if (fs.exists(previous)) backupReady(previous) else SlotReadiness.Unavailable
        val committingState = if (fs.exists(committing)) backupReady(committing) else SlotReadiness.Unavailable
        val source = when {
            previousState == SlotReadiness.Ready -> previous
            committingState == SlotReadiness.Ready -> committing
            // A read failure on a candidate is not proof that it is unusable.
            // Keep the published slot untouched and retry later.
            previousState == SlotReadiness.Unknown || committingState == SlotReadiness.Unknown ->
                return ReconcileResult.CleanupPending
            else -> return ReconcileResult.Failed
        }
        if (fs.exists(slot) && !fs.delete(slot)) return ReconcileResult.Failed
        if (!fs.move(source, slot)) return ReconcileResult.Failed
        if (!recoverMeta(slot)) return ReconcileResult.Failed
        if (backupReady(slot) != SlotReadiness.Ready) return ReconcileResult.Failed
        val leftover = if (source == previous) committing else previous
        return if (!fs.exists(leftover) || fs.delete(leftover)) {
            ReconcileResult.Done
        } else {
            ReconcileResult.CleanupPending
        }
    }

    fun rootPath(): String = Paths.get(packageDir, InstancePaths.ROOT_DIR).absolutePathString()

    /**
     * 实例恢复可能需要同版本、同大小的共享 APK，即使清单里没有显式 apk.json。
     * 读不到某个槽的清单时保守返回 true，避免清理路径误删仍可能被引用的 APK。
     */
    fun referencesApk(versionCode: Long, size: Long): Boolean {
        val root = rootPath()
        val ids = try {
            if (!fs.exists(root)) return false
            fs.listDir(root).mapNotNull { InstanceNames.baseId(it) }.distinct()
        } catch (e: Exception) {
            return true
        }
        return ids.any { id ->
            // A crash can leave only `.committing` / `.previous`; it still owns
            // the APK until reconcile publishes or removes it.
            listOf(slotPath(id), committingPath(id), previousPath(id))
                .filter { fs.exists(it) }
                .any { dir ->
                    val metaFile = Paths.get(dir, MetaInfoHelper.META_INFO_FILE_NAME).absolutePathString()
                    val meta = try {
                        MetaInfoHelper.read(fs, metaFile)
                    } catch (e: Exception) {
                        return@any true
                    } ?: return@any true
                    val pkg = meta.packageInfo ?: return@any true
                    pkg.versionCode == versionCode && pkg.size == size
                }
        }
    }

    private fun backupReady(dir: String): SlotReadiness {
        val manifest = Paths.get(dir, MetaInfoHelper.META_INFO_FILE_NAME).absolutePathString()
        val type = try {
            fs.fileType(manifest)
        } catch (e: Exception) {
            return SlotReadiness.Unknown
        }
        if (type == IFileType.TYPE_NONE) return SlotReadiness.Unavailable
        if (type != IFileType.TYPE_FILE) return SlotReadiness.Unknown
        val text = try {
            readTextFile(manifest)
        } catch (e: Exception) {
            return SlotReadiness.Unknown
        } ?: return SlotReadiness.Unknown
        // A parse failure means the file is structurally invalid, not that the
        // read is still pending.
        val manifestState = try {
            val meta = JSON.parseObject(text, MetaInfo::class.java)
            if (meta?.packageInfo != null) SlotReadiness.Ready else SlotReadiness.Unavailable
        } catch (e: Exception) {
            SlotReadiness.Unavailable
        }
        if (manifestState != SlotReadiness.Ready) return manifestState
        val metaState = metaReadiness(dir)
        return when (metaState) {
            SlotReadiness.Ready -> SlotReadiness.Ready
            SlotReadiness.Unknown -> SlotReadiness.Unknown
            SlotReadiness.Unavailable -> SlotReadiness.Unavailable
        }
    }

    private fun manifestExists(dir: String): Boolean {
        val manifest = Paths.get(dir, MetaInfoHelper.META_INFO_FILE_NAME).absolutePathString()
        return try {
            fs.fileType(manifest) != IFileType.TYPE_NONE
        } catch (e: Exception) {
            true
        }
    }

    private fun child(name: String): String = Paths.get(rootPath(), name).absolutePathString()

    private fun metaPath(id: String): String =
        Paths.get(slotPath(id), InstancePaths.META_FILE).absolutePathString()

    private fun writeText(path: String, text: String) {
        try {
            val parent = fs.getParent(path)
            if (!parent.isNullOrEmpty()) fs.mkdirs(parent)
            val temp = path + InstancePaths.META_TEMP_SUFFIX
            val backup = path + InstancePaths.META_BACKUP_SUFFIX
            // Write the new copy completely before touching the current file or its
            // backup. A crash here leaves the old metadata untouched.
            if (fs.exists(temp) && !fs.delete(temp)) {
                throw InstanceException("instance metadata write failed", InstanceError.META_WRITE_FAILED)
            }
            val mode = ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_TRUNCATE or
                ParcelFileDescriptor.MODE_WRITE_ONLY
            fs.openFile(temp, mode).use { pfd ->
                ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                }
            }
            if (fs.exists(path)) {
                if (fs.exists(backup) && !fs.delete(backup)) {
                    fs.delete(temp)
                    throw InstanceException("instance metadata write failed", InstanceError.META_WRITE_FAILED)
                }
                if (!fs.move(path, backup)) {
                    fs.delete(temp)
                    throw InstanceException("instance metadata write failed", InstanceError.META_WRITE_FAILED)
                }
            }
            if (!fs.move(temp, path)) {
                if (fs.exists(backup)) fs.move(backup, path)
                fs.delete(temp)
                throw InstanceException("instance metadata write failed", InstanceError.META_WRITE_FAILED)
            }
            if (fs.exists(backup)) fs.delete(backup)
        } catch (e: InstanceException) {
            throw e
        } catch (e: Exception) {
            throw InstanceException("instance metadata write failed", InstanceError.META_WRITE_FAILED)
        }
    }
}

internal enum class ReconcileResult {
    Done,
    CleanupPending,
    Failed,
}

private enum class SlotReadiness {
    Ready,
    Unavailable,
    Unknown,
}

private fun readTextFromFs(fs: IFileSystem, path: String): String? {
    return try {
        if (!fs.exists(path)) return null
        fs.openFile(path, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            FileReader(pfd.fileDescriptor).readText()
        }
    } catch (e: Exception) {
        null
    }
}
