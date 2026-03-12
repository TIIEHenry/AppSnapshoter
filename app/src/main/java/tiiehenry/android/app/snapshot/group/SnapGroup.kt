package tiiehenry.android.app.snapshot.group

import android.util.Log
import tiiehenry.android.app.snapshot.app.AppInfo
import tiiehenry.android.app.snapshot.config.GroupConfig
import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.IFileType
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

data class SnapGroup(
    val id: String,
) {
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

    val apps: MutableList<SnapedApp> = mutableListOf()

    fun loadApps(
        fs: IFileSystem, appManager: IAppManager, reload: Boolean
    ): MutableList<SnapedApp> {
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
            Log.i("SnapGroup", "files: $files")
            for (pkgName in files) {
                val packageDir = Paths.get(path, pkgName).absolutePathString()
                val fileType = fs.fileType(packageDir)
                if (fileType == IFileType.TYPE_DIR) {
                    val iconFile = Paths.get(path, "$pkgName.png").absolutePathString()
                    Log.d(
                        "SnapGroup",
                        "Creating SnapedApp for $pkgName, iconFile: $iconFile, exists: ${
                            fs.fileType(iconFile) != 0
                        }"
                    )
                    val app = SnapedApp(this, packageDir, iconFile)
                    try {
                        app.loadArchives(fs, appManager, false)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        val newAppInfo = app.latestArchive?.appInfo ?: AppInfo.from(
                            appManager.getPackageInfo(
                                pkgName, 0, config.groupConfigData.userId
                            )
                        )
                        // 先设置存档图标文件路径，再赋值给 app.appInfo
                        newAppInfo.archiveIconFile = iconFile
                        Log.d("SnapGroup", "Setting archiveIconFile for $pkgName: $iconFile")
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