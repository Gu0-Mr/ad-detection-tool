package com.accessibility.adx

/**
 * 广播 Action 常量
 * 定义所有广播相关的 Action 和 Extra
 */
object Constants {
    // 广播 Action
    const val ACTION_AD_DETECTED = "com.accessibility.adx.ACTION_AD_DETECTED"
    const val ACTION_AD_COUNTDOWN = "com.accessibility.adx.ACTION_AD_COUNTDOWN"
    const val ACTION_DETECTION_STATUS_CHANGED = "com.accessibility.adx.ACTION_DETECTION_STATUS_CHANGED"
    const val ACTION_START = "com.accessibility.adx.ACTION_START_SERVICE"
    const val ACTION_STOP = "com.accessibility.adx.ACTION_STOP_SERVICE"

    // 广播 Extra
    const val EXTRA_DETECTION_COUNT = "extra_detection_count"
    const val EXTRA_TOTAL_COUNT = "extra_total_count"
    const val EXTRA_DETECTION_STATE = "extra_detection_state"
    const val EXTRA_MATCHED_TEXT = "extra_matched_text"
}
