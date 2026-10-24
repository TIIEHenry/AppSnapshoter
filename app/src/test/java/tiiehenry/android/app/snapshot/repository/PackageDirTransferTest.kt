package tiiehenry.android.app.snapshot.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tiiehenry.android.app.snapshot.group.MoveAppResult
import tiiehenry.android.snapshot.file.IFileCompressor
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.IFileType
import kotlinx.coroutines.runBlocking

class PackageDirTransferTest {

    @Test
    fun `foreign file or different length is target non empty and keeps the target`() = runBlocking {
        val foreign = memoryFs()
        foreign.file("/src/pkg/data", 1)
        foreign.file("/dst/pkg/data", 1)
        foreign.file("/dst/pkg/extra", 2)
        var commits = 0
        val foreignResult = transferPackageDirectory(foreign, "/src/pkg", "/dst/pkg") { commits++ }

        assertEquals(MoveAppResult.TargetNonEmpty, foreignResult)
        assertEquals(0, commits)
        assertTrue(foreign.events.isEmpty())
        assertEquals(2L, foreign.length("/dst/pkg/extra"))
        assertTrue(foreign.exists("/src/pkg/data"))

        val differentLength = memoryFs()
        differentLength.file("/src/pkg/data", 1)
        differentLength.file("/dst/pkg/data", 9)
        val lengthResult = transferPackageDirectory(differentLength, "/src/pkg", "/dst/pkg") { commits++ }

        assertEquals(MoveAppResult.TargetNonEmpty, lengthResult)
        assertEquals(0, commits)
        assertTrue(differentLength.events.isEmpty())
        assertEquals(9L, differentLength.length("/dst/pkg/data"))
        assertTrue(differentLength.exists("/src/pkg/data"))
    }

    @Test
    fun `empty or strict subset deletes the target then moves`() = runBlocking {
        val emptyTarget = memoryFs()
        emptyTarget.file("/src/pkg/data", 1)
        emptyTarget.dir("/dst/pkg")
        var commits = 0
        val emptyResult = transferPackageDirectory(emptyTarget, "/src/pkg", "/dst/pkg") { commits++ }

        assertEquals(MoveAppResult.Moved, emptyResult)
        assertEquals(1, commits)
        assertEquals(
            listOf("delete:/dst/pkg", "move:/src/pkg->/dst/pkg"),
            emptyTarget.events,
        )
        assertFalse(emptyTarget.exists("/src/pkg"))
        assertEquals(1L, emptyTarget.length("/dst/pkg/data"))

        val subset = memoryFs()
        subset.file("/src/pkg/data", 1)
        subset.file("/src/pkg/more", 2)
        subset.file("/dst/pkg/data", 1)
        val subsetResult = transferPackageDirectory(subset, "/src/pkg", "/dst/pkg") { commits++ }

        assertEquals(MoveAppResult.Moved, subsetResult)
        assertEquals(2, commits)
        assertEquals(
            listOf("delete:/dst/pkg", "move:/src/pkg->/dst/pkg"),
            subset.events,
        )
        assertFalse(subset.exists("/src/pkg"))
        assertEquals(1L, subset.length("/dst/pkg/data"))
        assertEquals(2L, subset.length("/dst/pkg/more"))
    }

    @Test
    fun `copied source delete failure keeps target without metadata and retry completes`() = runBlocking {
        val fs = memoryFs()
        fs.allowMove = false
        fs.dir("/dst")
        fs.file("/src/pkg/data", 4)
        var sourceDeletes = 0
        fs.rejectDelete = { path ->
            if (path != "/src/pkg") {
                false
            } else {
                sourceDeletes += 1
                sourceDeletes == 1
            }
        }
        var commits = 0

        val first = transferPackageDirectory(fs, "/src/pkg", "/dst/pkg") { commits++ }

        assertTrue(first is MoveAppResult.Error)
        assertEquals(0, commits)
        assertTrue(fs.exists("/src/pkg/data"))
        assertEquals(4L, fs.length("/dst/pkg/data"))
        val movesAfterCopy = fs.events.count { it.startsWith("move:") }

        val second = transferPackageDirectory(fs, "/src/pkg", "/dst/pkg") { commits++ }

        assertEquals(MoveAppResult.Moved, second)
        assertEquals(1, commits)
        assertFalse(fs.exists("/src/pkg"))
        assertEquals(4L, fs.length("/dst/pkg/data"))
        assertEquals(movesAfterCopy, fs.events.count { it.startsWith("move:") })
    }

