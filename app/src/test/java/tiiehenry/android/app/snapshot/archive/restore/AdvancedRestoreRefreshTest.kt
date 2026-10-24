package tiiehenry.android.app.snapshot.archive.restore

import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.Executors

class AdvancedRestoreRefreshTest {
    @Test fun `success and failure both refresh on the main dispatcher and not on the io dispatcher`() = runBlocking {
        val ioExecutor = Executors.newSingleThreadExecutor { Thread(it, "archive-io") }
        val mainExecutor = Executors.newSingleThreadExecutor { Thread(it, "archive-main") }
        val io = ioExecutor.asCoroutineDispatcher()
        val main = mainExecutor.asCoroutineDispatcher()
        try {
            suspend fun attempt(fail: Boolean): List<String> {
                val events = mutableListOf<String>()
                withContext(io) {
                    finishAdvancedRestore(
                        restore = {
                            events += "work:${Thread.currentThread().name}"
                            if (fail) error("restore failed")
                        },
                        onFailure = { events += "fail:${Thread.currentThread().name}" },
                        updateCurrent = { events += "refresh:${Thread.currentThread().name}" },
                        mainDispatcher = main,
                    )
                }
                return events
            }
            assertEquals(listOf("archive-io", "archive-main"), threadNames(attempt(false)))
            assertEquals(
                listOf("archive-io", "archive-io", "archive-main"),
                threadNames(attempt(true)),
            )
        } finally {
            ioExecutor.shutdownNow()
            mainExecutor.shutdownNow()
        }
    }

    @Test fun `restoreAdvanced finally updates the current row on the main dispatcher`() {
        val body = restoreAdvancedSource()
        val tryAt = body.indexOf("try {")
        val catchAt = body.indexOf("catch (e: Exception)")
        val finallyAt = body.lastIndexOf("finally {")
        assertTrue(tryAt >= 0 && catchAt > tryAt && finallyAt > catchAt)
        val finallyBlock = body.substring(finallyAt)
        assertTrue(finallyBlock.contains("withContext(Dispatchers.Main) { updateCurrent() }"))
        assertFalse(Regex("""finally \{\s*updateCurrent\(\)\s*}""").containsMatchIn(body))
    }

    private fun threadNames(events: List<String>): List<String> =
        events.map { it.substringAfter(':').substringBefore(" @") }

    private fun restoreAdvancedSource(): String {
        val text = restorerSource()
        val start = text.indexOf("fun restoreAdvanced(")
        val end = text.indexOf("fun restoreLatest(")
        check(start >= 0 && end > start) { "restoreAdvanced not found" }
        return text.substring(start, end)
    }

    private fun restorerSource(): String {
        val relative = "src/main/java/tiiehenry/android/app/snapshot/archive/restore/ArchiveRestorer.kt"
        var dir = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            val moduleFile = File(dir, relative)
            if (moduleFile.isFile) return moduleFile.readText()
            val repoFile = File(dir, "app/$relative")
            if (repoFile.isFile) return repoFile.readText()
            dir = dir.parentFile ?: return@repeat
        }
        error("ArchiveRestorer.kt not found from ${File(System.getProperty("user.dir")).absolutePath}")
    }
}
