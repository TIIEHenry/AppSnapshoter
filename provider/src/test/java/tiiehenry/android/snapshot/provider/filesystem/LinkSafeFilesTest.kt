package tiiehenry.android.snapshot.provider.filesystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException

class LinkSafeFilesTest {
    @Test fun `deleting a directory symlink removes the link and leaves the target tree`() {
        val ops = MemoryLinkOps()
        ops.addDir("/target")
        ops.addFile("/target/keep", "stay")
        ops.addSymlink("/link", "/target")
        val files = LinkSafeFiles(ops)

        assertTrue(files.deleteRecursively("/link"))

        assertEquals(FsKind.MISSING, ops.status("/link").kind)
        assertEquals("stay", ops.text("/target/keep"))
    }

    @Test fun `writeText replaces a symlink and leaves the target tree`() {
        val ops = MemoryLinkOps()
        ops.addDir("/target")
        ops.addFile("/target/keep", "stay")
        ops.addSymlink("/link", "/target")
        val files = LinkSafeFiles(ops)

        assertTrue(files.writeText("/link", "hello"))

        assertEquals(FsKind.FILE, ops.status("/link").kind)
        assertEquals("hello", ops.text("/link"))
        assertEquals("stay", ops.text("/target/keep"))
    }

    @Test fun `copy onto a directory symlink writes the caller path and keeps the target`() {
        val ops = MemoryLinkOps()
        ops.addDir("/target")
        ops.addFile("/target/keep", "stay")
        ops.addFile("/src", "bytes")
        ops.addSymlink("/dest", "/target")
        val files = LinkSafeFiles(ops)

        assertTrue(files.copyRecursively("/src", "/dest", overwrite = true))

        assertEquals(FsKind.FILE, ops.status("/dest").kind)
        assertEquals("bytes", ops.text("/dest"))
        assertEquals("stay", ops.text("/target/keep"))
    }

    @Test fun `copy without overwrite leaves a directory symlink alone`() {
        val ops = MemoryLinkOps()
        ops.addDir("/target")
        ops.addFile("/target/keep", "stay")
        ops.addFile("/src", "bytes")
        ops.addSymlink("/dest", "/target")
        val files = LinkSafeFiles(ops)

        assertFalse(files.copyRecursively("/src", "/dest", overwrite = false))

        assertEquals(FsKind.SYMLINK, ops.status("/dest").kind)
        assertEquals("/target", ops.link("/dest"))
        assertEquals("stay", ops.text("/target/keep"))
    }

    @Test fun `copying a symlink creates another link and does not duplicate the target`() {
        val ops = MemoryLinkOps()
        ops.addDir("/target")
        ops.addFile("/target/keep", "stay")
        ops.addSymlink("/src", "/target")
        val files = LinkSafeFiles(ops)

        assertTrue(files.copyRecursively("/src", "/dst", overwrite = false))

        assertEquals(FsKind.SYMLINK, ops.status("/dst").kind)
        assertEquals("/target", ops.link("/dst"))
        assertEquals(FsKind.MISSING, ops.status("/dst/keep").kind)
        assertEquals("stay", ops.text("/target/keep"))
    }

    @Test fun `setUid changes the link owner and not the target owner`() {
        val ops = MemoryLinkOps()
        ops.addFile("/target/file", "x", uid = 1)
        ops.addDir("/target")
        ops.addSymlink("/link", "/target/file", uid = 2)
        val files = LinkSafeFiles(ops)

        assertTrue(files.setUid("/link", 9))

        assertEquals(9, ops.status("/link").uid)
        assertEquals(1, ops.status("/target/file").uid)
    }

    @Test fun `setGid changes the link group and not the target group`() {
        val ops = MemoryLinkOps()
        ops.addFile("/target/file", "x", gid = 3)
        ops.addSymlink("/link", "/target/file", gid = 4)
        val files = LinkSafeFiles(ops)

        assertTrue(files.setGid("/link", 8))

        assertEquals(8, ops.status("/link").gid)
        assertEquals(3, ops.status("/target/file").gid)
    }

    @Test fun `deleting a real directory removes its ordinary files and nested links only`() {
        val ops = MemoryLinkOps()
        ops.addDir("/target")
        ops.addFile("/target/keep", "stay")
        ops.addDir("/dir")
        ops.addFile("/dir/a", "gone")
        ops.addSymlink("/dir/link", "/target")
        val files = LinkSafeFiles(ops)

        assertTrue(files.deleteRecursively("/dir"))

        assertEquals(FsKind.MISSING, ops.status("/dir").kind)
        assertEquals(FsKind.MISSING, ops.status("/dir/a").kind)
        assertEquals(FsKind.MISSING, ops.status("/dir/link").kind)
        assertEquals("stay", ops.text("/target/keep"))
    }

