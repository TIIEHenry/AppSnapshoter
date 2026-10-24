package tiiehenry.android.app.snapshot.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tiiehenry.android.app.snapshot.instance.AppInstance

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

    @Test
    fun pending_is_bounded() {
        var state = ExternalAccessState(enabled = true)
        repeat(ExternalAccess.MAX_PENDING + 5) { index ->
            state = ExternalAccess.addPending(state, CallerId("com.pending$index", "cert"))
        }
        assertEquals(ExternalAccess.MAX_PENDING, state.pending.size)
        assertEquals(
            state,
            ExternalAccess.addPending(state, CallerId("com.one-more", "cert")),
        )
    }

    @Test
    fun approved_caller_does_not_need_pending_entry() {
        val state = ExternalAccessState(enabled = true, approved = setOf(caller))
        assertEquals(state, ExternalAccess.addPending(state, caller))
    }

    @Test
    fun mcp_identity_requires_mcp_switch() {
        val approved = ExternalAccessState(
            enabled = true,
            approved = setOf(ExternalAccess.mcpCaller),
            mcpEnabled = false,
            targetMode = TargetMode.ALL,
        )
        assertEquals(
            ExternalAccess.SERVICE_DISABLED,
            ExternalAccess.decide(approved, ExternalAccess.mcpCaller, "com.target"),
        )
        assertNull(
            ExternalAccess.decide(
                approved.copy(mcpEnabled = true),
                ExternalAccess.mcpCaller,
                "com.target",
            ),
        )
    }

    @Test
    fun getOperation_passes_through_the_same_gate() {
        assertEquals(ExternalAccess.SERVICE_DISABLED, ExternalAccess.decideOperation(ExternalAccessState(), caller))
        assertEquals(
            ExternalAccess.CONSENT_REQUIRED,
            ExternalAccess.decideOperation(ExternalAccessState(enabled = true), caller),
        )
        assertNull(
            ExternalAccess.decideOperation(
                ExternalAccessState(enabled = true, approved = setOf(caller)),
                caller,
            ),
        )
    }

    @Test
    fun confirm_rechecks_certificate_for_regular_callers() {
        val state = ExternalAccessState(
            enabled = true,
            approved = setOf(caller),
            targetMode = TargetMode.ALL,
        )
        assertEquals(false, ExternalAccess.isCallerValid(state, caller, "com.target", "changed"))
        assertEquals(false, ExternalAccess.isCallerValid(state, caller, "com.target", ""))
        assertEquals(true, ExternalAccess.isCallerValid(state, caller, "com.target", caller.certSha256))
    }

    @Test
    fun mcp_caller_uses_switch_and_approval_instead_of_certificate() {
        val state = ExternalAccessState(
            enabled = true,
            approved = setOf(ExternalAccess.mcpCaller),
            targetMode = TargetMode.ALL,
            mcpEnabled = true,
        )
        assertEquals(true, ExternalAccess.isCallerValid(state, ExternalAccess.mcpCaller, "com.target", ""))
        assertEquals(
            false,
            ExternalAccess.isCallerValid(
                state.copy(mcpEnabled = false),
                ExternalAccess.mcpCaller,
                "com.target",
                "",
            ),
        )
    }

    @Test
    fun confirm_rejects_revoked_or_out_of_scope_targets() {
        val state = ExternalAccessState(
            enabled = true,
            approved = setOf(caller),
            targetMode = TargetMode.WHITELIST,
            targetPackages = setOf("com.allowed"),
        )
        assertEquals(false, ExternalAccess.isCallerValid(state, caller, "com.other", caller.certSha256))
        assertEquals(
            false,
            ExternalAccess.isCallerValid(
                state.copy(approved = emptySet()),
                caller,
                "com.allowed",
                caller.certSha256,
            ),
        )
    }

    @Test
    fun pending_states_are_not_ok_success() {
        // ok 只在终态成功（done）时为 true；confirm/running 必须带码返回。
        val confirm = InstanceProtocol.operation("op1", "confirm", "工作", ExternalAccess.CONFIRM_REQUIRED)
        val running = InstanceProtocol.operation("op2", "running")
        val done = InstanceProtocol.operation("op3", "done", "ok")
        val failed = InstanceProtocol.operation("op4", "error", "boom", ExternalAccess.CANCELLED)
        assertEquals(false, org.json.JSONObject(confirm).getBoolean("ok"))
        assertEquals(ExternalAccess.CONFIRM_REQUIRED, org.json.JSONObject(confirm).getString("code"))
        assertEquals(false, org.json.JSONObject(running).getBoolean("ok"))
        assertEquals(ExternalAccess.RUNNING, org.json.JSONObject(running).getString("code"))
        assertEquals(true, org.json.JSONObject(done).getBoolean("ok"))
        assertEquals(false, org.json.JSONObject(failed).getBoolean("ok"))
        assertEquals(ExternalAccess.CANCELLED, org.json.JSONObject(failed).getString("code"))
    }

    @Test
    fun ambiguous_card_lists_candidate_groups() {
        val json = org.json.JSONObject(InstanceProtocol.ambiguous(listOf("g1" to "工作", "g2" to "生活")))
        assertEquals(ExternalAccess.AMBIGUOUS_CARD, json.getString("code"))
        val groups = json.getJSONArray("groups")
        assertEquals(2, groups.length())
        assertEquals("g1", groups.getJSONObject(0).getString("id"))
        assertEquals("生活", groups.getJSONObject(1).getString("name"))
    }

    @Test
    fun bound_instance_only_accepts_its_external_id() {
        assertTrue(InstanceProtocol.ownsInstance("", "xai/acct-1"))
        assertTrue(InstanceProtocol.ownsInstance("xai/acct-1", "xai/acct-1"))
        assertFalse(InstanceProtocol.ownsInstance("xai/acct-1", ""))
        assertFalse(InstanceProtocol.ownsInstance("xai/acct-1", "xai/acct-2"))
    }

    @Test
    fun list_instances_hides_other_external_ids() {
        val instances = listOf(
            instance("unbound", ""),
            instance("mine", "e1"),
            instance("other", "e2"),
        )
        assertEquals(
            listOf("unbound", "mine"),
            InstanceProtocol.visibleInstances(instances, "e1").map { it.id },
        )
        assertEquals(
            listOf("unbound"),
            InstanceProtocol.visibleInstances(instances, "").map { it.id },
        )
    }

    private fun instance(id: String, externalId: String) = AppInstance(
        id = id,
        name = id,
        account = "",
        note = "",
        createdAt = 0,
        updatedAt = 0,
        externalId = externalId,
    )
}
