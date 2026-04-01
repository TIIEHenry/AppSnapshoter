package tiiehenry.android.snapshot.provider.filesystem

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import com.topjohnwu.superuser.nio.FileSystemManager
import tiiehenry.android.snapshot.provider.filesystem.root.fsm.FileSystemManagerRootService
import tiiehenry.android.snapshot.provider.service.SnapShotRootServiceClient
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.provider.FileSystemProvider
import java.util.concurrent.CompletableFuture

class FileSystemProviderImpl(
    context: Context,
    val serviceClient: SnapShotRootServiceClient
) : FileSystemProvider(context) {

    private lateinit var fsmFuture: CompletableFuture<IBinder>

    override fun onInstall() {
        Log.i("FileSystemProvider", "Binding to FileSystemManagerRootService")
        fsmFuture = CompletableFuture<IBinder>()
        RootService.bind(
            Intent(context, FileSystemManagerRootService::class.java),
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName,
                    service: IBinder
                ) {
                    Log.i("FileSystemProvider", "Bound to FileSystemManagerRootService")
                    fsmFuture.complete(service)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    fsmFuture.completeExceptionally(Exception("Service disconnected"))
                }
            })
        // SnapShotRootServiceClient 由外部 ProvidersImpl 统一初始化
    }

    override fun provide(): IFileSystem {
        // 如果外部未提供 serviceClient，需要等待连接
        if (serviceClient.waitFetch(context) == null) {
            throw Exception("SnapShotRootService is not available")
        }
        // 如果已连接，直接使用
        if (!serviceClient.isConnected) {
            throw Exception("SnapShotRootService is not connected")
        }
        val fileSystemManager = FileSystemManager.getRemote(fsmFuture.get())
        return FileSystemImpl(serviceClient, fileSystemManager, context)
    }

}
