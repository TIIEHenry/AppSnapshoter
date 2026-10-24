package tiiehenry.android.app.snapshot.instance

import android.os.ParcelFileDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tiiehenry.android.snapshot.file.IFileCompressor
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.IFileType

class InstanceCatalogTest {
    private val instanceJson =
        """{"id":"slot","name":"name","account":"","note":"","externalId":"","createdAt":1,"updatedAt":1}"""
    private val manifestJson = """{"packageInfo":{"packageName":"com.example","versionCode":1,"size":1}}"""

    @Test
    fun `unreadable published manifest keeps both slots during publish`() {
        val fs = MemoryFs()
        fs.file("$ROOT/slot.committing/meta-info.json", manifestJson)
        fs.file("$ROOT/slot.committing/instance.json", instanceJson)
        fs.file("$ROOT/slot/meta-info.json", manifestJson)
        fs.file("$ROOT/slot/instance.json", instanceJson)
        fs.file("$ROOT/slot.previous/meta-info.json", manifestJson)
        fs.file("$ROOT/slot.previous/instance.json", instanceJson)
        val catalog = InstanceCatalog(fs, "/pkg") { path ->
            if (path == "$ROOT/slot/meta-info.json") throw IllegalStateException("read failed")
            fs.read(path)
        }

        assertFalse(catalog.publishCommit("slot"))
        assertTrue(fs.exists("$ROOT/slot/meta-info.json"))
        assertTrue(fs.exists("$ROOT/slot.previous/meta-info.json"))
        assertTrue(fs.exists("$ROOT/slot.committing/meta-info.json"))
    }

    @Test
    fun `unreadable published manifest is not replaced by reconcile`() {
        val fs = MemoryFs()
        fs.file("$ROOT/slot/meta-info.json", manifestJson)
        fs.file("$ROOT/slot/instance.json", instanceJson)
        fs.file("$ROOT/slot.previous/meta-info.json", manifestJson)
        fs.file("$ROOT/slot.previous/instance.json", instanceJson)
        val catalog = InstanceCatalog(fs, "/pkg") { path ->
            if (path == "$ROOT/slot/meta-info.json") throw IllegalStateException("read failed")
            fs.read(path)
        }

        assertFalse(catalog.reconcile("slot"))
        assertTrue(fs.exists("$ROOT/slot/meta-info.json"))
        assertTrue(fs.exists("$ROOT/slot.previous/meta-info.json"))
    }

    @Test
    fun `confirmed missing manifest is replaced by a complete previous slot`() {
        val fs = MemoryFs()
        fs.dir("$ROOT/slot")
        fs.file("$ROOT/slot/instance.json", instanceJson)
        fs.file("$ROOT/slot.previous/meta-info.json", manifestJson)
        fs.file("$ROOT/slot.previous/instance.json", instanceJson)
        val catalog = InstanceCatalog(fs, "/pkg") { fs.read(it) }

        assertTrue(catalog.reconcile("slot"))
        assertTrue(fs.exists("$ROOT/slot/meta-info.json"))
        assertFalse(fs.exists("$ROOT/slot.previous"))
    }

    @Test
    fun `failed fallback move is visible to the caller`() {
        val fs = MemoryFs()
        fs.dir("$ROOT/slot")
        fs.file("$ROOT/slot/instance.json", instanceJson)
        fs.file("$ROOT/slot.previous/meta-info.json", manifestJson)
        fs.file("$ROOT/slot.previous/instance.json", instanceJson)
        fs.allowMove = false
        val catalog = InstanceCatalog(fs, "/pkg") { fs.read(it) }

        assertFalse(catalog.reconcile("slot"))
        assertTrue(fs.exists("$ROOT/slot.previous/meta-info.json"))
    }

    @Test
    fun `unreadable staging manifest is kept`() {
        val fs = MemoryFs()
        fs.file("$ROOT/slot.committing/meta-info.json", manifestJson)
        fs.file("$ROOT/slot.committing/instance.json", instanceJson)
        val catalog = InstanceCatalog(fs, "/pkg") { path ->
            if (path == "$ROOT/slot.committing/meta-info.json") throw IllegalStateException("read failed")
            fs.read(path)
        }

        assertFalse(catalog.reconcile("slot"))
        assertTrue(fs.exists("$ROOT/slot.committing/meta-info.json"))
    }

    @Test
    fun `unreadable fallback candidate keeps the published slot`() {
        val fs = MemoryFs()
        fs.dir("$ROOT/slot")
        fs.file("$ROOT/slot/instance.json", instanceJson)
        fs.file("$ROOT/slot.previous/meta-info.json", manifestJson)
        fs.file("$ROOT/slot.previous/instance.json", instanceJson)
        val catalog = InstanceCatalog(fs, "/pkg") { path ->
            if (path == "$ROOT/slot.previous/meta-info.json") throw IllegalStateException("read failed")
            fs.read(path)
        }

        assertEquals(ReconcileResult.CleanupPending, catalog.reconcileDetailed("slot"))
        assertTrue(fs.exists("$ROOT/slot"))
        assertTrue(fs.exists("$ROOT/slot.previous/meta-info.json"))
    }

