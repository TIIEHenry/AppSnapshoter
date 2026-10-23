package tiiehenry.android.app.snapshot.archive.restore

import tiiehenry.android.app.snapshot.archive.bean.MetaDataItem
import tiiehenry.android.app.snapshot.archive.bean.MetaInfo
import tiiehenry.android.app.snapshot.config.CompressItems
import java.nio.file.Paths

internal class ArchiveValidationException(val invalidPath: String, cause: Throwable? = null) :
    Exception(invalidPath, cause)

internal data class PreparedRestore(
    val dataItems: MutableList<MetaDataItem>,
    val extraItems: Map<MetaDataItem, String>
)

/** Checks the original manifest before any application or destination is modified. */
internal object RestorePreflight {
    fun prepare(
        metaInfo: MetaInfo,
        archiveDir: String,
        packageDir: String,
        selectedTypes: Set<String>?,
        needInstallApk: Boolean,
        readItem: (String, String) -> MetaDataItem,
        verifyFile: (MetaDataItem, String) -> Boolean
    ): PreparedRestore {
        fun read(name: String, dir: String): MetaDataItem = try {
            childPath(dir, name)
            readItem(name, dir)
        } catch (e: Exception) {
            throw ArchiveValidationException("$dir/$name", e)
        }

        // Legacy manifests reference apk.json even though APK metadata is shared outside
        // the snapshot. Missing ordinary descriptors must never silently remove an item.
        val declaredItems = metaInfo.dataItems ?: throw ArchiveValidationException(archiveDir)
        val ordinaryItems = declaredItems.filter { it != "apk.json" }.map { name ->
            read(name, archiveDir).also {
                if (it.name !in CompressItems.all || it.name == CompressItems.COMPRESS_ITEM_APK ||
                    name != "${it.name}.json") {
                    throw ArchiveValidationException("$archiveDir/$name")
                }
            }
        }
        val extras = metaInfo.extraItems.orEmpty().map { (name, target) ->
            val path = Paths.get(target).normalize()
            if (!path.isAbsolute || path.parent == null) throw ArchiveValidationException(target)
            read(name, archiveDir) to target
        }.toMap()
        val availableTypes = ordinaryItems.map { it.name }.toSet() +
            extras.keys.map { it.name } + CompressItems.COMPRESS_ITEM_APK
        if (selectedTypes != null && !availableTypes.containsAll(selectedTypes)) {
            throw ArchiveValidationException(archiveDir)
        }
        val items = ordinaryItems.filter { selectedTypes == null || it.name in selectedTypes }.toMutableList()
        val selectedExtras = extras.filterKeys { selectedTypes == null || it.name in selectedTypes }

        fun verify(item: MetaDataItem, dir: String) {
            try {
                childPath(dir, item.file)
                if (item.md5.isNullOrBlank() || item.targetSize < 0 || !verifyFile(item, dir)) {
                    throw ArchiveValidationException("$dir/${item.file}")
                }
            } catch (e: ArchiveValidationException) {
                throw e
            } catch (e: Exception) {
                throw ArchiveValidationException("$dir/${item.file}", e)
            }
        }
        items.forEach { verify(it, archiveDir) }
        selectedExtras.keys.forEach { verify(it, archiveDir) }
        val checkApk = needInstallApk || selectedTypes?.contains(CompressItems.COMPRESS_ITEM_APK) == true ||
            (selectedTypes == null && "apk.json" in declaredItems)
        if (checkApk) {
            val apkDir = "$packageDir/apks/${metaInfo.packageInfo.versionCode}"
            val apk = read("${metaInfo.packageInfo.size}.json", apkDir)
            apk.name = CompressItems.COMPRESS_ITEM_APK
            verify(apk, apkDir)
            if (needInstallApk) items.add(0, apk)
        }
        return PreparedRestore(items, selectedExtras)
    }

    /** Metadata can name only files inside the declared archive directory. */
    fun childPath(directory: String, child: String): String {
        val root = Paths.get(directory).toAbsolutePath().normalize()
        val path = Paths.get(child)
        val resolved = root.resolve(path).normalize()
        if (child.isBlank() || path.isAbsolute || resolved == root || !resolved.startsWith(root)) {
            throw ArchiveValidationException("$directory/$child")
        }
        return resolved.toString()
    }
}
