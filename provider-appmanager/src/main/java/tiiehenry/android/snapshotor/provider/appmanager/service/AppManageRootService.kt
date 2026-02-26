package tiiehenry.android.snapshotor.provider.appmanager.service

import android.app.ActivityThread
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.content.pm.UserInfo
import android.os.Build
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.os.StatFs
import android.os.UserManagerHidden
import android.util.Log
import com.topjohnwu.superuser.ipc.RootService
import com.xayah.hiddenapi.castTo
import com.xayah.libnative.NativeLib
import com.xayah.libnative.TarWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import tiiehenry.android.snapshotor.provider.appmanager.App
import tiiehenry.android.snapshotor.provider.appmanager.model.AppInfo
import tiiehenry.android.snapshotor.provider.appmanager.model.AppStorage
import tiiehenry.android.snapshotor.provider.appmanager.model.Info
import tiiehenry.android.snapshotor.provider.appmanager.model.Storage
import tiiehenry.android.snapshotor.provider.appmanager.parcelables.BytesParcelable
import tiiehenry.android.snapshotor.provider.appmanager.parcelables.FilePathParcelable
import tiiehenry.android.snapshotor.provider.appmanager.parcelables.StatFsParcelable
import tiiehenry.android.snapshotor.provider.appmanager.util.LogHelper
import tiiehenry.android.snapshotor.provider.appmanager.util.PathHelper
import java.io.File
import kotlin.coroutines.resumeWithException

object AppManageRootService {
    private const val TAG = "AppManageRootService"
    private const val TIMEOUT_10S = 10000L
    private const val TIMEOUT_30S = 30000L

