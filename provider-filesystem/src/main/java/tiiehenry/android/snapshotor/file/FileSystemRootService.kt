@file:Suppress("DEPRECATION")

package tiiehenry.android.snapshotor.file

// Import the new service client
import android.app.ActivityThread
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageManagerHidden
import android.net.wifi.WifiManagerHidden
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.StatFs
import android.os.UserManagerHidden
import android.system.Os
import com.github.luben.zstd.ZstdOutputStream
import com.topjohnwu.superuser.ipc.RootService
import com.xayah.libnative.TarWrapper
import tiiehenry.android.snapshotor.fs.IFileType
import tiiehenry.android.snapshotor.util.PathHelper.TMP_PARCEL_PREFIX
import tiiehenry.android.snapshotor.util.PathHelper.TMP_SUFFIX
import tiiehenry.hiddenapi.castTo
import tiiehenry.libnative.NativeLib
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FileSystemRootService : RootService() {
    init {
        System.loadLibrary("nativelib")
        System.loadLibrary("tar-wrapper")
    }

    override fun onBind(intent: Intent): IBinder = Impl(applicationContext).apply { onBind() }
    private val TAG = "FileSystemRootService"


    private var mOnErrorEvent: (() -> Unit)? = null
    private var mOnNoSpaceLeftEvent: (() -> Unit)? = null

    private class Impl(private val context: Context) : IFileSystemRootService.Stub() {
        private lateinit var mSystemContext: Context
        private lateinit var mPackageManager: PackageManager
        private lateinit var mPackageManagerHidden: PackageManagerHidden
        private lateinit var mUserManager: UserManagerHidden
        private lateinit var mWifiManager: WifiManagerHidden

        private fun writeToParcel(context: Context, block: (Parcel) -> Unit): ParcelFileDescriptor {
            val parcel = Parcel.obtain()
            parcel.setDataPosition(0)
            block(parcel)
            val tmpFile = File.createTempFile(TMP_PARCEL_PREFIX, TMP_SUFFIX, context.cacheDir)
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

        fun onBind() {
            mSystemContext = ActivityThread.systemMain().systemContext
            mPackageManager = mSystemContext.packageManager
            mPackageManagerHidden = mPackageManager.castTo()
            mUserManager = UserManagerHidden.get(mSystemContext).castTo()
            mWifiManager = mSystemContext.getSystemService(Context.WIFI_SERVICE).castTo()
        }


        override fun readStatFs(path: String): StatFsParcelable {
            val statFs = StatFs(path)
            return StatFsParcelable(statFs.availableBytes, statFs.totalBytes)
        }

        override fun listFilePaths(
            path: String,
            listFiles: Boolean,
            listDirs: Boolean
        ): List<FilePathParcelable> {
            return File(path).listFiles()?.filter {
                (it.isFile && listFiles) || (it.isDirectory && listDirs)
            }?.map {
                FilePathParcelable(it.path, if (it.isFile) 0 else if (it.isDirectory) 1 else -1)
            } ?: listOf()
        }

        override fun readText(path: String): ParcelFileDescriptor {
            return writeToParcel(context) { parcel ->
                parcel.writeString(runCatching { File(path).readText() }.getOrNull() ?: "")
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
            Os.mkfifo(stdErr, 420)
            Os.mkfifo(stdOut, 420)
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
            callback: IBinaryCallback?
        ): String? {
            runCatching {
                FileInputStream(inputPath).use { fileInputStream ->
                    FileOutputStream(outputPath).use { fileOutputStream ->
                        CountingOutputStream(
                            source = fileOutputStream,
                            onProgress = if (callback != null) { bytesWritten, speed ->
                                callback.onProgress(
                                    bytesWritten,
                                    speed
                                )
                            } else null
                        ).use { countingOutputStream ->
                            ZstdOutputStream(countingOutputStream, level).use { zstdOutputStream ->
                                zstdOutputStream.setWorkers(
                                    Runtime.getRuntime().availableProcessors()
                                )
                                fileInputStream.copyTo(zstdOutputStream)
                            }
                        }
                    }
                }
            }.onFailure { return it.message }
            return null
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

        override fun fileType(path: String): Int {
            val file = File(path)
            return when {
                !file.exists() -> IFileType.TYPE_NONE
                file.isDirectory -> IFileType.TYPE_DIR
                file.isFile -> IFileType.TYPE_FILE
                else -> IFileType.TYPE_OTHER
            }
        }

        override fun deleteRecursively(path: String): Boolean {
            return runCatching { File(path).deleteRecursively() }.getOrNull() ?: false
        }

        override fun copyRecursively(source: String, target: String, overwrite: Boolean): Boolean {
            return runCatching { File(source).copyRecursively(File(target), overwrite) }.getOrNull()
                ?: false
        }

        override fun getLastModifiedTime(path: String): Long {
            return runCatching { File(path).lastModified() }.getOrNull() ?: 0L
        }

        override fun setLastModifiedTime(path: String, time: Long): Boolean {
            return runCatching { File(path).setLastModified(time) }.getOrNull() ?: false
        }


        override fun getUid(path: String): Int {
            return runCatching {
                val file = File(path)
                val stat = android.system.Os.stat(path)
                stat.st_uid
            }.getOrNull() ?: -1
        }

        override fun setUid(path: String, uid: Int): Boolean {
            return runCatching {
                android.system.Os.chown(path, uid, -1) // -1 means don't change gid
                true
            }.getOrNull() ?: false
        }

        override fun getGid(path: String): Int {
            return runCatching {
                val stat = android.system.Os.stat(path)
                stat.st_gid
            }.getOrNull() ?: -1
        }

        override fun setGid(path: String, gid: Int): Boolean {
            return runCatching {
                android.system.Os.chown(path, -1, gid) // -1 means don't change uid
                true
            }.getOrNull() ?: false
        }

        override fun openFile(path: String, mode: Int): ParcelFileDescriptor? {
            return runCatching {
                // Check if file needs to be created
                val file = File(path)
                if (mode and ParcelFileDescriptor.MODE_CREATE != 0 && !file.exists()) {
                    file.parentFile?.mkdirs()
                    file.createNewFile()
                }
                ParcelFileDescriptor.open(file, mode)
            }.getOrNull()
        }

        override fun md5(file: String): String? {
            return runCatching {
                val digest = java.security.MessageDigest.getInstance("MD5")
                java.io.FileInputStream(file).use { input ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        digest.update(buffer, 0, bytesRead)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }.getOrNull()
        }
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

}