    @Test
    fun `deleting previous after publish is cleanup debt`() {
        val fs = MemoryFs()
        fs.file("$ROOT/slot.committing/meta-info.json", manifestJson)
        fs.file("$ROOT/slot.committing/instance.json", instanceJson)
        fs.file("$ROOT/slot/meta-info.json", manifestJson)
        fs.file("$ROOT/slot/instance.json", instanceJson)
        fs.file("$ROOT/slot.previous/meta-info.json", manifestJson)
        fs.file("$ROOT/slot.previous/instance.json", instanceJson)
        var previousDeletes = 0
        fs.rejectDelete = { path ->
            if (path == "$ROOT/slot.previous") {
                previousDeletes += 1
                previousDeletes > 1
            } else {
                false
            }
        }
        val catalog = InstanceCatalog(fs, "/pkg") { fs.read(it) }

        assertTrue(catalog.publishCommit("slot"))
        assertTrue(fs.exists("$ROOT/slot/meta-info.json"))
        assertTrue(fs.exists("$ROOT/slot.previous/meta-info.json"))
        val unreadable = InstanceCatalog(fs, "/pkg") { path ->
            if (path == "$ROOT/slot/meta-info.json") throw IllegalStateException("read failed")
            fs.read(path)
        }
        assertEquals(ReconcileResult.CleanupPending, unreadable.reconcileDetailed("slot"))
        assertTrue(fs.exists("$ROOT/slot/meta-info.json"))
        assertTrue(fs.exists("$ROOT/slot.previous/meta-info.json"))
    }

    private companion object {
        const val ROOT = "/pkg/instances"
    }
}

private class MemoryFs : IFileSystem {
    private class Node(val directory: Boolean, var content: String? = null) {
        val children = linkedMapOf<String, Node>()
    }

    private val root = Node(directory = true)
    var allowMove = true
    var rejectDelete: (String) -> Boolean = { false }

    fun dir(path: String) {
        ensureDir(path)
    }

    fun file(path: String, content: String) {
        ensureDir(parent(path)).children[leaf(path)] = Node(directory = false, content = content)
    }

    fun read(path: String): String? = find(path)?.content

    private fun segments(path: String) = path.split('/').filter { it.isNotEmpty() }
    private fun leaf(path: String) = segments(path).last()
    private fun parent(path: String): String {
        val parent = segments(path).dropLast(1)
        return if (parent.isEmpty()) "/" else "/" + parent.joinToString("/")
    }

    private fun ensureDir(path: String): Node {
        var current = root
        for (segment in segments(path)) {
            current = current.children.getOrPut(segment) { Node(directory = true) }
        }
        return current
    }

    private fun find(path: String): Node? {
        var current = root
        for (segment in segments(path)) {
            current = current.children[segment] ?: return null
        }
        return current
    }

    private fun detach(path: String): Node? {
        val parent = find(parent(path)) ?: return null
        return parent.children.remove(leaf(path))
    }

    override fun fileType(path: String): Int {
        val node = find(path) ?: return IFileType.TYPE_NONE
        return if (node.directory) IFileType.TYPE_DIR else IFileType.TYPE_FILE
    }

    override fun listDir(path: String): MutableList<String> =
        find(path)?.children?.keys?.toMutableList() ?: mutableListOf()

    override fun calculateSize(path: String) = 0L
    override fun mkdirs(path: String): Boolean {
        ensureDir(path)
        return true
    }

    override fun delete(path: String): Boolean {
        if (rejectDelete(path)) return false
        return detach(path) != null
    }

    override fun exists(path: String) = find(path) != null
    override fun getParent(path: String) = parent(path)
    override fun length(path: String) = 0L
    override fun getLastModifiedTime(path: String) = 0L
    override fun setLastModifiedTime(path: String, time: Long) = true
    override fun md5(file: String) = ""
    override fun getUid(path: String) = 0
    override fun setUid(path: String, uid: Int) = true
    override fun getGid(path: String) = 0
    override fun setGid(path: String, gid: Int) = true
    override fun openFile(path: String, mode: Int): ParcelFileDescriptor? = null
    override fun openInputStream(path: String): ParcelFileDescriptor? = null
    override fun openOutputStream(path: String): ParcelFileDescriptor? = null
    override fun createTempFile(prefix: String, suffix: String) = ""
    override fun createTarArchive(
        sourceDir: String,
        targetFile: String,
        excludes: MutableList<String>,
        excludeFiles: MutableList<String>,
        stdErr: String,
        stdOut: String,
    ) {
    }

    override fun createTarArchiveForMultiple(
        files: MutableList<String>,
        targetFile: String,
        stdErr: String,
        stdOut: String,
    ) {
    }

    override fun getCompressor(): IFileCompressor? = null
    override fun mkfifo(path: String, mode: Int) = false
    override fun isFifo(path: String) = false
    override fun extractTar(tarFifo: String, targetDir: String) = false
    override fun extractTarFromFd(input: ParcelFileDescriptor, targetDir: String) = false
    override fun cleanDir(path: String) = false

    override fun move(sourcePath: String, targetPath: String): Boolean {
        if (!allowMove) return false
        val node = detach(sourcePath) ?: return false
        val destination = find(parent(targetPath))
        if (destination == null) {
            ensureDir(parent(sourcePath)).children[leaf(sourcePath)] = node
            return false
        }
        destination.children[leaf(targetPath)] = node
        return true
    }

    override fun copyRecursively(source: String, target: String, overwrite: Boolean): Boolean = false
}
