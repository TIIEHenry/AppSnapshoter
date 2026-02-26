package tiiehenry.android.app.snapshotor.data

import tiiehenry.android.snapshotor.app.IAppManager
import tiiehenry.android.snapshotor.file.IFileSystem
import tiiehenry.android.app.snapshotor.SnapShotApp
import tiiehenry.android.app.snapshotor.app.AppInfo
import java.io.File

class SnapShotLoader {

    companion object {

        private fun calculateDirSize(dir: File): Long {
            var size = 0L
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    size += file.length()
                }
            }
            return size
        }

        fun loadAppInfo(
            fileSystem: IFileSystem,
            appManager: IAppManager,
            packageName: String,
            userId: Int
        ): AppInfo? {
            val packageInfo = appManager.getPackageInfo(packageName, 0, userId) ?: return null

            return AppInfo(
                fs = fileSystem,
                appManager = appManager,
                packageName = packageName,
                userId = userId,
                versionName = packageInfo.versionName,
                versionCode = packageInfo.longVersionCode
            )
        }

        fun loadInstalledApps(userId: Int): List<AppInfo> {
            SnapShotApp.getViewModel()
            val fileSystem = SnapShotApp.getInstance().fileSystem
            SnapShotApp.getViewModel()
            val appManager = SnapShotApp.getInstance().appManager
            val packageNames = appManager.getInstalledPackages(0, 0)

            return packageNames.mapNotNull { packageName ->
                loadAppInfo(fileSystem,appManager, packageName, userId)
            }
        }


    }
}
