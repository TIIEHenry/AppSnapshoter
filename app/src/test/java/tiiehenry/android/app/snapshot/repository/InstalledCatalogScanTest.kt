package tiiehenry.android.app.snapshot.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstalledCatalogScanTest {

    @Test
    fun `one user package list failure keeps previous apps and stays unloaded`() {
        val previous = mapOf(1 to listOf(CatalogPackage("com.keep", "1", 1L)))
        var current = previous
        var loaded = false
        val scan = scanInstalledCatalog(
            users = listOf(1, 2),
            userId = { it },
            installedPackages = { user ->
                if (user == 2) error("packages failed")
                listOf("com.new")
            },
            readPackage = { _, name -> CatalogPackage(name, "9", 9L) },
        )
        val published = installedCatalogToPublish(scan)
        if (published != null) {
            current = published
            loaded = true
        }

        assertTrue(scan.failedUserIds.isNotEmpty())
        assertNull(scan.rows)
        assertNull(published)
        assertFalse(loaded)
        assertEquals(previous, current)
    }

    @Test
    fun `all user package list failures do not publish an empty map`() {
        val previous = mapOf(1 to listOf(CatalogPackage("com.keep", null, 1L)))
        var current = previous
        var loaded = false
        val scan = scanInstalledCatalog(
            users = listOf(1, 2),
            userId = { it },
            installedPackages = { error("packages failed") },
            readPackage = { _, name -> CatalogPackage(name, null, 1L) },
        )
        val published = installedCatalogToPublish(scan)
        if (published != null) {
            current = published
            loaded = true
        }

        assertEquals(listOf(1, 2), scan.failedUserIds)
        assertNull(scan.rows)
        assertNull(published)
        assertFalse(loaded)
        assertEquals(previous, current)
    }

    @Test
    fun `all users succeeding publishes and marks the catalog loaded`() {
        val scan = scanInstalledCatalog(
            users = listOf(1, 2),
            userId = { it },
            installedPackages = { user -> listOf("com.user$user") },
            readPackage = { _, name -> CatalogPackage(name, "1", 2L) },
        )
        val published = installedCatalogToPublish(scan)
        val loaded = published != null

        assertTrue(loaded)
        assertEquals(setOf(1, 2), published!!.keys)
        assertEquals("com.user1", published.getValue(1).single().packageName)
        assertEquals("com.user2", published.getValue(2).single().packageName)
    }

    @Test
    fun `one package info failure still publishes that user`() {
        val scan = scanInstalledCatalog(
            users = listOf(7),
            userId = { it },
            installedPackages = { listOf("com.good", "com.bad") },
            readPackage = { _, name ->
                if (name == "com.bad") error("info")
                CatalogPackage(name, "2", 3L)
            },
        )
        val published = installedCatalogToPublish(scan)

        assertTrue(published != null)
        assertEquals(setOf(7), published!!.keys)
        assertEquals(listOf("com.good"), published.getValue(7).map { it.packageName })
    }

    @Test
    fun `empty user list does not publish or mark loaded`() {
        val scan = scanInstalledCatalog(
            users = emptyList<Int>(),
            userId = { it },
            installedPackages = { error("should not list packages") },
            readPackage = { _, name -> CatalogPackage(name, null, 0L) },
        )

        assertTrue(scan.noUsers)
        assertNull(scan.rows)
        assertNull(installedCatalogToPublish(scan))
    }
}
