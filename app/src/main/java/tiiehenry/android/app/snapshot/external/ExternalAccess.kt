package tiiehenry.android.app.snapshot.external

enum class TargetMode {
    ALL,
    WHITELIST,
}

data class CallerId(
    val packageName: String,
    val certSha256: String,
)

data class ExternalAccessState(
    val enabled: Boolean = false,
    val approved: Set<CallerId> = emptySet(),
    val pending: Set<CallerId> = emptySet(),
    val targetMode: TargetMode = TargetMode.WHITELIST,
    val targetPackages: Set<String> = emptySet(),
    val mcpEnabled: Boolean = false,
    val mcpTokenHash: String = "",
)

object ExternalAccess {
    const val MCP_PACKAGE = "local.mcp"
    const val MCP_CERT = "mcp"

    val mcpCaller = CallerId(MCP_PACKAGE, MCP_CERT)

    const val SERVICE_DISABLED = "SERVICE_DISABLED"
    const val CONSENT_REQUIRED = "CONSENT_REQUIRED"
    const val APP_NOT_MANAGED = "APP_NOT_MANAGED"
    const val APP_NOT_IN_GROUP = "APP_NOT_IN_GROUP"
    const val AMBIGUOUS_CARD = "AMBIGUOUS_CARD"
    const val INSTANCE_NOT_FOUND = "INSTANCE_NOT_FOUND"
    const val BUSY = "BUSY"
    const val FAILED = "FAILED"
    const val CONFIRM_REQUIRED = "CONFIRM_REQUIRED"
    const val CANCELLED = "CANCELLED"

    fun decide(state: ExternalAccessState, caller: CallerId?, targetPackage: String): String? {
        if (!state.enabled) return SERVICE_DISABLED
        if (caller == null || caller !in state.approved) return CONSENT_REQUIRED
        if (!targetAllowed(state, targetPackage)) return APP_NOT_MANAGED
        return null
    }

    fun targetAllowed(state: ExternalAccessState, targetPackage: String): Boolean {
        if (state.targetMode == TargetMode.ALL) return true
        return targetPackage in state.targetPackages
    }
}
