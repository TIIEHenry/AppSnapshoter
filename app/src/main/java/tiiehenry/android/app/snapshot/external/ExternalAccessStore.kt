package tiiehenry.android.app.snapshot.external

import android.content.Context
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject

class ExternalAccessStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(): ExternalAccessState {
        val raw = prefs.getString(KEY, null) ?: return ExternalAccessState()
        return try {
            val parsed = JSON.parseObject(raw) ?: return ExternalAccessState()
            decode(parsed)
        } catch (e: Exception) {
            ExternalAccessState()
        }
    }

    fun write(state: ExternalAccessState) {
        prefs.edit().putString(KEY, encode(state).toString()).apply()
    }

    fun update(block: (ExternalAccessState) -> ExternalAccessState): ExternalAccessState {
        val next = block(read())
        write(next)
        return next
    }

    companion object {
        private const val PREFS = "external_instance_access"
        private const val KEY = "state"

        fun encode(state: ExternalAccessState): JSONObject = JSONObject()
            .fluentPut("enabled", state.enabled)
            .fluentPut("targetMode", state.targetMode.name)
            .fluentPut("targetPackages", JSONArray(state.targetPackages.toList()))
            .fluentPut("mcpEnabled", state.mcpEnabled)
            .fluentPut("mcpTokenHash", state.mcpTokenHash)
            .fluentPut("approved", callersJson(state.approved))
            .fluentPut("pending", callersJson(state.pending))

        fun decode(obj: JSONObject): ExternalAccessState = ExternalAccessState(
            enabled = obj.getBooleanValue("enabled"),
            approved = callers(obj.getJSONArray("approved")),
            pending = callers(obj.getJSONArray("pending")),
            targetMode = if (obj.getString("targetMode") == TargetMode.ALL.name) {
                TargetMode.ALL
            } else {
                TargetMode.WHITELIST
            },
            targetPackages = stringSet(obj.getJSONArray("targetPackages")),
            mcpEnabled = obj.getBooleanValue("mcpEnabled"),
            mcpTokenHash = obj.getString("mcpTokenHash").orEmpty(),
        )

        private fun callersJson(callers: Set<CallerId>): JSONArray {
            val array = JSONArray()
            callers.forEach {
                array.add(JSONObject().fluentPut("packageName", it.packageName).fluentPut("certSha256", it.certSha256))
            }
            return array
        }

        private fun callers(array: JSONArray?): Set<CallerId> {
            if (array == null) return emptySet()
            return buildSet {
                for (i in 0 until array.size) {
                    val item = array.getJSONObject(i) ?: continue
                    val name = item.getString("packageName").orEmpty()
                    val cert = item.getString("certSha256").orEmpty()
                    if (name.isNotEmpty() && cert.isNotEmpty()) add(CallerId(name, cert))
                }
            }
        }

        private fun stringSet(array: JSONArray?): Set<String> {
            if (array == null) return emptySet()
            return buildSet {
                for (i in 0 until array.size) {
                    val value = array.getString(i).orEmpty()
                    if (value.isNotEmpty()) add(value)
                }
            }
        }
    }
}
