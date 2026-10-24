package tiiehenry.android.app.snapshot.group

import android.content.Context
import tiiehenry.android.app.snapshot.archive.bean.MetaInfo
import tiiehenry.android.app.snapshot.archive.bean.MetaPackageInfo
import tiiehenry.android.app.snapshot.archive.bean.MetaPermission
import tiiehenry.android.app.snapshot.main.launch.batch.BatchTestStubFileSystem
import tiiehenry.android.snapshot.file.IFileSystem
import tiiehenry.android.snapshot.fs.IFileType
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

internal fun SnapGroup.detach(
    path: String = "/snap/$id",
    userId: Int = 0,
    exclusive: Boolean = true,
): SnapGroup {
    detachedPath = path
    detachedUserId = userId
    detachedExclusive = exclusive
    return this
}

internal fun childPath(parent: String, name: String): String =
    Paths.get(parent, name).absolutePathString()

internal fun packageNames(group: SnapGroup): List<String> =
    group.apps.map { Paths.get(it.packageDir).fileName.toString() }

internal fun testMeta(
    packageName: String,
    userId: Int = 0,
    makeTime: Long = 1L,
    dataItems: List<String>? = emptyList(),
    extraItems: Map<String, String>? = emptyMap(),
): MetaInfo {
    val pkg = MetaPackageInfo(packageName, packageName, 1L, "1.0", 0L, 0, 0L, 0L)
    return MetaInfo(
        pkg,
        userId,
        "",
        dataItems,
        extraItems,
        emptyList<MetaPermission>(),
        makeTime,
        false,
    )
}

internal class ScriptedFileSystem : IFileSystem by BatchTestStubFileSystem {
    var onListDir: (String) -> MutableList<String> = { mutableListOf() }
    var onFileType: (String) -> Int = { IFileType.TYPE_DIR }
    var onExists: (String) -> Boolean = { true }

    override fun listDir(path: String) = onListDir(path)
    override fun fileType(path: String) = onFileType(path)
    override fun exists(path: String) = onExists(path)
}

internal val unusedContext: Context by lazy {
    val unsafeClass = Class.forName("sun.misc.Unsafe")
    val field = unsafeClass.getDeclaredField("theUnsafe")
    field.isAccessible = true
    val unsafe = field.get(null)
    val allocate = unsafeClass.getMethod("allocateInstance", Class::class.java)
    val wrapper = Class.forName("android.content.ContextWrapper")
    allocate.invoke(unsafe, wrapper) as Context
}
