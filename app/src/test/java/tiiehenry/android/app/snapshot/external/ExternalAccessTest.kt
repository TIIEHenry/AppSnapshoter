package tiiehenry.android.app.snapshot.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalAccessTest {
    private val caller = CallerId("com.harnessmanager.android", "abc")

    @Test
    fun disabled_rejects_before_consent() {
        val code = ExternalAccess.decide(ExternalAccessState(), caller, "com.target")
        assertEquals(ExternalAccess.SERVICE_DISABLED, code)
    }

    @Test
    fun enabled_without_approval_needs_consent() {
        val code = ExternalAccess.decide(ExternalAccessState(enabled = true), caller, "com.target")
        assertEquals(ExternalAccess.CONSENT_REQUIRED, code)
    }

    @Test
    fun whitelist_blocks_other_packages() {
        val state = ExternalAccessState(
            enabled = true,
            approved = setOf(caller),
            targetMode = TargetMode.WHITELIST,
            targetPackages = setOf("com.allowed"),
        )
        assertEquals(ExternalAccess.APP_NOT_MANAGED, ExternalAccess.decide(state, caller, "com.other"))
        assertNull(ExternalAccess.decide(state, caller, "com.allowed"))
    }

    @Test
    fun all_mode_allows_any_target_after_consent() {
        val state = ExternalAccessState(
            enabled = true,
            approved = setOf(caller),
            targetMode = TargetMode.ALL,
        )
        assertNull(ExternalAccess.decide(state, caller, "com.any"))
    }

    @Test
    fun store_round_trip() {
        val state = ExternalAccessState(
            enabled = true,
            approved = setOf(caller, ExternalAccess.mcpCaller),
            pending = setOf(CallerId("com.pending", "def")),
            targetMode = TargetMode.ALL,
            targetPackages = setOf("com.allowed"),
            mcpEnabled = true,
            mcpTokenHash = "hash",
        )
        assertEquals(state, ExternalAccessStore.decode(ExternalAccessStore.encode(state)))
    }
}
