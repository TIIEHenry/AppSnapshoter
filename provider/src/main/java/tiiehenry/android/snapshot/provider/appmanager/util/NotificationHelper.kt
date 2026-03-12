package tiiehenry.android.snapshot.provider.appmanager.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * 通知工具类，提供通知相关的功能
 */
object NotificationHelper {
    private const val CHANNEL_ID = "app_snapshot_channel"
    private const val CHANNEL_NAME = "App Snapshoter"
    const val NOTIFICATION_ID_APPS_UPDATE_WORKER = 1001
    
    /**
     * 获取通知管理器
     */
    fun getNotificationManager(context: Context): NotificationManager {
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    
    /**
     * 获取通知构建器
     */
    fun getNotificationBuilder(context: Context): NotificationCompat.Builder {
        createNotificationChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(false)
    }
    
    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "App Snapshoter notifications"
            }
            getNotificationManager(context).createNotificationChannel(channel)
        }
    }
    
    /**
     *显示通知
     */
    fun showNotification(context: Context, id: Int, notification: Notification) {
        getNotificationManager(context).notify(id, notification)
    }
    
    /**
     *取通知
     */
    fun cancelNotification(context: Context, id: Int) {
        getNotificationManager(context).cancel(id)
    }
}