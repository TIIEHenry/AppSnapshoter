package tiiehenry.android.app.snapshot.archive.restore

import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.coroutineContext

class RestoreCancellationTest {
    @Test fun `cancelled restore does not overwrite the next app data entry`() = runBlocking {
        val written = mutableListOf<String>()
        val job = launch {
            beforeEachAppDataOverwrite(
                listOf("data", "user"),
                beforeOverwrite = { coroutineContext.ensureActive() },
            ) { name ->
                written += name
                if (name == "data") coroutineContext.cancel()
            }
        }
        job.join()
        assertEquals(listOf("data"), written)
        assertTrue(job.isCancelled)
    }

    @Test fun `cancelled restore does not start the first app data overwrite`() = runBlocking {
        val written = mutableListOf<String>()
        val job = launch {
            coroutineContext.cancel()
            beforeEachAppDataOverwrite(listOf("data", "user"), beforeOverwrite = { coroutineContext.ensureActive() }) {
                written += it
            }
        }
        job.join()
        assertTrue(written.isEmpty())
        assertTrue(job.isCancelled)
    }

    @Test fun `active restore overwrites app data in order`() = runBlocking {
        val written = mutableListOf<String>()
        beforeEachAppDataOverwrite(listOf("data", "user", "extra"), beforeOverwrite = { coroutineContext.ensureActive() }) {
            written += it
        }
        assertEquals(listOf("data", "user", "extra"), written)
    }
}
