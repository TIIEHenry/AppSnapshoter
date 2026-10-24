package tiiehenry.android.app.snapshot.external

import android.content.Context
import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import com.tencent.mmkv.MMKV

class ExternalAccessStore(context: Context) {
    private val mmkv = MMKV.mmkvWithID(MMKV_ID)

    init {
        migrateLegacy(context.applicationContext)
    }

    fun read(): ExternalAccessState {
        val raw = mmkv.decodeString(KEY, null) ?: return ExternalAccessState()
        return try {
            val parsed = JSON.parseObject(raw) ?: return ExternalAccessState()
            decode(parsed)
        } catch (e: Exception) {
            ExternalAccessState()
        }
    }

    fun write(state: ExternalAccessState) {
        mmkv.encode(KEY, encode(state).toString())
    }

    fun update(block: (ExternalAccessState) -> ExternalAccessState): ExternalAccessState {
        // MMKV instances share the same file; use the process-wide lock so
        // concurrent read-modify-write calls cannot drop approvals.
        synchronized(lock) {
            val next = block(read())
            write(next)
            return next
        }
    }

    companion object {
        private val lock = Any()
        private const val MMKV_ID = "external_instance_access"
        private const val KEY = "state"
        private const val LEGACY_PREFS = "external_instance_access"

        /** 一次性迁移旧版 SharedPreferences；迁移后只读写 MMKV。 */
        private fun migrateLegacy(context: Context) {
            synchronized(lock) {
                val mmkv = MMKV.mmkvWithID(MMKV_ID)
                if (mmkv.containsKey(KEY)) return
                val prefs = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                val raw = prefs.getString(KEY, null) ?: return
                mmkv.encode(KEY, raw)
                prefs.edit().remove(KEY).apply()
            }
        }

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
