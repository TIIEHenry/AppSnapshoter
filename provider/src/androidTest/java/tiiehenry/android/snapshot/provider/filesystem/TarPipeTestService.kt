@file:Suppress("DEPRECATION")

package tiiehenry.android.snapshot.provider.filesystem

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.Process
import tiiehenry.android.snapshot.provider.service.handler.FileSystemHandler
import java.io.File

/** Runs only in the test APK's private process, with the test app's ordinary UID. */
class TarPipeTestService : Service() {
    private val endpoint = object : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code != EXTRACT && code != PID) return super.onTransact(code, data, reply, flags)
            data.enforceInterface(TOKEN)
            checkNotNull(reply)
            if (code == PID) {
                reply.writeNoException()
                reply.writeInt(Process.myPid())
                return true
            }
            val input = checkNotNull(data.readParcelable<ParcelFileDescriptor>(ParcelFileDescriptor::class.java.classLoader))
            val inputFd = input.fileDescriptor
            val target = File(checkNotNull(data.readString()))
            val ready = data.readStrongBinder()
            // Even an accidental malformed test request cannot extract outside
            // this test application's cache directory.
            check(target.canonicalPath.startsWith(cacheDir.canonicalPath + File.separator))
            ready?.let { callback ->
                val notification = Parcel.obtain()
                try {
                    notification.writeInterfaceToken(TOKEN)
                    callback.transact(READY, notification, null, IBinder.FLAG_ONEWAY)
                } finally {
                    notification.recycle()
                }
            }
            val success = FileSystemHandler(applicationContext).extractTarFromFd(input, target.path)
            reply.writeNoException()
            reply.writeInt(if (success) 1 else 0)
            reply.writeInt(if (!inputFd.valid()) 1 else 0)
            return true
        }
    }

    override fun onBind(intent: Intent): IBinder = endpoint

    companion object {
        const val TOKEN = "snapshot.provider.test.TarPipe"
        const val EXTRACT = IBinder.FIRST_CALL_TRANSACTION
        const val PID = IBinder.FIRST_CALL_TRANSACTION + 1
        const val READY = IBinder.FIRST_CALL_TRANSACTION + 2
    }
}
