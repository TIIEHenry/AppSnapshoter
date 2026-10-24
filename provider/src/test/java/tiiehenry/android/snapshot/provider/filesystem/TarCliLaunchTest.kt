package tiiehenry.android.snapshot.provider.filesystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TarCliLaunchTest {
    @Test fun `executable replaces the tar word and later arguments stay in order`() {
        val command = tarCliCommand(
            "/data/app/libtar-cli.so",
            arrayOf("tar", "-cpf", "-", "-C", "/data", "--add-file=user/0/pkg", "--add-file=-secret")
        )
        assertEquals(
            listOf(
                "/data/app/libtar-cli.so",
                "-cpf",
                "-",
                "-C",
                "/data",
                "--add-file=user/0/pkg",
                "--add-file=-secret"
            ),
            command
        )
        assertFalse(command.contains("tar"))
        assertFalse(command.contains("--"))
    }
}
