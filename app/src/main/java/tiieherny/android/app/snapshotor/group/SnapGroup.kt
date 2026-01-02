package tiieherny.android.app.snapshotor.group

import android.os.Environment
import com.tencent.mmkv.MMKV
import tiiehenry.android.shapshotor.fs.FileSystemFile
import tiiehenry.android.shapshotor.app.IAppManager
import tiiehenry.android.shapshotor.file.IFileSystem
import tiieherny.android.app.snapshotor.app.AppInfo
import tiieherny.android.app.snapshotor.config.GlobalConfig
import java.nio.file.Paths

data class SnapGroup(
    val id: String,
    val mmkv: MMKV,
) {

    val name by lazy {
        mmkv.decodeString("name", id) ?: id
    }
    val path by lazy {
        mmkv.decodeString("path", GlobalConfig.rootPath) ?: GlobalConfig.rootPath
    }
    val apps: MutableList<SnapedApp> = mutableListOf()

    fun loadApps(
        fileSystem: IFileSystem,
        appManager: IAppManager,
        reload: Boolean
    ): MutableList<SnapedApp> {
        synchronized(apps) {
            if (apps.isNotEmpty() && !reload) {
                return apps
            }
            val files = fileSystem.listDir(path)
            for (string in files) {
                val paths = Paths.get(path, string)
                val fileType = fileSystem.fileType(paths.toString())
                if (fileType == FileSystemFile.FILE_TYPE_DIR) {
                    fileSystem.fileType(Paths.get(path,string,"meta-info"))
                    val appInfo= AppInfo(appManager,)
                    val app = SnapedApp(string, mmkv)
                    apps.add(app)
                }
            }
            return apps
        }
    }
}