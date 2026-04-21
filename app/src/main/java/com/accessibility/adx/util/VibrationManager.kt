package com.accessibility.adx.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.getSystemService
import com.accessibility.adx.PreferencesManager

/**
 * 震动管理器
 * 控制设备震动反馈
 */
class VibrationManager(private val context: Context) {

    private val prefs = PreferencesManager.getInstance(context)
    
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService<VibratorManager>()?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    /**
     * 执行震动
     */
    fun vibrate() {
        if (!prefs.isVibrationEnabled) return
        if (vibrator?.hasVibrator() != true) return

        val duration = prefs.vibrationDuration.toLong()
        val amplitude = calculateAmplitude()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createOneShot(
                    duration,
                    amplitude
                )
                vibrator?.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(duration)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 根据强度设置计算振幅
     * 强度为0-100，对应振幅1-255
     */
    private fun calculateAmplitude(): Int {
        val intensity = prefs.vibrationIntensity
        return if (intensity == 0) {
            1
        } else {
            // 将0-100映射到1-255
            (1 + (intensity / 100.0f * 254)).toInt().coerceIn(1, 255)
        }
    }

    /**
     * 取消震动
     */
    fun cancel() {
        vibrator?.cancel()
    }

    /**
     * 检查设备是否有振动功能
     */
    fun hasVibrator(): Boolean {
        return vibrator?.hasVibrator() == true
    }
}
