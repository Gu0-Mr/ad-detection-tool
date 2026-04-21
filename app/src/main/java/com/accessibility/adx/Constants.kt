package com.accessibility.adx

/**
 * 广播 Action 常量和适配名单配置
 * 作者：古封
 */
object Constants {
    // ========== 广播 Action ==========
    const val ACTION_AD_DETECTED = "com.accessibility.adx.ACTION_AD_DETECTED"
    const val ACTION_AD_COUNTDOWN = "com.accessibility.adx.ACTION_AD_COUNTDOWN"
    const val ACTION_AD_STATE_CHANGED = "com.accessibility.adx.ACTION_AD_STATE_CHANGED"
    const val ACTION_DETECTION_STATUS_CHANGED = "com.accessibility.adx.ACTION_DETECTION_STATUS_CHANGED"
    const val ACTION_START = "com.accessibility.adx.ACTION_START_SERVICE"
    const val ACTION_STOP = "com.accessibility.adx.ACTION_STOP_SERVICE"
    const val ACTION_KEEP_ALIVE = "com.accessibility.adx.ACTION_KEEP_ALIVE"

    // ========== 广播 Extra ==========
    const val EXTRA_DETECTION_COUNT = "extra_detection_count"
    const val EXTRA_TOTAL_COUNT = "extra_total_count"
    const val EXTRA_DETECTION_STATE = "extra_detection_state"
    const val EXTRA_MATCHED_TEXT = "extra_matched_text"
    const val EXTRA_LOOP_COUNT = "extra_loop_count"
    const val EXTRA_CURRENT_PACKAGE = "extra_current_package"
    const val EXTRA_IS_SUPPORTED = "extra_is_supported"

    // ========== 适配应用名单 ==========
    // 格式：PackageInfo(包名, 应用名称, Logo资源名)
    data class SupportedApp(
        val packageName: String,
        val appName: String,
        val description: String = ""
    )
    
    val SUPPORTED_APPS = listOf(
        SupportedApp(
            packageName = "com.netease.cloudmusic",
            appName = "汽水音乐",
            description = "看视频领时长"
        ),
        SupportedApp(
            packageName = "com.tencent.qqmusic",
            appName = "QQ音乐",
            description = "看广告领VIP时长"
        ),
        SupportedApp(
            packageName = "com.tencent.videolite",
            appName = "腾讯视频",
            description = "看广告领会员时长"
        ),
        SupportedApp(
            packageName = "com.dragon.read",
            appName = "番茄免费小说",
            description = "阅读领时长"
        ),
        SupportedApp(
            packageName = "com.qiyi.video",
            appName = "爱奇艺视频",
            description = "看视频领会员时长"
        ),
        SupportedApp(
            packageName = "com.youku.phone",
            appName = "优酷视频",
            description = "看视频领VIP时长"
        ),
        SupportedApp(
            packageName = "com.baidu.BaiduWenku",
            appName = "百度文库",
            description = "看广告领下载券"
        ),
        SupportedApp(
            packageName = "com.xunlei.downloadprovider",
            appName = "迅雷",
            description = "看视频领会员"
        )
    )
    
    /**
     * 检查包名是否在适配名单中
     */
    fun isAppSupported(packageName: String): Boolean {
        return SUPPORTED_APPS.any { it.packageName == packageName }
    }
    
    /**
     * 获取应用信息
     */
    fun getSupportedApp(packageName: String): SupportedApp? {
        return SUPPORTED_APPS.find { it.packageName == packageName }
    }
}
