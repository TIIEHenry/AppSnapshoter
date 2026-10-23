package tiiehenry.android.app.snapshot.instance

import android.os.ParcelFileDescriptor
import tiiehenry.android.app.snapshot.archive.MetaInfoHelper
import tiiehenry.android.snapshot.file.IFileSystem
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
) {
    fun list(): List<AppInstance> {
        val root = rootPath()
        if (!fs.exists(root)) return emptyList()
        reconcileAll()
        return fs.listDir(root)
            .filter { InstanceNames.isSlotId(it) }
            .mapNotNull { read(it) }
            .sortedBy { it.createdAt }
    }

    fun read(id: String): AppInstance? {
        if (!InstanceNames.isSlotId(id)) return null
        val text = readText(metaPath(id)) ?: return null
        val decoded = InstanceMeta.decode(text) ?: return null
        return decoded.copy(id = id)
    }

    fun writeCommitted(dir: String, instance: AppInstance) {
        writeText(Paths.get(dir, InstancePaths.META_FILE).absolutePathString(), InstanceMeta.encode(instance))
    }

    fun deleteSlot(id: String): Boolean {
        if (!InstanceNames.isSlotId(id)) return false
        val slotGone = !fs.exists(slotPath(id)) || fs.delete(slotPath(id))
        fs.delete(committingPath(id))
        fs.delete(previousPath(id))
        return slotGone && !fs.exists(slotPath(id)) &&
            !fs.exists(committingPath(id)) && !fs.exists(previousPath(id))
    }

    fun committingPath(id: String): String = child(id + InstancePaths.COMMITTING_SUFFIX)

    fun previousPath(id: String): String = child(id + InstancePaths.PREVIOUS_SUFFIX)

    fun slotPath(id: String): String = child(id)

    /**
     * 用刚写好的提交目录替换 `{id}`。成功后 `instances/` 里这个 id 只剩一份备份。
     */
    fun publishCommit(id: String): Boolean {
        val committing = committingPath(id)
        val finalSlot = slotPath(id)
        val previous = previousPath(id)
        if (fs.exists(previous) && !fs.delete(previous)) return false
        if (fs.exists(finalSlot) && !fs.move(finalSlot, previous)) return false
        if (!fs.move(committing, finalSlot)) {
            if (fs.exists(previous)) fs.move(previous, finalSlot)
            return false
        }
        if (fs.exists(previous) && !fs.delete(previous)) {
            reconcile(id)
        }
        return fs.exists(finalSlot) && !fs.exists(committing) && !fs.exists(previous)
    }

    /** 把同一 id 收成一个目录：有正式槽就删掉临时副本，没有就从临时目录恢复那一份。 */
    fun reconcile(id: String) {
        if (!InstanceNames.isSlotId(id)) return
        val slot = slotPath(id)
        val committing = committingPath(id)
        val previous = previousPath(id)
        if (fs.exists(slot)) {
            if (fs.exists(previous)) fs.delete(previous)
            if (fs.exists(committing)) fs.delete(committing)
            return
        }
        if (fs.exists(previous)) {
            if (fs.move(previous, slot)) fs.delete(committing)
            return
        }
        if (fs.exists(committing) && backupReady(committing)) {
            fs.move(committing, slot)
        }
    }

    fun rootPath(): String = Paths.get(packageDir, InstancePaths.ROOT_DIR).absolutePathString()

    private fun reconcileAll() {
        val root = rootPath()
        if (!fs.exists(root)) return
        fs.listDir(root).mapNotNull { InstanceNames.baseId(it) }.distinct().forEach { reconcile(it) }
    }

    private fun backupReady(dir: String): Boolean {
        val manifest = Paths.get(dir, MetaInfoHelper.META_INFO_FILE_NAME).absolutePathString()
        val info = Paths.get(dir, InstancePaths.META_FILE).absolutePathString()
        return fs.exists(manifest) && fs.exists(info)
    }

    private fun child(name: String): String = Paths.get(rootPath(), name).absolutePathString()

    private fun metaPath(id: String): String =
        Paths.get(slotPath(id), InstancePaths.META_FILE).absolutePathString()

    private fun readText(path: String): String? {
        if (!fs.exists(path)) return null
        return try {
            fs.openFile(path, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                FileReader(pfd.fileDescriptor).readText()
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun writeText(path: String, text: String) {
        val parent = fs.getParent(path)
        if (!parent.isNullOrEmpty()) fs.mkdirs(parent)
        val mode = ParcelFileDescriptor.MODE_CREATE or
            ParcelFileDescriptor.MODE_TRUNCATE or
            ParcelFileDescriptor.MODE_WRITE_ONLY
        fs.openFile(path, mode).use { pfd ->
            ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
                out.write(text.toByteArray(Charsets.UTF_8))
            }
        }
    }
}
