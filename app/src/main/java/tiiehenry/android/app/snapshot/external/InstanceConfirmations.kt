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
        val packageName: String,
        val userId: Int,
        val groupId: String,
        val instanceId: String,
        val instanceName: String,
        val account: String,
        val externalId: String,
        val card: ArchivedApp,
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    fun put(id: String, item: Pending) {
        pending[id] = item
    }

    fun peek(id: String): Pending? = pending[id]

    fun take(id: String): Pending? = pending.remove(id)

    fun ask(context: Context, id: String, instanceName: String, account: String, packageName: String) {
        val appContext = context.applicationContext
        val intent = Intent(appContext, InstanceConfirmActivity::class.java).putExtra(EXTRA_ID, id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val open = PendingIntent.getActivity(appContext, id.hashCode(), intent, flags)
        ensureChannel(appContext)
        val who = if (account.isEmpty()) instanceName else "$instanceName · $account"
        val notification = NotificationCompat.Builder(appContext, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(appContext.getString(R.string.instance_confirm_title))
            .setContentText(appContext.getString(R.string.instance_confirm_notification, who, packageName))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(appContext).notify(id.hashCode(), notification)
        } catch (e: SecurityException) {
            // 没有通知权限时仍尝试直接打开确认页
        }
        try {
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // Android 10 之后后台启动可能被拒绝，用户点通知进入确认页
        }
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
