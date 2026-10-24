package tiiehenry.android.app.snapshot.external

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import org.json.JSONObject

class McpInstanceServerTest {
    private val server = McpInstanceServer(
        port = 0,
        tokenHash = { McpInstanceServer.sha256("token") },
        callTool = { name, args -> "$name:${args.optString("value")}" },
    )

    @Test
    fun header_reader_handles_crlf_and_returns_headers_without_terminator() {
        val header = server.readHeader(
            ByteArrayInputStream("Content-Length: 2\r\nAuthorization: Bearer token\r\n\r\n{}".toByteArray()),
        )
        assertNotNull(header)
        assertEquals("Content-Length: 2\r\nAuthorization: Bearer token", header)
    }

    @Test
    fun body_reader_uses_byte_length_for_utf8() {
        val body = """{"method":"tools/call","params":{"name":"x","arguments":{"value":"中文"}}}"""
        val bytes = body.toByteArray(Charsets.UTF_8)
        val read = server.readExactly(ByteArrayInputStream(bytes), bytes.size)
        assertNotNull(read)
        assertEquals(body, read!!.toString(Charsets.UTF_8))
    }

    @Test
    fun body_reader_rejects_truncated_body() {
        assertEquals(null, server.readExactly(ByteArrayInputStream("{}".toByteArray()), 10))
    }

    @Test
    fun tools_list_includes_operation_polling() {
        val response = JSONObject(
            server.answer(
                "Bearer token",
                """{"jsonrpc":"2.0","id":1,"method":"tools/list"}""",
            ),
        )
        val tools = response.getJSONObject("result").getJSONArray("tools")
        val names = buildSet {
            for (index in 0 until tools.length()) {
                add(tools.getJSONObject(index).getString("name"))
            }
        }
        assertEquals(
            setOf(
                "list_instances",
                "switch_instance",
                "create_instance",
                "update_instance",
                "delete_instance",
                "bind_instance",
                "unbind_instance",
                "get_operation",
            ),
            names,
        )
    }

    @Test
    fun get_operation_maps_to_protocol_method() {
        val request = JSONObject(
            McpInstanceServer.toolToRequest(
                "get_operation",
                JSONObject().put("operationId", "op-1"),
            ),
        )
        assertEquals("getOperation", request.getString("method"))
        assertEquals("op-1", request.getString("operationId"))
    }

    @Test
    fun tool_call_returns_text_content() {
        val response = JSONObject(
            server.answer(
                "Bearer token",
                """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"list_instances","arguments":{"value":"中文"}}}""",
            ),
        )
        val text = response.getJSONObject("result").getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
        assertTrue(text.startsWith("list_instances:中文"))
    }

}
