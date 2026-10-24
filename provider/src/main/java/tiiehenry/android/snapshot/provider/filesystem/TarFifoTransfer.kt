package tiiehenry.android.snapshot.provider.filesystem

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import tiiehenry.android.snapshot.file.IFileSystem
import java.io.InputStream
import kotlin.coroutines.coroutineContext

private const val FIFO_PREFIX = "fifo-"
private const val FIFO_SUFFIX = ".tmp"
private const val FIFO_MODE = 420

/**
 * Runs tar with stdout and stderr on FIFOs this process already has open.
 * Root opens those paths for write. Closing a read end makes tar's write fail,
 * so `callTarCli` returns. Returns false when a FIFO cannot be created; the
 * error is already reported.
 */
internal suspend fun transferTarFifo(
    fileSystem: IFileSystem,
    cancellation: TaskCancellation,
    reportError: (String) -> Unit,
    startTar: (stderrPath: String, relayPath: String) -> Unit,
    readStderr: (InputStream) -> Unit,
    consumeRelay: suspend (InputStream) -> Unit
): Boolean {
    coroutineContext[Job]?.let(cancellation::bindJob)
    val stderrPath = fileSystem.createTempFile(FIFO_PREFIX, FIFO_SUFFIX)
    fileSystem.delete(stderrPath)
    val relayPath = fileSystem.createTempFile(FIFO_PREFIX, FIFO_SUFFIX)
    fileSystem.delete(relayPath)
    var stderrIn: InputStream? = null
    var relayIn: InputStream? = null
    try {
        if (!fileSystem.mkfifo(stderrPath, FIFO_MODE)) {
            reportError("Failed to create stderr FIFO")
            return false
        }
        if (!fileSystem.mkfifo(relayPath, FIFO_MODE)) {
            reportError("Failed to create stdout FIFO")
            return false
        }
        stderrIn = openFifoReader(stderrPath)
        relayIn = openFifoReader(relayPath)
        val stderr = stderrIn
        val relay = relayIn
        fun breakPipes() {
            runCatching { stderr.close() }
            runCatching { relay.close() }
        }
        cancellation.track { breakPipes() }
        cancellation.ensureActive()
        coroutineScope {
            val tarJob = async(Dispatchers.IO) {
                try {
                    startTar(stderrPath, relayPath)
                } catch (error: Exception) {
                    breakPipes()
                    throw error
                }
            }
            val stderrJob = async(Dispatchers.IO) {
                try {
                    readStderr(stderr)
                } catch (error: Exception) {
                    breakPipes()
                    throw error
                }
            }
            val relayJob = async(Dispatchers.IO) {
                try {
                    consumeRelay(relay)
                } catch (error: Exception) {
                    breakPipes()
                    throw error
                }
            }
            awaitAll(tarJob, stderrJob, relayJob)
        }
        cancellation.ensureActive()
        return true
    } finally {
        runCatching { stderrIn?.close() }
        runCatching { relayIn?.close() }
        runCatching { fileSystem.delete(stderrPath) }
        runCatching { fileSystem.delete(relayPath) }
    }
}
