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
    const val RUNNING = "RUNNING"

    /** 全局最多保留的待同意条目，避免外部进程反复探测撑大配置。 */
    const val MAX_PENDING = 32

    /** 总开关与 MCP 子开关都打开时，外部入口才可用。 */
    fun serviceEnabled(state: ExternalAccessState, caller: CallerId?): Boolean =
        state.enabled && (caller != mcpCaller || state.mcpEnabled)

    fun decide(state: ExternalAccessState, caller: CallerId?, targetPackage: String): String? {
        if (!serviceEnabled(state, caller)) return SERVICE_DISABLED
        if (caller == null || caller !in state.approved) return CONSENT_REQUIRED
        if (!targetAllowed(state, targetPackage)) return APP_NOT_MANAGED
        return null
    }

    /**
     * 确认页执行前的最终校验。MCP 使用内置调用方，没有可读取的安装包证书；
     * 它的门禁是 [serviceEnabled] 中的 mcpEnabled 加同意名单。
     */
    fun isCallerValid(
        state: ExternalAccessState,
        caller: CallerId,
        targetPackage: String,
        currentCertSha256: String,
    ): Boolean {
        if (decide(state, caller, targetPackage) != null) return false
        if (caller == mcpCaller) return true
        return currentCertSha256.isNotEmpty() && currentCertSha256 == caller.certSha256
    }

    /**
     * getOperation 也要过闸：总开关和调用方同意缺一不可。
     * 操作归属由 InstanceGateway 再查（只放行创建者，防止探测他人进度）。
     */
    fun decideOperation(state: ExternalAccessState, caller: CallerId?): String? {
        if (!serviceEnabled(state, caller)) return SERVICE_DISABLED
        if (caller == null || caller !in state.approved) return CONSENT_REQUIRED
        return null
    }

    fun targetAllowed(state: ExternalAccessState, targetPackage: String): Boolean {
        if (state.targetMode == TargetMode.ALL) return true
        return targetPackage in state.targetPackages
    }

    fun addPending(state: ExternalAccessState, caller: CallerId): ExternalAccessState {
        if (caller in state.approved || caller in state.pending) return state
        if (state.pending.size >= MAX_PENDING) return state
        return state.copy(pending = state.pending + caller)
    }
}
