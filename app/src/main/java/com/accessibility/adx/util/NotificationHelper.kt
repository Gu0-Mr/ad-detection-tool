package com.accessibility.adx.util

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.accessibility.adx.AdXApplication
import com.accessibility.adx.R
import com.accessibility.adx.ui.MainActivity

/**
 * 通知工具类
 * 创建和管理前台服务通知
 */
object NotificationHelper {

    private const val NOTIFICATION_ID = 1001

    /**
     * 创建前台服务通知
     */
    fun createForegroundNotification(service: Service): Notification {
        // 创建点击通知打开应用的Intent
        val openIntent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val openPendingIntent = PendingIntent.getActivity(
            service,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 创建停止服务的Intent
        val stopIntent = Intent(service, service::class.java).apply {
            action = "ACTION_STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            service,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(service, AdXApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(service.getString(R.string.notification_title))
            .setContentText(service.getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_accessibility)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                R.drawable.ic_close,
                service.getString(R.string.btn_stop),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    /**
     * 获取通知ID
     */
    fun getNotificationId(): Int = NOTIFICATION_ID
}
