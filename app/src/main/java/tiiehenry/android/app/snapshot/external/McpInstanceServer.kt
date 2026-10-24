package tiiehenry.android.app.snapshot.external

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 只接受 127.0.0.1 的 MCP JSON-RPC。令牌、总开关和目标应用范围与 Service 相同。
 */
class McpInstanceServer(
    private val port: Int,
    private val tokenHash: () -> String,
    private val callTool: (String, JSONObject) -> String,
) {
    private val running = AtomicBoolean(false)
    private var socket: ServerSocket? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val server = try {
            ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"))
        } catch (e: Exception) {
            running.set(false)
            throw e
        }
        socket = server
        Thread({
            try {
                while (running.get()) {
                    val client = server.accept()
                    if (client.inetAddress?.isLoopbackAddress != true) {
                        client.close()
                        continue
                    }
                    Thread { handle(client) }.start()
                }
            } catch (e: Exception) {
                if (running.get()) Log.w(TAG, "mcp stopped", e)
            } finally {
                try {
                    server.close()
                } catch (e: Exception) {
                    Log.w(TAG, "mcp close", e)
                }
                // A stopped server may already have been replaced by a new one.
                // Only the current listener may clear the shared running flag.
                if (socket === server) {
                    running.set(false)
                    socket = null
                }
            }
        }, "instance-mcp").start()
    }

    fun stop() {
        running.set(false)
        try {
            socket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "mcp close", e)
        }
        socket = null
    }

    fun isRunning(): Boolean = running.get()

    private fun handle(client: java.net.Socket) {
        client.use { socket ->
            socket.soTimeout = SOCKET_TIMEOUT_MS
            val input = socket.getInputStream()
            val header = readHeader(input) ?: return
            val length = header.lineSequence()
                .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                ?.toIntOrNull()
                ?: return
            if (length < 0 || length > MAX_BODY_BYTES) return
            val auth = header.lineSequence()
                .firstOrNull { it.startsWith("Authorization:", ignoreCase = true) }
                ?.substringAfter(':')
                ?.trim()
                .orEmpty()
            val body = readExactly(input, length) ?: return
            val response = answer(auth, String(body, Charsets.UTF_8))
            val bytes = response.toByteArray()
            val out = socket.getOutputStream()
            out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(bytes)
        }
    }

    /** Read HTTP headers as bytes; Content-Length counts bytes, not characters. */
    internal fun readHeader(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        var matched = 0
        while (out.size() < MAX_HEADER_BYTES) {
            val b = input.read()
            if (b < 0) return null
            out.write(b)
            matched = when {
                matched == 0 && b == '\r'.code -> 1
                matched == 1 && b == '\n'.code -> 2
                matched == 2 && b == '\r'.code -> 3
                matched == 3 && b == '\n'.code -> {
                    val text = out.toString(Charsets.ISO_8859_1.name())
                    return text.substringBefore("\r\n\r\n")
                }
                b == '\r'.code -> 1
                else -> 0
            }
        }
        return null
    }

    internal fun readExactly(input: InputStream, length: Int): ByteArray? {
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(body, read, length - read)
            if (n < 0) return null
            read += n
        }
        return body
    }

    fun answer(authorization: String, body: String): String {
        val expected = tokenHash()
        val token = authorization.removePrefix("Bearer ").trim()
        if (expected.isEmpty() || sha256(token) != expected) {
            return rpcError(null, "unauthorized")
        }
        val request = try {
            JSONObject(body)
        } catch (e: Exception) {
            return rpcError(null, "bad json")
        }
        val id = request.opt("id")
        return when (request.optString("method")) {
            "initialize" -> rpc(id, JSONObject().put("protocolVersion", "2024-11-05"))
            "tools/list" -> rpc(id, JSONObject().put("tools", tools()))
            "tools/call" -> {
                val params = request.optJSONObject("params") ?: JSONObject()
                val name = params.optString("name")
                val args = params.optJSONObject("arguments") ?: JSONObject()
                // A tool failure must become a JSON-RPC error, not tear down the socket.
                val text = try {
                    callTool(name, args)
                } catch (e: Exception) {
                    return rpcError(id, "tool failed")
                }
                rpc(id, JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text))))
            }
            else -> rpcError(id, "unknown method")
        }
    }

    private fun tools(): JSONArray = JSONArray()
        .put(tool("list_instances"))
        .put(tool("switch_instance"))
        .put(tool("create_instance"))
        .put(tool("update_instance"))
        .put(tool("delete_instance"))
        .put(tool("bind_instance"))
        .put(tool("unbind_instance"))
        .put(tool("get_operation"))

    private fun tool(name: String) = JSONObject().put("name", name).put("inputSchema", JSONObject().put("type", "object"))

    private fun rpc(id: Any?, result: JSONObject): String =
        JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result).toString()

    private fun rpcError(id: Any?, message: String): String =
        JSONObject().put("jsonrpc", "2.0").put("id", id).put("error", JSONObject().put("code", -32000).put("message", message)).toString()

    companion object {
        const val PORT = 47331
        private const val TAG = "McpInstance"
        private const val MAX_HEADER_BYTES = 16 * 1024
        private const val MAX_BODY_BYTES = 1024 * 1024
        private const val SOCKET_TIMEOUT_MS = 10_000

        fun sha256(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }

        fun toolToRequest(name: String, args: JSONObject): String {
            val method = when (name) {
                "list_instances" -> "listInstances"
                "switch_instance" -> "switchInstance"
                "create_instance" -> "createInstance"
                "update_instance" -> "updateInstance"
                "delete_instance" -> "deleteInstance"
                "bind_instance" -> "bindInstance"
                "unbind_instance" -> "unbindInstance"
                "get_operation" -> "getOperation"
                else -> ""
            }
            return JSONObject(args.toString()).put("method", method).toString()
        }
    }
}
