package com.accessibility.adx.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.accessibility.adx.AdXApplication
import com.accessibility.adx.PreferencesManager
import com.accessibility.adx.R
import com.accessibility.adx.ui.MainActivity
import com.accessibility.adx.util.NotificationHelper
import com.accessibility.adx.Constants.ACTION_START
import com.accessibility.adx.Constants.ACTION_STOP
import com.accessibility.adx.Constants.ACTION_DETECTION_STATUS_CHANGED

/**
 * 前台检测服务
 * 保持应用在后台运行，提供检测功能
 */
class DetectionService : Service() {

    companion object {
        private const val TAG = "DetectionService"
        
        @Volatile
        var isRunning = false
            private set
    }

    private lateinit var prefs: PreferencesManager
    private lateinit var localBroadcastManager: LocalBroadcastManager

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager.getInstance(this)
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                // 启动前台服务
                startForegroundService()
            }
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        broadcastStatus(false)
    }

    /**
     * 启动前台服务
     */
    private fun startForegroundService() {
        val notification = createNotification()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.getNotificationId(),
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NotificationHelper.getNotificationId(), notification)
        }
        
        isRunning = true
        broadcastStatus(true)
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        // 打开主界面的Intent
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 停止服务的Intent
        val stopIntent = Intent(this, DetectionService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AdXApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_accessibility)
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                R.drawable.ic_close,
                getString(R.string.btn_stop),
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /**
     * 广播状态变化
     */
    private fun broadcastStatus(running: Boolean) {
        val intent = Intent(ACTION_DETECTION_STATUS_CHANGED).apply {
            putExtra("running", running)
        }
        localBroadcastManager.sendBroadcast(intent)
    }

    /**
     * 更新通知内容
     */
    fun updateNotification(count: Int) {
        if (!isRunning) return
        
        val notification = NotificationCompat.Builder(this, AdXApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.float_count, count))
            .setSmallIcon(R.drawable.ic_accessibility)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NotificationHelper.getNotificationId(), notification)
    }
}

