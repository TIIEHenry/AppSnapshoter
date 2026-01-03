package tiieherny.android.app.snapshotor.group

import android.util.Log
import com.tencent.mmkv.MMKV
import tiiehenry.android.shapshotor.fs.IFileType
import tiiehenry.android.shapshotor.app.IAppManager
import tiiehenry.android.shapshotor.file.IFileSystem
import tiieherny.android.app.snapshotor.app.AppInfo
import tiieherny.android.app.snapshotor.config.GlobalConfig
import tiieherny.android.app.snapshotor.config.GroupConfig
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
            return mmkv.decodeString("path", GlobalConfig.rootPath) ?: GlobalConfig.rootPath
        }
        set(value) {
            mmkv.encode("path", value)
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
            for (pkgName in files) {
                val packageDir = Paths.get(path, pkgName).absolutePathString()
                val fileType = fs.fileType(packageDir)
                if (fileType == IFileType.TYPE_DIR) {
                    val iconFile = Paths.get(path, pkgName + ".png").absolutePathString()
                    val app = SnapedApp(packageDir, iconFile)
                    try {
                        val archives = app.loadArchives(fs, appManager, false)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        app.appInfo = app.latestArchive?.appInfo ?: AppInfo.from(
                            appManager.getPackageInfo(
                                pkgName,
                                0,
                                0
                            )
                        )
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