    private fun writeToParcel(context: Context, block: (Parcel) -> Unit): ParcelFileDescriptor {
        val parcel = Parcel.obtain()
        parcel.setDataPosition(0)
        block(parcel)
        val tmpFile = File.createTempFile(
            PathHelper.TMP_PARCEL_PREFIX,
            PathHelper.TMP_SUFFIX,
            context.cacheDir
        )
        tmpFile.delete()
        tmpFile.createNewFile()
        tmpFile.writeBytes(parcel.marshall())
        val pfd = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE)
        tmpFile.delete()
        parcel.recycle()
        return pfd
    }

    private fun readFromParcel(pfd: ParcelFileDescriptor, block: (Parcel) -> Unit) = run {
        val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
        val bytes = stream.readBytes()
        val parcel = Parcel.obtain()
        parcel.unmarshall(bytes, 0, bytes.size)
        parcel.setDataPosition(0)
        block(parcel)
        parcel.recycle()
    }

    private var mMutex = Mutex()
    private var mBinder: IBinder? = null
    private var mService: IAppManageRootService? = null
    private var mConnection: ServiceConnection? = null
    private val mServiceIntent by lazy {
        Intent().apply {
            component = ComponentName(App.application.packageName, Service::class.java.name)
        }
    }
    private var mRetries = 0
    private var mOnErrorEvent: (() -> Unit)? = null
    private var mOnNoSpaceLeftEvent: (() -> Unit)? = null

    class Service : RootService() {
        override fun onBind(intent: Intent): IBinder = Impl(applicationContext).apply { onBind() }
    }

    private class Impl(private val context: Context) : IAppManageRootService.Stub() {
        private lateinit var mSystemContext: Context
        private lateinit var mPackageManager: PackageManager
        private lateinit var mPackageManagerHidden: PackageManagerHidden
        private lateinit var mUserManager: UserManagerHidden

        fun onBind() {
            mSystemContext = ActivityThread.systemMain().systemContext
            mPackageManager = mSystemContext.packageManager
            mPackageManagerHidden = mPackageManager.castTo()
            mUserManager = UserManagerHidden.get(mSystemContext).castTo()
        }

        override fun testConnection() {}

        override fun getInstalledAppInfos(): ParcelFileDescriptor {
            return writeToParcel(context) { parcel ->
                val infos = mutableListOf<AppInfo>()
                val users = mUserManager.users
                users.forEach { user ->
                    infos.addAll(mPackageManagerHidden.getInstalledPackagesAsUser(0, user.id).map {
                        AppInfo(
                            packageName = it.packageName,
                            userId = user.id,
                            info = Info(
                                uid = it.applicationInfo?.uid ?: 0,
                                label = it.applicationInfo?.loadLabel(mPackageManager).toString(),
                                versionName = it.versionName ?: "",
                                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    it.longVersionCode
                                } else {
                                    it.versionCode.toLong()
                                },
                                flags = it.applicationInfo?.flags ?: 0,
                                firstInstallTime = it.firstInstallTime,
                                lastUpdateTime = it.lastUpdateTime
                            )
                        )
                    })
                }
                parcel.writeTypedList(infos)
            }
        }

        override fun getInstalledAppStorages(): ParcelFileDescriptor {
            return writeToParcel(context) { parcel ->
                val packages = mutableListOf<Pair<Int, PackageInfo>>()
                val storages = mutableListOf<AppStorage>()
                val users = mUserManager.users
                users.forEach { user ->
                    packages.addAll(
                        mPackageManagerHidden.getInstalledPackagesAsUser(0, user.id)
                            .map { user.id to it })
                }
                storages.addAll(packages.map { (userId, item) ->
                    val apkBytes = runCatching {
                        item.applicationInfo?.sourceDir?.let { path -> File(path).parent }
                            ?.let { path -> NativeLib.calculateTreeSize(path) }
                    }.getOrNull() ?: 0
                    val userBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppUserDir(
                            userId,
                            item.packageName
                        )
                    )
                    val userDeBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppUserDeDir(
                            userId,
                            item.packageName
                        )
                    )
                    val dataBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppDataDir(
                            userId,
                            item.packageName
                        )
                    )
                    val obbBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppObbDir(
                            userId,
                            item.packageName
                        )
                    )
                    val mediaBytes = NativeLib.calculateTreeSize(
                        PathHelper.getAppMediaDir(
                            userId,
                            item.packageName
                        )
                    )
                    AppStorage(
                        packageName = item.packageName,
                        userId = userId,
                        storage = Storage(
                            apkBytes = apkBytes,
                            internalDataBytes = userBytes + userDeBytes,
                            externalDataBytes = dataBytes,
                            additionalDataBytes = obbBytes + mediaBytes,
                        )
                    )
                })
                parcel.writeTypedList(storages)
            }
        }

        override fun getUsers(): List<UserInfo> {
            return mUserManager.users
        }

        override fun getPrivilegedConfiguredNetworks(): List<BytesParcelable> {
            return emptyList()
        }

        override fun addNetworks(configs: List<BytesParcelable>): IntArray {
            return intArrayOf()
        }

        override fun readStatFs(path: String): StatFsParcelable {
            return try {
                val statFs = StatFs(path)
                StatFsParcelable(statFs.availableBytes, statFs.totalBytes)
            } catch (e: Exception) {
                StatFsParcelable(0, 0)
            }
        }

        override fun listFilePaths(
            path: String,
            listFiles: Boolean,
            listDirs: Boolean
        ): List<FilePathParcelable> {
            return try {
                val file = File(path)
                if (!file.exists()) return emptyList()

                file.listFiles()?.filter {
                    (it.isFile && listFiles) || (it.isDirectory && listDirs)
                }?.map {
                    FilePathParcelable(it.path, if (it.isFile) 0 else if (it.isDirectory) 1 else -1)
                } ?: emptyList()
            } catch (e: Exception) {
                LogHelper.e("RemoteRootService", "listFilePaths", "Failed to list paths", e)
                emptyList()
            }
        }

        override fun readText(path: String): ParcelFileDescriptor {
            return writeToParcel(context) { parcel ->
                val text = runCatching {
                    File(path).readText()
                }.getOrNull() ?: ""
                parcel.writeString(text)
            }
        }

        override fun writeText(path: String, pfd: ParcelFileDescriptor) {
            var text = ""
            readFromParcel(pfd) { parcel -> parcel.readString()?.also { text = it } }
            val textFile = File(path)
            if (textFile.isDirectory || textFile.exists()) {
                textFile.deleteRecursively()
            }
            textFile.createNewFile()
            textFile.writeText(text)
        }

        override fun calculateTreeSize(path: String): Long {
            return NativeLib.calculateTreeSize(path)
        }

        override fun callTarCli(stdOut: String, stdErr: String, argv: Array<String>): Int {
            return TarWrapper.callCli(stdOut, stdErr, argv)
        }

        override fun getPackageSourceDir(packageName: String, userId: Int): List<String> {
            val sourceDirList = mutableListOf<String>()
            val packageInfo = mPackageManagerHidden.getPackageInfoAsUser(packageName, 0, userId)
            packageInfo.applicationInfo?.sourceDir?.also { sourceDirList.add(it) }
            val splitSourceDirs = packageInfo.applicationInfo?.splitSourceDirs
            if (!splitSourceDirs.isNullOrEmpty()) for (i in splitSourceDirs) sourceDirList.add(i)
            return sourceDirList
        }

        override fun compress(
            level: Int,
            inputPath: String,
            outputPath: String,
            callback: IAppManageRootService.ICallback?
        ): String? {
            return "Not implemented"
        }

        override fun mkdirs(path: String): Boolean {
            return runCatching {
                val file = File(path)
                if (file.exists().not()) file.mkdirs() else true
            }.getOrNull() ?: false
        }

        override fun exists(path: String): Boolean {
            return runCatching { File(path).exists() }.getOrNull() ?: false
        }

        override fun deleteRecursively(path: String): Boolean {
            return runCatching { File(path).deleteRecursively() }.getOrNull() ?: false
        }

        override fun copyRecursively(source: String, target: String, overwrite: Boolean): Boolean {
            return runCatching { File(source).copyRecursively(File(target), overwrite) }.getOrNull()
                ?: false
        }

        //工具方法
        private fun calculateDirSize(path: String): Long {
            val file = File(path)
            if (!file.exists()) return 0
            if (file.isFile) return file.length()

            return runCatching {
                file.walk().sumOf { if (it.isFile) it.length() else 0L }
            }.getOrNull() ?: 0L
        }

        private fun calculateFileSize(path: String?): Long {
            return path?.let { File(it).length() } ?: 0L
        }

        private fun writeToParcel(context: Context, block: (Parcel) -> Unit): ParcelFileDescriptor {
            val parcel = Parcel.obtain()
            parcel.setDataPosition(0)
            block(parcel)
            val tmpFile = File.createTempFile(
                PathHelper.TMP_PARCEL_PREFIX,
                PathHelper.TMP_SUFFIX,
                context.cacheDir
            )
            tmpFile.delete()
            tmpFile.createNewFile()
            tmpFile.writeBytes(parcel.marshall())
            val pfd = ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE)
            tmpFile.delete()
            parcel.recycle()
            return pfd
        }

        private fun readFromParcel(pfd: ParcelFileDescriptor, block: (Parcel) -> Unit) {
            val stream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
            val bytes = stream.readBytes()
            val parcel = Parcel.obtain()
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            block(parcel)
            parcel.recycle()
        }
    }


    private fun destroyService() {
        if (mConnection != null || mService != null || mBinder != null) {
            Log.d(TAG, "Destroy the remote root service.")
            mConnection = null
            mService = null
            mBinder = null
        }
    }

    private suspend fun bindService(): IAppManageRootService {
        return withTimeout(TIMEOUT_10S) {
            suspendCancellableCoroutine { continuation ->
                if (mService == null) {
                    mRetries++
                    destroyService()
                    LogHelper.i(TAG, "bindService", "Bind the root service, retries: $mRetries.")
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName, service: IBinder) {
                            val ipc = IAppManageRootService.Stub.asInterface(service)
                            mBinder = service
                            mService = ipc
                            val msg = "Service connected."
                            LogHelper.i(TAG, "bindService", msg)
                            if (continuation.context.isActive) continuation.resumeWith(Result.success(ipc))
                        }

                        override fun onServiceDisconnected(name: ComponentName) {
                            val msg = "Service disconnected."
                            LogHelper.w(TAG, "bindService", msg)
                            destroyService()
                            if (continuation.context.isActive) continuation.resumeWithException(
                                RemoteException(msg)
                            )
                        }

                        override fun onBindingDied(name: ComponentName) {
                            val msg = "Binding died."
                            LogHelper.e(TAG, "bindService", msg)
                            destroyService()
                            if (continuation.context.isActive) continuation.resumeWithException(
                                RemoteException(msg)
                            )
                        }

                        override fun onNullBinding(name: ComponentName) {
                            val msg = "Null binding."
                            LogHelper.e(TAG, "bindService", msg)
                            destroyService()
                            if (continuation.context.isActive) continuation.resumeWithException(
                                RemoteException(msg)
                            )
                        }
                    }
                    RootService.bind(mServiceIntent, connection)
                    mConnection = connection
                } else {
                    mRetries = 0
                    mService
                }
            }
        }
    }

    suspend fun getService(): IAppManageRootService? {
        try {
            mMutex.withLock {
                if (mBinder?.isBinderAlive == false) {
                    destroyService()
                }
                runCatching {
                    withTimeout(TIMEOUT_30S) {
                        while (mService == null) {
                            withContext(Dispatchers.Main) {
                                mService = runCatching { bindService() }.getOrNull()
                            }
                            delay(1000)
                        }
                    }
                }
                if (mService == null) {
                    val msg = "Failed to bind the root service."
                    LogHelper.e(TAG, "getService", msg)
                    mOnErrorEvent?.invoke()
                }
                mService?.testConnection()
                return mService
            }
        } catch (e: DeadObjectException) {
            getService()
            LogHelper.e(TAG, "getService", "Failed to get root service", e)
        }
        return mService
    }

    fun setOnErrorEvent(block: () -> Unit) {
        mOnErrorEvent = block
    }

    fun setOnNoSpaceLeftEvent(block: () -> Unit) {
        mOnNoSpaceLeftEvent = block
    }

    /**
     * Check ENOSPC in exception message
     */
    fun checkENOSPC(msg: String) {
        if (msg.contains("ENOSPC")) {
            // ENOSPC (No space left on device)
            mOnNoSpaceLeftEvent?.invoke()
        }
    }

    suspend fun checkService(): Boolean {
        mRetries = 0
        return getService() != null
    }

    suspend fun getInstalledAppInfos(): List<AppInfo> {
        val infos = mutableListOf<AppInfo>()
        getService()?.getInstalledAppInfos()?.also { pfd ->
            readFromParcel(pfd) {
                it.readTypedList(infos, AppInfo.CREATOR)
            }
        }
        return infos
    }

    suspend fun getInstalledAppStorages(): List<AppStorage> {
        val storages = mutableListOf<AppStorage>()
        getService()?.getInstalledAppStorages()?.also { pfd ->
            readFromParcel(pfd) {
                it.readTypedList(storages, AppStorage.CREATOR)
            }
        }
        return storages
    }

    suspend fun getUsers(): List<UserInfo> {
        return getService()?.getUsers() ?: listOf()
    }

    suspend fun readStatFs(path: String): StatFsParcelable? {
        return getService()?.readStatFs(path)
    }

    suspend fun listFilePaths(
        path: String,
        listFiles: Boolean,
        listDirs: Boolean
    ): List<FilePathParcelable> {
        return getService()?.listFilePaths(path, listFiles, listDirs) ?: listOf()
    }

    suspend fun readText(path: String): String {
        var text = ""
        getService()?.readText(path)?.also { pfd ->
            readFromParcel(pfd) { parcel ->
                parcel.readString()?.also { text = it }
            }
        }
        return text
    }

    suspend fun writeText(path: String, text: String) {
        getService()?.writeText(
            path,
            writeToParcel(App.application) { parcel ->
                parcel.writeString(text)
            }
        )
    }

    suspend fun calculateTreeSize(path: String): Long {
        return getService()?.calculateTreeSize(path) ?: 0
    }

    suspend fun callTarCli(stdOut: String, stdErr: String, argv: Array<String>): Int {
        return getService()?.callTarCli(stdOut, stdErr, argv) ?: -1
    }

    suspend fun getPackageSourceDir(packageName: String, userId: Int): List<String> {
        return getService()?.getPackageSourceDir(packageName, userId) ?: listOf()
    }

    suspend fun compress(
        level: Int,
        inputPath: String,
        outputPath: String,
        callback: IAppManageRootService.ICallback?
    ): String? {
        return getService()?.compress(level, inputPath, outputPath, callback)
    }

    suspend fun mkdirs(path: String): Boolean {
        return getService()?.mkdirs(path) ?: false
    }

    suspend fun exists(path: String): Boolean {
        return getService()?.exists(path) ?: false
    }

    suspend fun deleteRecursively(path: String): Boolean {
        return getService()?.deleteRecursively(path) ?: false
    }

    suspend fun copyRecursively(
        source: String,
        target: String,
        overwrite: Boolean = false
    ): Boolean {
        return getService()?.copyRecursively(source, target, overwrite) ?: false
    }
}