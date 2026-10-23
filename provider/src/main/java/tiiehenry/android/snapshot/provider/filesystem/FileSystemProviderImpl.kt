package tiiehenry.android.snapshot.provider.filesystem

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.provider.FileSystemProvider
import tiiehenry.android.snapshot.provider.filesystem.root.fsm.FileSystemManagerRootService
import tiiehenry.android.snapshot.provider.service.ISnapShotRootService
import tiiehenry.android.snapshot.provider.service.SnapShotRootServiceClient
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class FileSystemProviderImpl(
    context: Context,
    private val serviceClient: SnapShotRootServiceClient
) : FileSystemProvider(context) {
    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private var future = CompletableFuture<IBinder>()
    private var connection: ServiceConnection? = null
    private var binder: IBinder? = null
    private var manager: FileSystemManager? = null
    private var deathRecipient: IBinder.DeathRecipient? = null
    private var rootBinding: CompletableFuture<Unit>? = null

    // Previously created domain objects may retain this wrapper. Resolve each
    // service lazily so they also recover after the underlying Binder dies.
    private val fileSystem by lazy { FileSystemImpl(::awaitRootService, ::awaitManager, context) }

    override fun onInstall() {
        val bind = synchronized(lock) {
            if (binder?.isBinderAlive == true || connection != null) return
            if (future.isDone) future = CompletableFuture()
            val pending = future
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) {
                    synchronized(lock) {
                        if (connection !== this) return
                        val death = IBinder.DeathRecipient { disconnected(this, service) }
                        try {
                            service.linkToDeath(death, 0)
                            binder = service
                            manager = FileSystemManager.getRemote(service)
                            deathRecipient = death
                            pending.complete(service)
                        } catch (error: Exception) {
                            pending.completeExceptionally(error)
                            disconnected(this, service)
                        }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName) = disconnected(this, null)
                override fun onBindingDied(name: ComponentName) = disconnected(this, null)
                override fun onNullBinding(name: ComponentName) = disconnected(this, null)
            }.also { connection = it }
        }
        main.post {
            if (synchronized(lock) { connection !== bind }) return@post
            try {
                RootService.bind(Intent(context, FileSystemManagerRootService::class.java), bind)
            } catch (error: Exception) {
                synchronized(lock) { if (connection === bind) future.completeExceptionally(error) }
                disconnected(bind, null)
            }
        }
    }

    private fun disconnected(source: ServiceConnection, deadBinder: IBinder?) {
        synchronized(lock) {
            if (connection !== source || (deadBinder != null && binder != null && binder !== deadBinder)) return
            deathRecipient?.let { recipient -> runCatching { binder?.unlinkToDeath(recipient, 0) } }
            binder = null
            manager = null
            deathRecipient = null
            connection = null
            future.completeExceptionally(IllegalStateException("File service disconnected"))
            future = CompletableFuture()
        }
        // RootService expects bind/unbind on the main thread. Retry on next
        // access rather than spinning if root permission has been revoked.
        main.post { runCatching { RootService.unbind(source) } }
    }

    private fun awaitRootService(): ISnapShotRootService {
        if (!serviceClient.isConnected) {
            val started = synchronized(lock) {
                rootBinding ?: CompletableFuture<Unit>().also { pending ->
                    rootBinding = pending
                    main.post {
                        try {
                            if (!serviceClient.isConnected) serviceClient.fetchRemote(context)
                            pending.complete(Unit)
                        } catch (error: Exception) {
                            pending.completeExceptionally(error)
                        }
                    }
                }
            }
            check(Looper.myLooper() != Looper.getMainLooper()) { "Root service reconnecting" }
            try {
                started.get(10, TimeUnit.SECONDS)
                serviceClient.waitFetch(context)
            } finally {
                synchronized(lock) { if (rootBinding === started) rootBinding = null }
            }
        }
        check(serviceClient.isConnected) { "Root service not connected" }
        return checkNotNull(serviceClient.client) { "Root service not connected" }
    }

    private fun awaitManager(): FileSystemManager {
        synchronized(lock) {
            if (binder?.isBinderAlive == true) manager?.let { return it }
            // A death callback can arrive after this access. Invalidate eagerly
            // rather than returning a manager whose Binder is already dead.
            if (binder != null) connection?.let { disconnected(it, binder) }
        }
        onInstall()
        check(Looper.myLooper() != Looper.getMainLooper()) { "File service reconnecting" }
        val pending = synchronized(lock) { future }
        val service = pending.get(10, TimeUnit.SECONDS)
        return synchronized(lock) {
            check(binder === service && service.isBinderAlive) { "File service disconnected while connecting" }
            checkNotNull(manager)
        }
    }

    override fun provide(): IFileSystem {
        awaitRootService()
        awaitManager()
        return fileSystem
    }
}
