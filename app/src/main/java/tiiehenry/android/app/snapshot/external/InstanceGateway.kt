package tiiehenry.android.app.snapshot.external

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.SnapGroup
import tiiehenry.android.app.snapshot.instance.InstanceError
import tiiehenry.android.app.snapshot.instance.InstanceException
import tiiehenry.android.app.snapshot.instance.InstanceStore
import tiiehenry.android.app.snapshot.main.launch.makearchive.progress.IItemProgressDialog
import tiiehenry.android.app.snapshot.repository.AppDataRepository
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class InstanceGateway(private val context: Context) {
    private val store = ExternalAccessStore(context)

    fun call(caller: CallerId?, requestJson: String): String {
        val request = InstanceProtocol.request(requestJson)
            ?: return InstanceProtocol.error(ExternalAccess.FAILED, "bad request")
        if (request.method == "getOperation") return operation(caller, request.operationId)
        val denied = authorize(caller, request.packageName)
        if (denied != null) return denied
        return when (request.method) {
            "listInstances" -> list(request)
            "switchInstance" -> requestSwitch(request, caller)
            "bindInstance", "unbindInstance" -> runBlocking { perform(request) }
            "createInstance", "updateInstance", "deleteInstance" -> begin(request, caller)
            else -> InstanceProtocol.error(ExternalAccess.FAILED, "unknown method")
        }
    }

    private fun authorize(caller: CallerId?, targetPackage: String): String? {
        val state = store.read()
        val code = ExternalAccess.decide(state, caller, targetPackage)
        if (code == ExternalAccess.CONSENT_REQUIRED && caller != null && caller !in state.approved) {
            store.update { current -> ExternalAccess.addPending(current, caller) }
        }
        return code?.let { InstanceProtocol.error(it) }
    }

    private fun list(request: InstanceRequest): String = runBlocking {
        try {
            val found = locate(request)
            if (found.error != null) return@runBlocking found.error
            val rows = found.card!!.second.let { app ->
                InstanceStore(context, app, NoProgress).list()
                    .let { InstanceProtocol.visibleInstances(it, request.externalId) }
                    .map {
                        InstanceProtocol.instanceRow(
                            it.id, it.name, it.account, it.note, it.externalId, it.updatedAt,
                        )
                    }
            }
            InstanceProtocol.instances(rows)
        } catch (e: Exception) {
            InstanceProtocol.error(ExternalAccess.FAILED, "list failed")
        }
    }

    private fun requestSwitch(request: InstanceRequest, caller: CallerId?): String = runBlocking {
        try {
            if (caller == null) return@runBlocking InstanceProtocol.error(ExternalAccess.CONSENT_REQUIRED)
            val found = locate(request)
            if (found.error != null) return@runBlocking found.error
            val card = found.card ?: return@runBlocking InstanceProtocol.error(ExternalAccess.FAILED)
            val app = card.second
            val instance = InstanceStore(context, app, NoProgress).list()
                .find { it.id == request.instanceId }
                ?: return@runBlocking InstanceProtocol.error(ExternalAccess.INSTANCE_NOT_FOUND)
            // Fail before showing a prompt the caller could never complete. The
            // restore re-checks ownership under the package lock.
            if (!InstanceProtocol.ownsInstance(instance.externalId, request.externalId)) {
                return@runBlocking InstanceProtocol.error(ExternalAccess.INSTANCE_NOT_FOUND)
            }
            val id = UUID.randomUUID().toString()
            val accepted = InstanceConfirmations.put(
                context,
                id,
                InstanceConfirmations.Pending(
                    caller = caller,
                    packageName = request.packageName,
                    userId = request.userId,
                    groupId = card.first.id,
                    instanceId = instance.id,
                    instanceName = instance.name,
                    account = instance.account,
                    externalId = instance.externalId,
                    card = app,
                ),
            )
            if (!accepted) return@runBlocking InstanceProtocol.error(ExternalAccess.BUSY)
            val result = InstanceProtocol.operation(id, "confirm", instance.name, ExternalAccess.CONFIRM_REQUIRED)
            putOperation(id, caller, result)
            if (InstanceConfirmations.ask(context, id, instance.name, instance.account, request.packageName)) {
                result
            } else {
                InstanceConfirmations.cancel(id, "error", "", ExternalAccess.FAILED)
                InstanceProtocol.operation(id, "error", "", ExternalAccess.FAILED)
            }
        } catch (e: Exception) {
            InstanceProtocol.error(ExternalAccess.FAILED, "switch failed")
        }
    }

    private fun begin(request: InstanceRequest, caller: CallerId?): String {
        val id = UUID.randomUUID().toString()
        putOperation(id, caller, InstanceProtocol.operation(id, "running"))
        thread(name = "instance-$id") {
            val result = try {
                runBlocking(Dispatchers.IO) { perform(request) }
            } catch (e: Exception) {
                InstanceProtocol.error(ExternalAccess.FAILED, "operation failed")
            }
            putOperation(id, caller, resultOperation(id, result))
        }
        return InstanceProtocol.operation(id, "running")
    }

    private suspend fun perform(request: InstanceRequest): String {
        return try {
            val found = locate(request)
            if (found.error != null) return found.error
            val app = found.card!!.second
            val progress = NoProgress
            val worker = InstanceStore(context, app, progress)
            when (request.method) {
                "createInstance" -> worker.create(request.name, request.account, request.note, request.externalId)
                "updateInstance" -> updateOwned(worker, request)
                "deleteInstance" -> deleteOwned(worker, request)
                "bindInstance" -> bind(worker, request, request.externalId)
                "unbindInstance" -> bind(worker, request, "")
                else -> InstanceProtocol.error(ExternalAccess.FAILED, "unknown method")
            }.let { text -> if (text.startsWith("{")) text else InstanceProtocol.okMessage(text) }
        } catch (e: InstanceException) {
            InstanceProtocol.error(publicCode(e.code), publicMessage(e))
        } catch (e: Exception) {
            // Never forward raw exception text: it can contain internal paths.
            InstanceProtocol.error(ExternalAccess.FAILED, "operation failed")
        }
    }

    /** Only expose the stable codes documented for external callers. */
    private fun publicCode(code: String): String = when (code) {
        InstanceError.NOT_FOUND -> ExternalAccess.INSTANCE_NOT_FOUND
        InstanceError.BUSY -> ExternalAccess.BUSY
        else -> ExternalAccess.FAILED
    }

    /** Internal slot errors carry no user copy; map them before crossing the IPC boundary. */
    private fun publicMessage(e: InstanceException): String = when (e.code) {
        InstanceError.META_WRITE_FAILED,
        InstanceError.SLOT_INVALID,
        -> context.getString(R.string.instance_save_failed)
        InstanceError.RESTORE_FAILED -> context.getString(R.string.instance_restore_failed)
        InstanceError.DELETE_FAILED -> context.getString(R.string.instance_delete_failed)
        else -> e.message.orEmpty()
    }

    private suspend fun bind(worker: InstanceStore, request: InstanceRequest, writeExternalId: String): String {
        val current = worker.list().find { it.id == request.instanceId }
            ?: return InstanceProtocol.error(ExternalAccess.INSTANCE_NOT_FOUND)
        // 绑定归属：已绑定的实例只允许同一 externalId 的调用方改写或清空，
        // 防止一个调用方偷换或抹掉另一个调用方的绑定。不匹配按不存在处理，不暴露归属。
        if (!InstanceProtocol.ownsInstance(current.externalId, request.externalId)) {
            return InstanceProtocol.error(ExternalAccess.INSTANCE_NOT_FOUND)
        }
        return worker.edit(current.id, current.name, current.account, current.note, writeExternalId)
    }

    private suspend fun updateOwned(worker: InstanceStore, request: InstanceRequest): String {
        return worker.update(request.instanceId, request.externalId)
    }

    private suspend fun deleteOwned(worker: InstanceStore, request: InstanceRequest): String {
        return worker.delete(request.instanceId, request.externalId)
    }

    private fun operation(caller: CallerId?, id: String): String {
        val denied = ExternalAccess.decideOperation(store.read(), caller)
            ?.let { InstanceProtocol.error(it) }
        if (denied != null) return denied
        val entry = operations[id]
        if (entry == null || entry.caller != callerKey(caller)) {
            return InstanceProtocol.error(ExternalAccess.FAILED, "unknown operation")
        }
        if (System.currentTimeMillis() - entry.createdAt > OPERATION_TTL_MS) {
            operations.remove(id, entry)
            return InstanceProtocol.error(ExternalAccess.FAILED, "unknown operation")
        }
        return entry.result
    }

    private fun resultOperation(id: String, result: String): String {
        val parsed = runCatching { org.json.JSONObject(result) }.getOrNull()
            ?: return InstanceProtocol.operation(id, "error", "", ExternalAccess.FAILED)
        if (parsed.optBoolean("ok", false)) {
            return InstanceProtocol.operation(id, "done", parsed.optString("message"))
        }
        return InstanceProtocol.operation(
            id,
            "error",
            parsed.optString("message"),
            parsed.optString("code").ifEmpty { ExternalAccess.FAILED },
        )
    }

    private suspend fun locate(request: InstanceRequest): Located {
        if (request.packageName.isEmpty()) {
            return Located(null, InstanceProtocol.error(ExternalAccess.FAILED, "packageName"))
        }
        val cards = cards(request.packageName, request.userId, request.groupId)
        return when {
            cards.isEmpty() -> Located(null, InstanceProtocol.error(ExternalAccess.APP_NOT_IN_GROUP))
            cards.size > 1 -> Located(
                null,
                InstanceProtocol.ambiguous(cards.map { it.first.id to it.first.name }.distinct()),
            )
            else -> Located(cards.first(), null)
        }
    }

    private suspend fun cards(packageName: String, userId: Int, groupId: String): List<Pair<SnapGroup, ArchivedApp>> {
        val app = SnapshotApp.getInstance()
        val repo = AppDataRepository.getInstance()
        // Use the repository's locked snapshot, not groupList.value: postValue is
        // asynchronous and can still expose the previous list.
        val groups = repo.loadedGroupsSnapshot().ifEmpty {
            withContext(Dispatchers.IO) {
                repo.loadGroups(app, app.fileSystem, app.appManager)
            }
        }
        return groups
            .filter { it.userId == userId && (groupId.isEmpty() || it.id == groupId) }
            .flatMap { group ->
                synchronized(group.apps) { group.apps.toList() }.mapNotNull { item ->
                    val name = try {
                        item.appInfo.packageName
                    } catch (e: UninitializedPropertyAccessException) {
                        null
                    }
                    if (name == packageName) group to item else null
                }
            }
    }

    private data class Located(val card: Pair<SnapGroup, ArchivedApp>?, val error: String?)

    private object NoProgress : IItemProgressDialog {
        override fun setItemMessage(message: String) {}
        override fun setItemStatus(message: String) {}
        override fun setItemProgress(progress: Int) {}
        override fun setCurrentItem(item: String) {}
        override fun showItem() {}
        override fun post(runnable: Runnable) {}
        override fun dismissItem() {}
        override fun setItemException(e: Exception) {}
    }

    companion object {
        private data class OperationEntry(val caller: String, val result: String, val createdAt: Long)

        private val operations = ConcurrentHashMap<String, OperationEntry>()

        /** Keep long-running operations queryable for a while, but do not grow without bound. */
        private const val MAX_OPERATIONS = 128
        private const val OPERATION_TTL_MS = 24 * 60 * 60 * 1000L

        private fun callerKey(caller: CallerId?): String =
            caller?.let { "${it.packageName}:${it.certSha256}" }.orEmpty()

        fun complete(id: String, state: String, message: String, code: String = "") {
            val existing = operations[id] ?: return
            operations[id] = existing.copy(
                result = InstanceProtocol.operation(id, state, message, code),
                createdAt = System.currentTimeMillis(),
            )
        }

        fun forget(id: String) {
            operations.remove(id)
        }

        private fun putOperation(id: String, caller: CallerId?, result: String) {
            val now = System.currentTimeMillis()
            operations.entries.removeIf { now - it.value.createdAt > OPERATION_TTL_MS }
            if (operations.size >= MAX_OPERATIONS) {
                operations.entries
                    .minByOrNull { it.value.createdAt }
                    ?.let { operations.remove(it.key, it.value) }
            }
            operations[id] = OperationEntry(callerKey(caller), result, now)
        }
    }
}
