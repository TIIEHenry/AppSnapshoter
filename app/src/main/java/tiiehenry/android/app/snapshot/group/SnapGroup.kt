package tiiehenry.android.app.snapshot.group

import android.content.Context
import android.util.Log
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.config.GroupConfig
import tiiehenry.android.app.snapshot.utils.AppIconUtils
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.IFileType
import android.os.ParcelFileDescriptor
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

data class SnapGroup(
    val id: String,
) {
    val TAG = "SnapGroup"
    val config by lazy { GroupConfig(id) }
    val mmkv by lazy { config.mmkv }

    var name: String
        get() {
            val configName = config.groupConfigData.name
            if (!configName.isNullOrEmpty()) {
                return configName
            }
            val pathName = Paths.get(path).fileName?.toString()
            if (!pathName.isNullOrEmpty()) {
                return pathName
            }
            return id
        }
        set(value) {
            config.groupConfigData.name = value
        }

    /** JVM tests set these so load and index paths do not open MMKV. */
    internal var detachedPath: String? = null
    internal var detachedUserId: Int? = null
    internal var detachedExclusive: Boolean? = null

    var userId: Int
        get() = detachedUserId ?: config.groupConfigData.userId
        set(value) {
            detachedUserId = null
            config.groupConfigData.userId = value
        }

    var path: String
        get() = detachedPath ?: config.rootPath
        set(value) {
            detachedPath = null
            config.rootPath = value
        }

    var isCollapsed: Boolean
        get() {
            return mmkv.decodeBool("isCollapsed", false)
        }
        set(value) {
            mmkv.encode("isCollapsed", value)
        }

    val membershipMode: GroupMembershipMode
        get() = config.membershipMode

    val isExclusive: Boolean
        get() = detachedExclusive ?: config.isExclusive

    val apps: MutableList<ArchivedApp> = mutableListOf()

    private fun ensureNoMedia(fs: IFileSystem) {
        val nomediaPath = Paths.get(path, ".nomedia").absolutePathString()
        if (!fs.exists(nomediaPath)) {
            try {
                fs.openFile(
                    nomediaPath,
                    ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY
                ).use { pfd ->
                    ParcelFileDescriptor.AutoCloseOutputStream(pfd).close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create .nomedia: ${e.message}")
            }
        }
    }

    fun loadApps(
        context: Context, fs: IFileSystem, appManager: IAppManager, reload: Boolean
    ): MutableList<ArchivedApp> {
        synchronized(apps) {
            if (!reload && apps.isNotEmpty() && apps.all { it.archivesComplete }) {
                return apps
            }
            if (!reload && apps.isNotEmpty()) {
                for (app in apps) {
                    if (app.archivesComplete) continue
                    try {
                        app.loadArchives(fs, appManager, false)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        app.archivesComplete = false
                    }
                }
                return apps
            }
            // 创建 .nomedia 文件，防止图库扫描图标文件
            ensureNoMedia(fs)
            val loaded = mutableListOf<ArchivedApp>()
            val files = fs.listDir(path)
            GroupLog.i(TAG, "files: $files")
            for (pkgName in files) {
                if (pkgName.startsWith(".") || !pkgName.contains(".")) {
                    //ignore dir likes .stfolder
                    continue
                }
                val packageDir = Paths.get(path, pkgName).absolutePathString()
                val fileType = fs.fileType(packageDir)
                if (fileType == IFileType.TYPE_DIR) {
                    val iconFile = Paths.get(path, "$pkgName.png").absolutePathString()
                    val iconExists = fs.exists(iconFile)
                    GroupLog.d(
                        TAG,
                        "Creating SnapedApp for $pkgName, iconFile: $iconFile, exists: $iconExists"
                    )
                    // 如果图标文件不存在，尝试从系统加载并保存
                    if (!iconExists) {
                        try {
                            AppIconUtils.loadAndSaveAppIcon(
                                context,
                                fs,
                                appManager,
                                pkgName,
                                userId,
                                iconFile
                            )
                        } catch (e: Exception) {
                            GroupLog.e(TAG, "Failed to load and save icon for $pkgName: ${e.message}", e)
                        }
                    }
                    val app = ArchivedApp(this, packageDir, iconFile)
                    try {
                        app.loadArchives(fs, appManager, false)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        app.archivesComplete = false
                    }
                    try {
                        val packageInfo = appManager.getPackageInfo(pkgName, 0, userId)
                        val newAppInfo = if (packageInfo != null) {
                            app.latestArchive?.appInfo ?: AppInfo.from(packageInfo)
                        } else {
                            app.latestArchive?.appInfo ?: AppInfo(
                                fs = fs,
                                appManager = appManager,
                                packageName = pkgName,
                                userId = userId
                            )
                        }
                        // 先设置存档图标文件路径，再赋值给 app.appInfo
                        newAppInfo.archiveIconFile = iconFile
                        GroupLog.d(TAG, "Setting archiveIconFile for $pkgName: $iconFile")
                        app.appInfo = newAppInfo
                        loaded.add(app)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            apps.clear()
            apps.addAll(loaded)
            return apps
        }
    }
}
