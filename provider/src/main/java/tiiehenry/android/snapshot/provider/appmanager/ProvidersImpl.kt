package tiiehenry.android.snapshot.provider.appmanager

import android.content.Context
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.provider.Providers
import tiiehenry.android.snapshot.provider.service.SnapShotRootServiceClient
import tiiehenry.android.snapshot.provider.filesystem.FileSystemProviderImpl

/**
 * 统一的 Provider 管理实现类
 * 负责 SnapShotRootServiceClient 的初始化，并提供 IAppManager 和 IFileSystem 的访问
 */
class ProvidersImpl(
    private val context: Context
) : Providers {

    private val serviceClient = SnapShotRootServiceClient.getInstance()

    // 使用 lazy 延迟创建 Provider 实例，共享同一个 serviceClient
    private val appManagerProvider by lazy { AppManagerProviderImpl(context, serviceClient) }
    private val fileSystemProvider by lazy { FileSystemProviderImpl(context, serviceClient) }

    private var _appManager: IAppManager? = null
    private var _fileSystem: IFileSystem? = null

    override fun bindRootService() {
        // 统一初始化 SnapShotRootServiceClient（只调用一次）
        serviceClient.fetchRemote(context)

        // 初始化 FileSystemProvider（它会初始化自己的 FileSystemManagerRootService）
        fileSystemProvider.onInstall()
    }

    @Throws(Exception::class)
    override fun getAppManager(): IAppManager {
        _appManager?.let { return it }
        val appManager = appManagerProvider.provide()
        _appManager = appManager
        return appManager
    }

    @Throws(Exception::class)
    override fun getFileSystem(): IFileSystem {
        _fileSystem?.let { return it }
        val fileSystem = fileSystemProvider.provide()
        _fileSystem = fileSystem
        return fileSystem
    }

    override fun waitForConnection(): Boolean {
        return try {
            serviceClient.waitFetch(context) != null
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
