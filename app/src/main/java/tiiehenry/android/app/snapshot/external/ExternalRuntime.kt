package tiiehenry.android.app.snapshot.external

import android.content.Context
import android.util.Log
import java.security.SecureRandom

object ExternalRuntime {
    @Volatile
    private var mcp: McpInstanceServer? = null
    @Volatile
    private var retryAt = 0L

    @Synchronized
    fun refresh(context: Context) {
        val state = ExternalAccessStore(context).read()
        val current = mcp
        if (!state.enabled || !state.mcpEnabled || state.mcpTokenHash.isEmpty() ||
            ExternalAccess.mcpCaller !in state.approved
        ) {
            current?.stop()
            mcp = null
            retryAt = 0L
            return
        }
        if (current != null && current.isRunning()) return
        if (current != null) {
            current.stop()
            mcp = null
        }
        if (System.currentTimeMillis() < retryAt) return
        val server = McpInstanceServer(
            port = McpInstanceServer.PORT,
            tokenHash = { ExternalAccessStore(context).read().mcpTokenHash },
            callTool = { name, args ->
                val request = McpInstanceServer.toolToRequest(name, args)
                InstanceGateway(context).call(ExternalAccess.mcpCaller, request)
            },
        )
        mcp = server
        try {
            server.start()
        } catch (e: Exception) {
            Log.w("ExternalRuntime", "mcp start failed", e)
            mcp = null
            // Port can be held briefly by the previous server after a restart.
            retryAt = System.currentTimeMillis() + 30_000L
        }
    }

    fun newToken(): String {
        val bytes = ByteArray(24)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