    @Test
    fun `failed residual delete returns error without move`() = runBlocking {
        val residual = memoryFs()
        residual.file("/src/pkg/data", 1)
        residual.file("/src/pkg/more", 2)
        residual.file("/dst/pkg/data", 1)
        residual.rejectDelete = { it == "/dst/pkg" }
        var commits = 0
        val residualResult = transferPackageDirectory(residual, "/src/pkg", "/dst/pkg") { commits++ }

        assertTrue(residualResult is MoveAppResult.Error)
        assertEquals(0, commits)
        assertFalse(residual.events.any { it.startsWith("move:") })
        assertEquals(1L, residual.length("/dst/pkg/data"))
        assertTrue(residual.exists("/src/pkg/more"))

        val emptyTarget = memoryFs()
        emptyTarget.file("/src/pkg/data", 1)
        emptyTarget.dir("/dst/pkg")
        emptyTarget.rejectDelete = { it == "/dst/pkg" }
        val emptyResult = transferPackageDirectory(emptyTarget, "/src/pkg", "/dst/pkg") { commits++ }

        assertTrue(emptyResult is MoveAppResult.Error)
        assertEquals(0, commits)
        assertFalse(emptyTarget.events.any { it.startsWith("move:") })
        assertTrue(emptyTarget.exists("/dst/pkg"))
        assertTrue(emptyTarget.exists("/src/pkg/data"))
    }
}

private fun memoryFs(): MemoryFs = MemoryFs()

private class MemoryFs : IFileSystem {
    private class Node(
        val directory: Boolean,
        var length: Long = 0,
        val children: MutableMap<String, Node> = linkedMapOf(),
    )

    private class ParentRef(val node: Node, val name: String)

    private val root = Node(directory = true)
    val events = mutableListOf<String>()
    var allowMove = true
    var allowCopy = true
    var rejectDelete: (String) -> Boolean = { false }

    fun dir(path: String) {
        ensureDir(path)
    }

    fun file(path: String, length: Long) {
        val parent = ensureDir(parentPath(path))
        parent.children[leaf(path)] = Node(directory = false, length = length)
    }

    private fun segments(path: String) = path.split('/').filter { it.isNotEmpty() }

    private fun leaf(path: String) = segments(path).last()

    private fun parentPath(path: String): String {
        val parent = segments(path).dropLast(1)
        return if (parent.isEmpty()) "/" else "/" + parent.joinToString("/")
    }

    private fun ensureDir(path: String): Node {
        var current = root
        for (seg in segments(path)) {
            val existing = current.children[seg]
            if (existing != null && existing.directory) {
                current = existing
            } else {
                val created = Node(directory = true)
                current.children[seg] = created
                current = created
            }
        }
        return current
    }

    private fun find(path: String): Node? {
        var current = root
        for (seg in segments(path)) {
            current = current.children[seg] ?: return null
        }
        return current
    }

    private fun parentOf(path: String): ParentRef? {
        val segs = segments(path)
        if (segs.isEmpty()) return null
        var current = root
        for (seg in segs.dropLast(1)) {
            val child = current.children[seg] ?: return null
            if (!child.directory) return null
            current = child
        }
        return ParentRef(current, segs.last())
    }

    private fun copyNode(node: Node): Node {
        val copied = Node(directory = node.directory, length = node.length)
        for ((name, child) in node.children) {
            copied.children[name] = copyNode(child)
        }
        return copied
    }

    override fun fileType(path: String): Int {
        val node = find(path) ?: return IFileType.TYPE_NONE
        return if (node.directory) IFileType.TYPE_DIR else IFileType.TYPE_FILE
    }

    override fun listDir(path: String): MutableList<String> {
        val node = find(path) ?: return mutableListOf()
        if (!node.directory) return mutableListOf()
        return node.children.keys.toMutableList()
    }

    override fun calculateSize(path: String) = 0L
    override fun mkdirs(path: String) = false

    override fun delete(path: String): Boolean {
        events += "delete:$path"
        if (rejectDelete(path)) return false
        val parent = parentOf(path) ?: return false
        return parent.node.children.remove(parent.name) != null
    }

    override fun exists(path: String) = find(path) != null
    override fun getParent(path: String) = parentPath(path)
    override fun length(path: String) = find(path)?.length ?: 0L
    override fun getLastModifiedTime(path: String) = 0L
    override fun setLastModifiedTime(path: String, time: Long) = true
    override fun md5(file: String) = ""
    override fun getUid(path: String) = 0
    override fun setUid(path: String, uid: Int) = true
    override fun getGid(path: String) = 0
    override fun setGid(path: String, gid: Int) = true
    override fun openFile(path: String, mode: Int) = null
    override fun openInputStream(path: String) = null
    override fun openOutputStream(path: String) = null
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
    override fun extractTarFromFd(input: android.os.ParcelFileDescriptor, targetDir: String) = false
    override fun cleanDir(path: String) = false

    override fun move(sourcePath: String, targetPath: String): Boolean {
        events += "move:$sourcePath->$targetPath"
        if (!allowMove) return false
        val src = parentOf(sourcePath) ?: return false
        val node = src.node.children.remove(src.name) ?: return false
        val dst = parentOf(targetPath)
        if (dst == null) {
            src.node.children[src.name] = node
            return false
        }
        dst.node.children[dst.name] = node
        return true
    }

    override fun copyRecursively(source: String, target: String, overwrite: Boolean): Boolean {
        events += "copy:$source->$target"
        if (!allowCopy) return false
        val node = find(source) ?: return false
        val dst = parentOf(target) ?: return false
        if (dst.node.children.containsKey(dst.name) && !overwrite) return false
        dst.node.children[dst.name] = copyNode(node)
        return true
    }
}
