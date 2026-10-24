package tiiehenry.android.app.snapshot.external

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import tiiehenry.android.app.snapshot.R
import tiiehenry.android.app.snapshot.group.ArchivedApp
import java.util.concurrent.ConcurrentHashMap

object InstanceConfirmations {
    data class Pending(
        val caller: CallerId,
        val packageName: String,
        val userId: Int,
        val groupId: String,
        val instanceId: String,
        val instanceName: String,
        val account: String,
        val externalId: String,
        val card: ArchivedApp,
        val createdAt: Long = System.currentTimeMillis(),
    )

    private val pending = ConcurrentHashMap<String, Pending>()
    private val lock = Any()

    /** Keep confirmation prompts bounded so an external caller cannot exhaust memory. */
    private const val MAX_PENDING = 16
    private const val PENDING_TTL_MS = 10 * 60 * 1000L

    /** @return false when the bounded prompt queue is full. */
    fun put(context: Context, id: String, item: Pending): Boolean {
        // Size check and insertion must be atomic; two callers can otherwise
        // both observe a free slot and exceed the bound.
        synchronized(lock) {
            prune(context)
            if (pending.size >= MAX_PENDING) return false
            pending[id] = item
            return true
        }
    }

    fun peek(id: String): Pending? {
        val item = pending[id] ?: return null
        if (System.currentTimeMillis() - item.createdAt > PENDING_TTL_MS) {
            if (pending.remove(id, item)) {
                // Do not leave the caller polling a prompt that can no longer be confirmed.
                InstanceGateway.complete(id, "error", "", ExternalAccess.CANCELLED)
            }
            return null
        }
        return item
    }

    fun take(id: String): Pending? = pending.remove(id)

    fun cancel(id: String, state: String, message: String, code: String) {
        if (take(id) == null) return
        InstanceGateway.complete(id, state, message, code)
    }

    private fun prune(context: Context) {
        val now = System.currentTimeMillis()
        pending.entries.removeIf { (id, item) ->
            if (now - item.createdAt <= PENDING_TTL_MS) return@removeIf false
            runCatching { dismiss(context, id) }
            InstanceGateway.complete(id, "error", "", ExternalAccess.CANCELLED)
            true
        }
    }

    /** @return true when at least one prompt (notification or activity) was shown. */
    fun ask(context: Context, id: String, instanceName: String, account: String, packageName: String): Boolean {
        val appContext = context.applicationContext
        val intent = Intent(appContext, InstanceConfirmActivity::class.java)
            .putExtra(EXTRA_ID, id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = runCatching {
            PendingIntent.getActivity(appContext, id.hashCode(), intent, flags)
        }.getOrNull()
        ensureChannel(appContext)
        val who = if (account.isEmpty()) instanceName else "$instanceName · $account"
        val notification = NotificationCompat.Builder(appContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(appContext.getString(R.string.instance_confirm_title))
            .setContentText(appContext.getString(R.string.instance_confirm_notification, who, packageName))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        var shown = false
        if (open != null) {
            try {
                NotificationManagerCompat.from(appContext).notify(id.hashCode(), notification)
                shown = true
            } catch (e: SecurityException) {
                // 没有通知权限时仍尝试直接打开确认页
            }
        }
        try {
            appContext.startActivity(intent)
            shown = true
        } catch (e: Exception) {
            // Android 10 之后后台启动可能被拒绝，用户点通知进入确认页
        }
        if (!shown) {
            cancel(id, "error", "", ExternalAccess.FAILED)
        }
        return shown
    }

    fun dismiss(context: Context, id: String) {
        NotificationManagerCompat.from(context.applicationContext).cancel(id.hashCode())
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.instance_confirm_title), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    const val EXTRA_ID = "confirmId"
    private const val CHANNEL = "instance_confirm"
}