    @Test fun `ordinary files and directories still copy`() {
        val ops = MemoryLinkOps()
        ops.addDir("/src")
        ops.addFile("/src/a", "A")
        ops.addDir("/src/sub")
        ops.addFile("/src/sub/b", "B")
        ops.addSymlink("/src/link", "/elsewhere")
        ops.addFile("/elsewhere", "outside")
        val files = LinkSafeFiles(ops)

        assertTrue(files.copyRecursively("/src", "/dst", overwrite = false))

        assertEquals("A", ops.text("/dst/a"))
        assertEquals("B", ops.text("/dst/sub/b"))
        assertEquals(FsKind.SYMLINK, ops.status("/dst/link").kind)
        assertEquals("/elsewhere", ops.link("/dst/link"))
        assertEquals("outside", ops.text("/elsewhere"))
        assertEquals(FsKind.MISSING, ops.status("/dst/link-content").kind)
    }

    @Test fun `writeText replaces an ordinary file`() {
        val ops = MemoryLinkOps()
        ops.addFile("/f", "old")
        val files = LinkSafeFiles(ops)

        assertTrue(files.writeText("/f", "new"))

        assertEquals("new", ops.text("/f"))
    }
}

private class MemoryLinkOps : LinkOps {
    private class Entry(
        var kind: FsKind,
        var text: String = "",
        var link: String? = null,
        var uid: Int = 0,
        var gid: Int = 0
    )

    private val entries = mutableMapOf<String, Entry>()

    fun addDir(path: String) {
        entries[path] = Entry(FsKind.DIRECTORY)
    }

    fun addFile(path: String, text: String, uid: Int = 0, gid: Int = 0) {
        entries[path] = Entry(FsKind.FILE, text = text, uid = uid, gid = gid)
    }

    fun addSymlink(path: String, target: String, uid: Int = 0, gid: Int = 0) {
        entries[path] = Entry(FsKind.SYMLINK, link = target, uid = uid, gid = gid)
    }

    fun text(path: String): String = entries[path]?.text ?: error("missing $path")

    fun link(path: String): String = entries[path]?.link ?: error("missing $path")

    override fun status(path: String): FsStatus {
        val node = entries[path] ?: return FsStatus(FsKind.MISSING)
        return FsStatus(node.kind, node.uid, node.gid)
    }

    override fun unlink(path: String) {
        if (entries.remove(path) == null) throw NoSuchFileException(path)
    }

    override fun readLink(path: String): String =
        entries[path]?.link ?: throw NoSuchFileException(path)

    override fun symlink(target: String, path: String) {
        if (path in entries) throw FileAlreadyExistsException(path)
        entries[path] = Entry(FsKind.SYMLINK, link = target)
    }

    override fun list(path: String): List<String>? {
        val node = entries[path] ?: return null
        if (node.kind != FsKind.DIRECTORY) return null
        val prefix = if (path.endsWith("/")) path else "$path/"
        return entries.keys.mapNotNull { key ->
            if (!key.startsWith(prefix)) return@mapNotNull null
            val rest = key.removePrefix(prefix)
            if (rest.isEmpty() || '/' in rest) null else rest
        }
    }

    override fun removeEmptyDirectory(path: String) {
        val node = entries[path] ?: throw NoSuchFileException(path)
        if (node.kind != FsKind.DIRECTORY) throw IOException(path)
        if (list(path)?.isNotEmpty() == true) throw IOException(path)
        entries.remove(path)
    }

    override fun mkdirs(path: String) {
        if (entries[path]?.kind == FsKind.DIRECTORY) return
        if (path in entries) throw IOException(path)
        var current = ""
        path.split('/').filter { it.isNotEmpty() }.forEach { segment ->
            current += "/$segment"
            val existing = entries[current]
            when (existing?.kind) {
                null -> entries[current] = Entry(FsKind.DIRECTORY)
                FsKind.DIRECTORY -> Unit
                else -> throw IOException(current)
            }
        }
    }

    override fun copyFile(source: String, target: String) {
        val src = entries[source] ?: throw NoSuchFileException(source)
        if (src.kind == FsKind.SYMLINK) throw IOException(source)
        entries[target] = Entry(FsKind.FILE, text = src.text, uid = src.uid, gid = src.gid)
    }

    override fun lchown(path: String, uid: Int, gid: Int) {
        val node = entries[path] ?: throw NoSuchFileException(path)
        if (uid != -1) node.uid = uid
        if (gid != -1) node.gid = gid
    }

    override fun writeFile(path: String, text: String) {
        entries[path] = Entry(FsKind.FILE, text = text)
    }
}
