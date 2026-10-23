package tiiehenry.android.app.snapshot.archive.restore

import org.junit.Assert.*
import org.junit.Test

class ExtraFileReplacementTest {
    @Test fun `replacement changes only the exact target and preserves siblings`() {
        val files = mutableMapOf("staging/file" to "new", "parent/file" to "old", "parent/sibling" to "keep")
        replaceRestoredFile("staging/file", "parent/file", "parent/original", files::containsKey,
            { from, to -> if (from in files && to !in files) { files[to] = files.remove(from)!!; true } else false },
            { files.remove(it) != null })
        assertEquals(mapOf("parent/file" to "new", "parent/sibling" to "keep"), files)
    }

    @Test fun `failed publication restores the previous exact target`() {
        val files = mutableMapOf("staging/file" to "new", "parent/file" to "old", "parent/sibling" to "keep")
        assertThrows(IllegalStateException::class.java) {
            replaceRestoredFile("staging/file", "parent/file", "parent/original", files::containsKey,
                { from, to ->
                    if (from == "staging/file") false
                    else if (from in files && to !in files) { files[to] = files.remove(from)!!; true }
                    else false
                }, { files.remove(it) != null })
        }
        assertEquals("old", files["parent/file"])
        assertEquals("keep", files["parent/sibling"])
        assertFalse(files.containsKey("parent/original"))
    }
}
