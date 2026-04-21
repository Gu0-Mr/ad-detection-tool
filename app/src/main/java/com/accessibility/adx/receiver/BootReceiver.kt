package com.accessibility.adx.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.accessibility.adx.PreferencesManager
import com.accessibility.adx.PermissionUtils
import com.accessibility.adx.service.AdDetectionService
import com.accessibility.adx.service.DetectionService

/**
 * 开机广播接收器
 * 设备启动后自动启动检测服务
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        if (context == null) return

        val prefs = PreferencesManager.getInstance(context)
        
        // 检查是否应该自动启动
        if (!prefs.isAccessibilityEnabled) return
        
        // 检查无障碍服务是否真的启用了
        if (!PermissionUtils.isAccessibilityServiceEnabled(context, AdDetectionService::class.java.name)) {
            return
        }

        // 启动检测服务
        val serviceIntent = Intent(context, DetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // 如果启用了悬浮窗，也启动悬浮窗服务
        if (prefs.isFloatWindowEnabled) {
            val floatIntent = Intent(context, com.accessibility.adx.service.FloatWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(floatIntent)
            } else {
                context.startService(floatIntent)
            }
        }
    }
}
