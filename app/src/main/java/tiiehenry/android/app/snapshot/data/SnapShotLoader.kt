package tiiehenry.android.app.snapshot.data

import tiiehenry.android.snapshot.app.IAppManager
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.app.AppInfo
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
            SnapshotApp.getViewModel()
            val fileSystem = SnapshotApp.getInstance().fileSystem
            SnapshotApp.getViewModel()
            val appManager = SnapshotApp.getInstance().appManager
            val packageNames = appManager.getInstalledPackages(0, 0)

            return packageNames.mapNotNull { packageName ->
                loadAppInfo(fileSystem,appManager, packageName, userId)
            }
        }


    }
}
