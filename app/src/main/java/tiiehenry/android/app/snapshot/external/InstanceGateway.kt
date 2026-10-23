package tiiehenry.android.app.snapshot.external

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import tiiehenry.android.app.snapshot.SnapshotApp
import tiiehenry.android.app.snapshot.group.ArchivedApp
import tiiehenry.android.app.snapshot.group.SnapGroup
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
        if (request.method == "getOperation") return operation(request.operationId)
        val denied = authorize(caller, request.packageName)
        if (denied != null) return denied
        return when (request.method) {
            "listInstances" -> list(request)
            "switchInstance" -> requestSwitch(request)
            "bindInstance", "unbindInstance" -> runBlocking { perform(request) }
            "createInstance", "updateInstance", "deleteInstance" -> begin(request)
            else -> InstanceProtocol.error(ExternalAccess.FAILED, "unknown method")
        }
    }

    private fun authorize(caller: CallerId?, targetPackage: String): String? {
        val state = store.read()
        val code = ExternalAccess.decide(state, caller, targetPackage)
        if (code == ExternalAccess.CONSENT_REQUIRED && caller != null && caller !in state.approved) {
            store.update { current ->
                if (caller in current.approved || caller in current.pending) current
                else current.copy(pending = current.pending + caller)
            }
        }
        return code?.let { InstanceProtocol.error(it) }
    }

    private fun list(request: InstanceRequest): String = runBlocking {
        val found = locate(request)
        if (found.error != null) return@runBlocking found.error
        val rows = found.card!!.second.let { app ->
            InstanceStore(context, app, NoProgress).list().map {
                InstanceProtocol.instanceRow(it.id, it.name, it.account, it.note, it.externalId, it.updatedAt)
            }
        }
        InstanceProtocol.instances(rows)
    }

    private fun requestSwitch(request: InstanceRequest): String = runBlocking {
        val found = locate(request)
        if (found.error != null) return@runBlocking found.error
        val app = found.card!!.second
        val instance = InstanceStore(context, app, NoProgress).list()
            .find { it.id == request.instanceId }
            ?: return@runBlocking InstanceProtocol.error(ExternalAccess.INSTANCE_NOT_FOUND)
        val id = UUID.randomUUID().toString()
        InstanceConfirmations.put(
            id,
            InstanceConfirmations.Pending(
                packageName = request.packageName,
                userId = request.userId,
                groupId = found.card!!.first.id,
                instanceId = instance.id,
                instanceName = instance.name,
                account = instance.account,
                externalId = instance.externalId,
                card = app,
            ),
        )
        operations[id] = InstanceProtocol.operation(id, "confirm", instance.name, ExternalAccess.CONFIRM_REQUIRED)
        InstanceConfirmations.ask(context, id, instance.name, instance.account, request.packageName)
        operations[id]!!
    }

    private fun begin(request: InstanceRequest): String {
        val id = UUID.randomUUID().toString()
        operations[id] = InstanceProtocol.operation(id, "running")
        thread(name = "instance-$id") {
            val result = try {
                runBlocking(Dispatchers.IO) { perform(request) }
            } catch (e: Exception) {
                InstanceProtocol.error(ExternalAccess.FAILED, e.message ?: "")
            }
            operations[id] = if (result.contains("\"ok\":false")) {
                InstanceProtocol.operation(id, "error", message = result, code = ExternalAccess.FAILED)
            } else {
                InstanceProtocol.operation(id, "done", message = result)
            }
        }
        return InstanceProtocol.operation(id, "running")
    }

    private suspend fun perform(request: InstanceRequest): String {
        val found = locate(request)
        if (found.error != null) return found.error
        val app = found.card!!.second
        val progress = NoProgress
        val worker = InstanceStore(context, app, progress)
        val text = try {
            when (request.method) {
                "createInstance" -> worker.create(request.name, request.account, request.note, request.externalId)
                "updateInstance" -> worker.update(request.instanceId)
                "deleteInstance" -> worker.delete(request.instanceId)
                "bindInstance" -> bind(worker, request, request.externalId)
                "unbindInstance" -> bind(worker, request, "")
                else -> InstanceProtocol.error(ExternalAccess.FAILED, "unknown method")
            }
        } catch (e: InstanceException) {
            return InstanceProtocol.error(ExternalAccess.FAILED, e.message ?: "")
        }
        return if (text.startsWith("{")) text else InstanceProtocol.okMessage(text)
    }

    private suspend fun bind(worker: InstanceStore, request: InstanceRequest, externalId: String): String {
        val current = worker.list().find { it.id == request.instanceId }
            ?: return InstanceProtocol.error(ExternalAccess.INSTANCE_NOT_FOUND)
        return worker.edit(current.id, current.name, current.account, current.note, externalId)
    }

    private fun operation(id: String): String =
        operations[id] ?: InstanceProtocol.error(ExternalAccess.FAILED, "unknown operation")

    private suspend fun locate(request: InstanceRequest): Located {
        if (request.packageName.isEmpty()) {
            return Located(null, InstanceProtocol.error(ExternalAccess.FAILED, "packageName"))
        }
        val cards = cards(request.packageName, request.userId, request.groupId)
        return when {
            cards.isEmpty() -> Located(null, InstanceProtocol.error(ExternalAccess.APP_NOT_IN_GROUP))
            cards.size > 1 -> Located(null, InstanceProtocol.error(ExternalAccess.AMBIGUOUS_CARD))
            else -> Located(cards.first(), null)
        }
    }

    private suspend fun cards(packageName: String, userId: Int, groupId: String): List<Pair<SnapGroup, ArchivedApp>> {
        val app = SnapshotApp.getInstance()
        val repo = AppDataRepository.getInstance()
        if (repo.groupList.value.isNullOrEmpty()) {
            withContext(Dispatchers.IO) {
                repo.loadGroups(app, app.fileSystem, app.appManager)
            }
        }
        return repo.groupList.value.orEmpty()
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
        private val operations = ConcurrentHashMap<String, String>()

        fun complete(id: String, state: String, message: String, code: String = "") {
            operations[id] = InstanceProtocol.operation(id, state, message, code)
        }
    }
}
