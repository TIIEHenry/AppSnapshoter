package tiiehenry.android.snapshot.provider.filesystem

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.io.Closeable
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Both ends are open before either stage runs, so a failed stage cannot strand an open(FIFO). */
internal class ReliablePipeTransfer : Closeable {
    private val descriptors = ParcelFileDescriptor.createReliablePipe()
    private val reader = descriptors[0]
    private val writer = descriptors[1]
    private val aborted = AtomicBoolean(false)

    fun abort(message: String) {
        if (aborted.compareAndSet(false, true)) {
            // Release both local ends. The remote read end is closed by the root
            // handler before it returns; a failed consumer therefore breaks a
            // blocked write, while a failed producer sends EOF/error to tar.
            runCatching { reader.closeWithError(message) }
            runCatching { writer.closeWithError(message) }
        }
    }

    suspend fun transfer(
        produce: (OutputStream) -> Unit,
        consume: (ParcelFileDescriptor) -> Unit
    ) = coroutineScope {
        try {
            awaitAll(
                async(Dispatchers.IO) {
                    try {
                        ParcelFileDescriptor.AutoCloseOutputStream(writer).use { output ->
                            try {
                                produce(output)
                                writer.checkError()
                            } catch (error: Throwable) {
                                // Publish failure before AutoCloseOutputStream
                                // performs its normal (successful) close.
                                abort(error.message ?: "Tar stream producer failed")
                                throw error
                            }
                        }
                    } catch (error: Throwable) {
                        abort(error.message ?: "Tar stream producer failed")
                        throw error
                    }
                },
                async(Dispatchers.IO) {
                    try {
                        consume(reader)
                    } catch (error: Throwable) {
                        abort(error.message ?: "Tar stream consumer failed")
                        throw error
                    } finally {
                        runCatching { reader.close() }
                    }
                }
            )
        } finally {
            close()
        }
    }

    override fun close() {
        runCatching { reader.close() }
        runCatching { writer.close() }
    }
}
