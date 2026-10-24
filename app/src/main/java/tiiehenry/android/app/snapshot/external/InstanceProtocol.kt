package tiiehenry.android.app.snapshot.external

import org.json.JSONArray
import org.json.JSONObject
import tiiehenry.android.app.snapshot.instance.AppInstance

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
    /**
     * 未绑定的实例对所有已授权调用方开放；已绑定的实例只属于写入该
     * externalId 的调用方。归属不匹配时按不存在处理，避免暴露绑定信息。
     */
    fun ownsInstance(instanceExternalId: String, callerExternalId: String): Boolean =
        instanceExternalId.isEmpty() || instanceExternalId == callerExternalId

    /** Unbound instances stay visible; bound ones only to their owning external id. */
    fun visibleInstances(instances: List<AppInstance>, externalId: String): List<AppInstance> =
        instances.filter { it.externalId.isEmpty() || it.externalId == externalId }

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

    /** AMBIGUOUS_CARD 时带上候选分组（id + 名称），调用方才能补 groupId 重试，而不是死胡同。 */
    fun ambiguous(groups: List<Pair<String, String>>): String =
        JSONObject()
            .put("ok", false)
            .put("code", ExternalAccess.AMBIGUOUS_CARD)
            .put("message", "")
            .put("operationId", "")
            .put("groups", JSONArray(groups.map { (id, name) -> JSONObject().put("id", id).put("name", name) }))
            .toString()

    fun instances(rows: List<JSONObject>): String =
        JSONObject().put("ok", true).put("instances", JSONArray(rows)).toString()

    /**
     * ok 仅在终态成功（state == "done"）时为 true。
     * 挂起态（running / confirm）用 ok:false + code（RUNNING / CONFIRM_REQUIRED）+ operationId，
     * 调用方拿 operationId 轮询 getOperation，直到 done 或 error，避免把「已受理」当成「已完成」。
     */
    fun operation(id: String, state: String, message: String = "", code: String = ""): String {
        val resolvedCode = when {
            code.isNotEmpty() -> code
            state == "confirm" -> ExternalAccess.CONFIRM_REQUIRED
            state == "running" -> ExternalAccess.RUNNING
            state == "error" -> ExternalAccess.FAILED
            else -> ""
        }
        return JSONObject()
            .put("ok", state == "done")
            .put("operationId", id)
            .put("state", state)
            .put("message", message)
            .put("code", resolvedCode)
            .toString()
    }

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
