package tiiehenry.android.app.snapshot.external

import org.json.JSONArray
import org.json.JSONObject

data class InstanceRequest(
    val method: String,
    val packageName: String,
    val userId: Int,
    val groupId: String,
    val instanceId: String,
    val name: String,
    val account: String,
    val note: String,
    val operationId: String,
    val externalId: String,
)

object InstanceProtocol {
    fun request(json: String): InstanceRequest? {
        val obj = try {
            JSONObject(json)
        } catch (e: Exception) {
            return null
        }
        val method = obj.optString("method")
        if (method.isEmpty()) return null
        return InstanceRequest(
            method = method,
            packageName = obj.optString("packageName"),
            userId = obj.optInt("userId"),
            groupId = obj.optString("groupId"),
            instanceId = obj.optString("instanceId"),
            name = obj.optString("name"),
            account = obj.optString("account"),
            note = obj.optString("note"),
            operationId = obj.optString("operationId"),
            externalId = obj.optString("externalId"),
        )
    }

    fun okMessage(message: String): String =
        JSONObject().put("ok", true).put("message", message).toString()

    fun error(code: String, message: String = "", operationId: String = ""): String =
        JSONObject().put("ok", false).put("code", code).put("message", message).put("operationId", operationId).toString()

    fun instances(rows: List<JSONObject>): String =
        JSONObject().put("ok", true).put("instances", JSONArray(rows)).toString()

    fun operation(id: String, state: String, message: String = "", code: String = ""): String =
        JSONObject()
            .put("ok", state != "error")
            .put("operationId", id)
            .put("state", state)
            .put("message", message)
            .put("code", code)
            .toString()

    fun instanceRow(
        id: String,
        name: String,
        account: String,
        note: String,
        externalId: String,
        updatedAt: Long,
    ): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("account", account)
            .put("note", note)
            .put("externalId", externalId)
            .put("updatedAt", updatedAt)
}
