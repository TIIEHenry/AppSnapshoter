package tiiehenry.android.app.snapshot.instance

import com.alibaba.fastjson2.JSON
import java.util.UUID

data class AppInstance(
    val id: String,
    val name: String,
    val account: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class InstanceInfo(
    val name: String,
    val account: String,
    val note: String,
)

class InstanceException(message: String) : Exception(message)

object InstanceNames {
    private val idPattern = Regex("^[a-zA-Z0-9_-]{1,64}$")

    fun isSlotId(name: String): Boolean = idPattern.matches(name)

    /** `instances/` 下的正式目录名就是 id。临时目录带后缀，不另算一份备份。 */
    fun baseId(dirName: String): String? {
        if (isSlotId(dirName)) return dirName
        val id = when {
            dirName.endsWith(InstancePaths.COMMITTING_SUFFIX) ->
                dirName.removeSuffix(InstancePaths.COMMITTING_SUFFIX)
            dirName.endsWith(InstancePaths.PREVIOUS_SUFFIX) ->
                dirName.removeSuffix(InstancePaths.PREVIOUS_SUFFIX)
            else -> return null
        }
        return id.takeIf { isSlotId(it) }
    }

    fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    fun check(
        nameRaw: String,
        accountRaw: String,
        noteRaw: String,
        existing: List<AppInstance>,
        exceptId: String?,
    ): InstanceInfoCheck {
        val name = normalize(nameRaw, max = 40, required = true)
            ?: return InstanceInfoCheck.NameInvalid
        val account = normalize(accountRaw, max = 80, required = false)
            ?: return InstanceInfoCheck.AccountInvalid
        val note = normalize(noteRaw, max = 200, required = false)
            ?: return InstanceInfoCheck.NoteInvalid
        val others = existing.filter { it.id != exceptId }
        if (others.any { it.name == name }) return InstanceInfoCheck.NameTaken
        if (account.isNotEmpty() && others.any { it.account == account }) {
            return InstanceInfoCheck.AccountTaken
        }
        return InstanceInfoCheck.Ok(InstanceInfo(name, account, note))
    }

    private fun normalize(raw: String, max: Int, required: Boolean): String? {
        val text = raw.trim()
        if (text.isEmpty()) return if (required) null else ""
        if (text.length > max) return null
        if (text.contains('/') || text.contains('\\') || text.contains('\n')) return null
        return text
    }
}

sealed class InstanceInfoCheck {
    data class Ok(val info: InstanceInfo) : InstanceInfoCheck()
    data object NameInvalid : InstanceInfoCheck()
    data object AccountInvalid : InstanceInfoCheck()
    data object NoteInvalid : InstanceInfoCheck()
    data object NameTaken : InstanceInfoCheck()
    data object AccountTaken : InstanceInfoCheck()
}

object InstanceMeta {
    fun encode(instance: AppInstance): String {
        return JSON.toJSONString(
            mapOf(
                "id" to instance.id,
                "name" to instance.name,
                "account" to instance.account,
                "note" to instance.note,
                "createdAt" to instance.createdAt,
                "updatedAt" to instance.updatedAt,
            )
        )
    }

    fun decode(json: String): AppInstance? {
        val obj = JSON.parseObject(json) ?: return null
        val id = obj.getString("id") ?: return null
        val name = obj.getString("name") ?: return null
        if (!InstanceNames.isSlotId(id)) return null
        if (name.isEmpty()) return null
        return AppInstance(
            id = id,
            name = name,
            account = obj.getString("account").orEmpty(),
            note = obj.getString("note").orEmpty(),
            createdAt = obj.getLongValue("createdAt"),
            updatedAt = obj.getLongValue("updatedAt"),
        )
    }
}
