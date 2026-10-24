package tiiehenry.android.snapshot.provider.filesystem

import android.system.Os
import android.system.OsConstants
import java.io.FileDescriptor
import java.io.FileInputStream

/**
 * Opens the read end of a FIFO without waiting for tar. Blocking reads are
 * restored afterwards so a later [FileInputStream.close] unblocks tar's write.
 */
internal fun openFifoReader(path: String): FileInputStream =
    FileInputStream(openFifoRead(path))

private fun openFifoRead(path: String): FileDescriptor {
    val descriptor = Os.open(path, OsConstants.O_RDONLY or OsConstants.O_NONBLOCK, 0)
    try {
        val flags = Os.fcntlInt(descriptor, OsConstants.F_GETFL, 0)
        Os.fcntlInt(descriptor, OsConstants.F_SETFL, flags and OsConstants.O_NONBLOCK.inv())
        return descriptor
    } catch (error: Exception) {
        runCatching { Os.close(descriptor) }
        throw error
    }
}
