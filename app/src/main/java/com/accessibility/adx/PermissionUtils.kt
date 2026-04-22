package com.accessibility.adx

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import com.accessibility.adx.AppOpsConstants.OPSTR_VIBRATE
import com.accessibility.adx.AppOpsConstants.OP_VIBRATE

/**
 * 权限检查工具类
 * 提供各类权限的检查和申请方法
 */
object PermissionUtils {

    /**
     * 检查无障碍服务是否启用
     */
    fun isAccessibilityServiceEnabled(context: Context, serviceClassName: String): Boolean {
        val accessibilityManager = ContextCompat.getSystemService(
            context, AccessibilityManager::class.java
        ) ?: return false

        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )

        // 提取简化的服务名称（只取类名部分）
        val simpleClassName = serviceClassName.substringAfterLast(".")
        
        return enabledServices.any { service ->
            // 检查多种匹配方式
            service.id.contains(simpleClassName) ||
            service.id.contains(serviceClassName) ||
            service.resolveInfo.serviceInfo.name.contains(simpleClassName)
        }
    }

    /**
     * 打开无障碍服务设置页面
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    /**
     * 检查悬浮窗权限
     */
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * 打开悬浮窗权限设置页面
     */
    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    /**
     * 检查通知权限（Android 13+）
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * 检查振动权限
     */
    fun hasVibratePermission(context: Context): Boolean {
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    OPSTR_VIBRATE,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    OPSTR_VIBRATE,
                    Process.myUid(),
                    context.packageName
                )
            }
            return mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            return true
        }
    }

    /**
     * 检查是否所有必需权限都已授予
     */
    fun hasAllRequiredPermissions(context: Context, serviceClassName: String): Boolean {
        return isAccessibilityServiceEnabled(context, serviceClassName) &&
                canDrawOverlays(context)
    }
}
