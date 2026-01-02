package tiieherny.android.app.snapshotor.data

import tiieherny.android.app.snapshotor.SnapShotApp
import tiieherny.android.app.snapshotor.app.AppInfo
import tiieherny.android.app.snapshotor.archive.ArchieveItem
import tiieherny.android.app.snapshotor.config.GlobalConfig
import java.io.File

class SnapShotLoader {

    companion object {
        fun loadArchievesForPackage(packageName: String, rootPath: String = GlobalConfig.rootPath): List<ArchieveItem> {
            val packageDir = File(rootPath, packageName)
            if (!packageDir.exists() || !packageDir.isDirectory) {
                return emptyList()
            }

            val archives = mutableListOf<ArchieveItem>()
            packageDir.listFiles()?.forEach { file ->
                if (file.isDirectory && !file.name.endsWith(".png")) {
                    val metaFile = File(file, "meta-info.json")
                    if (metaFile.exists()) {
                        // TODO: 解析meta-info.json获取存档信息
                        val appInfo = loadAppInfo(packageName)
                        val archive = ArchieveItem(
                            appInfo = appInfo,
                            name = file.name,
                            path = file.absolutePath,
                            createTime = file.lastModified(),
                            size = calculateDirSize(file)
                        )
                        archives.add(archive)
                    }
                }
            }

            return archives.sortedByDescending { it.createTime }
        }

        private fun calculateDirSize(dir: File): Long {
            var size = 0L
            dir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    size += file.length()
                }
            }
            return size
        }

        fun loadAppInfo(packageName: String): AppInfo {
            val appManager = SnapShotApp.getViewModel().appManager
            val packageInfo = appManager.getPackageInfo(packageName, 0, 0)
            
            return AppInfo(
                appManager = appManager,
                packageName = packageName,
                userId = 0,
                versionName = packageInfo?.versionName,
                versionCode = if (packageInfo != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    }
                } else {
                    0L
                }
            )
        }

        fun loadInstalledApps(): List<AppInfo> {
            val appManager = SnapShotApp.getViewModel().appManager
            val packageNames = appManager.getInstalledPackages(0, 0)

            return packageNames.mapNotNull { packageName ->
                val packageInfo = appManager.getPackageInfo(packageName, 0, 0) ?: return@mapNotNull null
                val applicationInfo = appManager.getApplicationInfo(packageName, 0, 0) ?: return@mapNotNull null
                
                // 过滤系统应用
                if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0) {
                    return@mapNotNull null
                }
                
                val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }

                AppInfo(
                    appManager = appManager,
                    packageName = packageName,
                    userId = 0,
                    versionName = packageInfo.versionName,
                    versionCode = versionCode
                )
            }
        }
        

    }
}
