package tiiehenry.android.app.snapshot.group

import android.content.Context
import android.util.Log
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.config.GroupConfig
import tiiehenry.android.app.snapshot.utils.AppIconUtils
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.IFileType
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

data class SnapGroup(
    val id: String,
) {
    val TAG = "SnapGroup"
    val config = GroupConfig(id)
    val mmkv = config.mmkv

    var name: String
        get() {
            return config.groupConfigData.name
        }
        set(value) {
            config.groupConfigData.name = value
        }

    var userId: Int
        get() = config.groupConfigData.userId
        set(value) {
            config.groupConfigData.userId = value
        }

    var path: String
        get() {
            return config.rootPath
        }
        set(value) {
            config.rootPath = value
        }

    var isCollapsed: Boolean
        get() {
            return mmkv.decodeBool("isCollapsed", false)
        }
        set(value) {
            mmkv.encode("isCollapsed", value)
        }

    val apps: MutableList<ArchivedApp> = mutableListOf()

    fun loadApps(
        context: Context, fs: IFileSystem, appManager: IAppManager, reload: Boolean
    ): MutableList<ArchivedApp> {
        if (reload) {
            synchronized(apps) {
                apps.clear()
            }
        }
        synchronized(apps) {
            if (apps.isNotEmpty() && !reload) {
                return apps
            }
            val files = fs.listDir(path)
            Log.i(TAG, "files: $files")
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
                    Log.d(
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
                                config.groupConfigData.userId,
                                iconFile
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to load and save icon for $pkgName: ${e.message}", e)
                        }
                    }
                    val app = ArchivedApp(this, packageDir, iconFile)
                    try {
                        app.loadArchives(fs, appManager, false)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        val packageInfo = appManager.getPackageInfo(
                            pkgName, 0, config.groupConfigData.userId
                        )
                        val newAppInfo = if (packageInfo != null) {
                            app.latestArchive?.appInfo ?: AppInfo.from(packageInfo)
                        } else {
                            app.latestArchive?.appInfo ?: AppInfo(
                                fs = fs,
                                appManager = appManager,
                                packageName = pkgName,
                                userId = config.groupConfigData.userId
                            )
                        }
                        // 先设置存档图标文件路径，再赋值给 app.appInfo
                        newAppInfo.archiveIconFile = iconFile
                        Log.d(TAG, "Setting archiveIconFile for $pkgName: $iconFile")
                        app.appInfo = newAppInfo
                        apps.add(app)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
            return apps
        }
    }
}