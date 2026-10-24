package tiiehenry.android.app.snapshot.archive.restore

import org.junit.Assert.*
import org.junit.Test
import tiiehenry.android.app.snapshot.archive.bean.MetaDataItem
import tiiehenry.android.app.snapshot.archive.bean.MetaInfo
import tiiehenry.android.app.snapshot.archive.bean.MetaPackageInfo

class RestorePreflightTest {
    private fun data(name: String) = MetaDataItem("zstd", name, "$name.tar.zst", 20, 10, "digest", 0, 0, 3)
    private fun meta(vararg names: String) = MetaInfo().apply {
        dataItems = names.toList()
        extraItems = emptyMap()
        packageInfo = MetaPackageInfo().apply { versionCode = 7; size = 123 }
    }
    private fun plan(
        meta: MetaInfo,
        descriptors: Map<String, MetaDataItem>,
        selected: Set<String>? = null,
        installApk: Boolean = false,
        verify: (MetaDataItem, String) -> Boolean = { _, _ -> true }
    ) = RestorePreflight.prepare(meta, "/group/pkg/snapshot", "/group/pkg", selected, installApk,
        { name, dir -> descriptors["$dir/$name"] ?: error("missing") }, verify)

    @Test fun `missing declared descriptor blocks restore rather than producing an empty success`() {
        assertThrows(ArchiveValidationException::class.java) { plan(meta("user.json"), emptyMap()) }
    }

    @Test fun `corrupt or missing compressed file blocks restore before any destination mutation`() {
        val metadata = meta("user.json")
        val descriptors = mapOf("/group/pkg/snapshot/user.json" to data("user"))
        assertThrows(ArchiveValidationException::class.java) { plan(metadata, descriptors, verify = { _, _ -> false }) }
    }

    @Test fun `media-only restore plan excludes internal data`() {
        val plan = plan(meta("user.json", "user_de.json", "media.json"),
            listOf("user", "user_de", "media").associate { "/group/pkg/snapshot/$it.json" to data(it) },
            selected = setOf("media"))
        assertEquals(listOf("media"), plan.dataItems.map { it.name })
    }

    @Test fun `legacy apk reference resolves shared APK metadata`() {
        val apk = data("123")
        val plan = plan(meta("apk.json"), mapOf("/group/pkg/apks/7/123.json" to apk),
            selected = setOf("apk"), installApk = true)
        assertEquals("apk", plan.dataItems.single().name)
    }

    @Test fun `deduplicated legacy snapshot with no apk reference can still install shared APK`() {
        val plan = plan(meta(), mapOf("/group/pkg/apks/7/123.json" to data("123")), installApk = true)
        assertEquals("apk", plan.dataItems.single().name)
    }

    @Test fun `missing extra descriptor is not silently discarded`() {
        val metadata = meta().apply { extraItems = mapOf("documents.json" to "/data/custom") }
        assertThrows(ArchiveValidationException::class.java) { plan(metadata, emptyMap()) }
    }

    @Test fun `extra json name shared with a data item fails preflight`() {
        val metadata = meta("user.json").apply { extraItems = mapOf("user.json" to "/data/custom") }
        val descriptors = mapOf("/group/pkg/snapshot/user.json" to data("user"))
        assertThrows(ArchiveValidationException::class.java) { plan(metadata, descriptors) }
    }

    @Test fun `extra json name that is not a data item stays in the restore plan`() {
        val metadata = meta("media.json").apply { extraItems = mapOf("documents.json" to "/data/custom") }
        val descriptors = mapOf(
            "/group/pkg/snapshot/media.json" to data("media"),
            "/group/pkg/snapshot/documents.json" to data("documents"),
        )
        val prepared = plan(metadata, descriptors)
        assertEquals(listOf("media"), prepared.dataItems.map { it.name })
        assertEquals(listOf("documents"), prepared.extraItems.keys.map { it.name })
    }

    @Test fun `missing checksum and paths outside archive are rejected`() {
        val item = data("user").apply { md5 = "" }
        assertThrows(ArchiveValidationException::class.java) {
            plan(meta("user.json"), mapOf("/group/pkg/snapshot/user.json" to item))
        }
        item.md5 = "digest"
        item.file = "../../other.tar.zst"
        assertThrows(ArchiveValidationException::class.java) {
            plan(meta("user.json"), mapOf("/group/pkg/snapshot/user.json" to item))
        }
    }
    @Test fun `selected APK is checked even when the installed version needs no reinstall`() {
        assertThrows(ArchiveValidationException::class.java) {
            plan(meta("apk.json"), emptyMap(), selected = setOf("apk"), installApk = false)
        }
    }

    @Test fun `missing manifest item list is not treated as an empty snapshot`() {
        assertThrows(ArchiveValidationException::class.java) { plan(MetaInfo(), emptyMap()) }
    }

}
