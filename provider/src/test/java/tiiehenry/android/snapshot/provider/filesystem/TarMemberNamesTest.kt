package tiiehenry.android.snapshot.provider.filesystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TarMemberNamesTest {
    @Test fun `colliding final segments become paths under their common parent`() {
        val specs = multiTarMemberSpecs(
            listOf("/data/user/0/pkg", "/data/user_de/0/pkg")
        )
        assertEquals("/data", specs[0].directory)
        assertEquals("user/0/pkg", specs[0].member)
        assertEquals("/data", specs[1].directory)
        assertEquals("user_de/0/pkg", specs[1].member)
        assertTrue(specs[0].member != specs[1].member)
    }

    @Test fun `unique final segments stay as those names even beside a collision`() {
        val specs = multiTarMemberSpecs(
            listOf("/data/user/0/pkg", "/data/user_de/0/pkg", "/sdcard/unique")
        )
        assertEquals(listOf("user/0/pkg", "user_de/0/pkg", "unique"), specs.map { it.member })
        assertEquals("/sdcard", specs[2].directory)
    }

    @Test fun `distinct final segments are archived under those names`() {
        val specs = multiTarMemberSpecs(listOf("/data/user/0/alpha", "/sdcard/beta"))
        assertEquals(listOf("alpha", "beta"), specs.map { it.member })
    }

    @Test fun `a leading dash is passed with add-file and without an end-of-options marker`() {
        val command = buildMultiTarCommand("/tmp/out.tar", listOf("/tmp/sec/-secret"))
        assertTrue(command.contains("--add-file=-secret"))
        assertFalse(command.contains("--"))
        assertFalse(command.contains("-secret"))
        assertEquals("-C", command[command.indexOf("--add-file=-secret") - 2])
    }

    @Test fun `gnu tar keeps colliding trees apart and accepts a dashed filename`() {
        val root = Files.createTempDirectory("tar-members").toFile()
        try {
            val user = File(root, "data/user/0/pkg").apply { mkdirs() }
            val userDe = File(root, "data/user_de/0/pkg").apply { mkdirs() }
            File(user, "f.txt").writeText("from-user")
            File(userDe, "f.txt").writeText("from-user-de")
            val secretDir = File(root, "sec").apply { mkdirs() }
            File(secretDir, "-secret").writeText("hidden")
            val alpha = File(root, "one/alpha").apply { mkdirs() }
            val beta = File(root, "two/beta").apply { mkdirs() }
            File(alpha, "n.txt").writeText("alpha")
            File(beta, "n.txt").writeText("beta")
            val archive = File(root, "out.tar")
            val command = buildMultiTarCommand(
                archive.path,
                listOf(user.path, userDe.path, File(secretDir, "-secret").path, alpha.path, beta.path)
            )
            assertEquals(0, run(command))
            val members = runLines(listOf("tar", "-tf", archive.path))
            assertTrue(members.any { it.startsWith("user/0/pkg") })
            assertTrue(members.any { it.startsWith("user_de/0/pkg") })
            assertTrue(members.any { it == "-secret" || it == "-secret/" })
            assertTrue(members.any { it.startsWith("alpha") })
            assertTrue(members.any { it.startsWith("beta") })
            assertFalse(members.any { it == "pkg" || it == "pkg/" })

            val out = File(root, "out").apply { mkdirs() }
            assertEquals(0, run(listOf("tar", "-xpf", archive.path, "-C", out.path)))
            assertEquals("from-user", File(out, "user/0/pkg/f.txt").readText())
            assertEquals("from-user-de", File(out, "user_de/0/pkg/f.txt").readText())
            assertEquals("hidden", File(out, "-secret").readText())
            assertEquals("alpha", File(out, "alpha/n.txt").readText())
            assertEquals("beta", File(out, "beta/n.txt").readText())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun run(command: List<String>): Int {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().decodeToString()
        val code = process.waitFor()
        assertEquals(output, 0, code)
        return code
    }

    private fun runLines(command: List<String>): List<String> {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.readBytes().decodeToString()
        assertEquals(output, 0, process.waitFor())
        return output.lines().filter { it.isNotEmpty() }
    }
}
