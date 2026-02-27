package tiiehenry.android.app.snapshotor.group

import android.util.Log
import com.tencent.mmkv.MMKV
import tiiehenry.android.snapshotor.fs.IFileType
import tiiehenry.android.snapshotor.app.IAppManager
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.app.snapshotor.app.AppInfo
import tiiehenry.android.app.snapshotor.config.GroupConfig
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

data class SnapGroup(
    val id: String,
) {
    val config = GroupConfig(id)
    val mmkv: MMKV get() = config.getMMKV()

    var name: String
        get() {
            return mmkv.decodeString("name", id) ?: id
        }
        set(value) {
            Log.i("SnapGroup", "name: $value")
            mmkv.encode("name", value)
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
        fs: IFileSystem,
        appManager: IAppManager,
        reload: Boolean
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
                    android.util.Log.d("SnapGroup", "Creating SnapedApp for $pkgName, iconFile: $iconFile, exists: ${fs.fileType(iconFile) != 0}")
                    val app = SnapedApp(packageDir, iconFile)
                    try {
                        val archives = app.loadArchives(fs, appManager, false)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        val newAppInfo = app.latestArchive?.appInfo ?: AppInfo.from(
                            appManager.getPackageInfo(
                                pkgName,
                                0,
                                0
                            )
                        )
                        // 先设置存档图标文件路径，再赋值给 app.appInfo
                        newAppInfo.archiveIconFile = iconFile
                        android.util.Log.d("SnapGroup", "Setting archiveIconFile for $pkgName: $iconFile")
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