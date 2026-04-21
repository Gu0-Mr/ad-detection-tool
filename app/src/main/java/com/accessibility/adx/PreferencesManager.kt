package com.accessibility.adx

import android.content.Context
import android.content.SharedPreferences

/**
 * SharedPreferences管理类
 * 统一管理应用所有配置项
 */
class PreferencesManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "adx_preferences"
        
        // Key定义
        private const val KEY_ACCESSIBILITY_ENABLED = "accessibility_enabled"
        private const val KEY_FLOAT_WINDOW_ENABLED = "float_window_enabled"
        private const val KEY_VIBRATION_ENABLED = "vibration_enabled"
        private const val KEY_SOUND_ENABLED = "sound_enabled"
        private const val KEY_VIBRATION_INTENSITY = "vibration_intensity"
        private const val KEY_VIBRATION_DURATION = "vibration_duration"
        private const val KEY_SOUND_VOLUME = "sound_volume"
        private const val KEY_TOTAL_COUNT = "total_count"
        private const val KEY_TODAY_COUNT = "today_count"
        private const val KEY_LAST_DATE = "last_date"
        
        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    // ==================== 无障碍服务 ====================
    var isAccessibilityEnabled: Boolean
        get() = prefs.getBoolean(KEY_ACCESSIBILITY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ACCESSIBILITY_ENABLED, value).apply()

    // ==================== 悬浮窗 ====================
    var isFloatWindowEnabled: Boolean
        get() = prefs.getBoolean(KEY_FLOAT_WINDOW_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_FLOAT_WINDOW_ENABLED, value).apply()

    // ==================== 震动 ====================
    var isVibrationEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATION_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATION_ENABLED, value).apply()

    var vibrationIntensity: Int
        get() = prefs.getInt(KEY_VIBRATION_INTENSITY, 50)
        set(value) = prefs.edit().putInt(KEY_VIBRATION_INTENSITY, value.coerceIn(0, 100)).apply()

    var vibrationDuration: Int
        get() = prefs.getInt(KEY_VIBRATION_DURATION, 100)
        set(value) = prefs.edit().putInt(KEY_VIBRATION_DURATION, value.coerceIn(50, 500)).apply()

    // ==================== 声音 ====================
    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var soundVolume: Int
        get() = prefs.getInt(KEY_SOUND_VOLUME, 80)
        set(value) = prefs.edit().putInt(KEY_SOUND_VOLUME, value.coerceIn(0, 100)).apply()

    // ==================== 统计数据 ====================
    var totalCount: Int
        get() = prefs.getInt(KEY_TOTAL_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_TOTAL_COUNT, value).apply()

    var todayCount: Int
        get() {
            // 检查日期是否过期
            checkAndResetDailyCount()
            return prefs.getInt(KEY_TODAY_COUNT, 0)
        }
        private set(value) = prefs.edit().putInt(KEY_TODAY_COUNT, value).apply()

    private var lastDate: String
        get() = prefs.getString(KEY_LAST_DATE, "") ?: ""
        private set(value) = prefs.edit().putString(KEY_LAST_DATE, value).apply()

    /**
     * 检查并重置每日计数
     */
    private fun checkAndResetDailyCount() {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            .format(java.util.Date())
        
        if (lastDate != today) {
            // 新的一天，重置计数
            prefs.edit()
                .putInt(KEY_TODAY_COUNT, 0)
                .putString(KEY_LAST_DATE, today)
                .apply()
        }
    }

    /**
     * 增加检测计数
     */
    fun incrementCount() {
        checkAndResetDailyCount()
        totalCount++
        todayCount++
    }

    /**
     * 清除统计数据
     */
    fun clearStatistics() {
        prefs.edit()
            .putInt(KEY_TOTAL_COUNT, 0)
            .putInt(KEY_TODAY_COUNT, 0)
            .apply()
    }

    /**
     * 重置所有设置
     */
    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
