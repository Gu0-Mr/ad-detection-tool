package com.accessibility.adx

/**
 * 广播 Action 常量和通用配置
 * 
 * 通用化设计理念：
 * - 不依赖特定APP的布局特征
 * - 使用通用关键词匹配所有APP的"领时长"功能
 * - 自适应检测区域和策略
 * 
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
    const val ACTION_APP_STATE_CHANGED = "com.accessibility.adx.ACTION_APP_STATE_CHANGED"

    // ========== 广播 Extra ==========
    const val EXTRA_DETECTION_COUNT = "extra_detection_count"
    const val EXTRA_TOTAL_COUNT = "extra_total_count"
    const val EXTRA_DETECTION_STATE = "extra_detection_state"
    const val EXTRA_MATCHED_TEXT = "extra_matched_text"
    const val EXTRA_LOOP_COUNT = "extra_loop_count"
    const val EXTRA_CURRENT_PACKAGE = "extra_current_package"
    const val EXTRA_IS_SUPPORTED = "extra_is_supported"

    // ========== 通用检测配置 ==========
    // 不再硬编码特定APP，而是使用通用关键词
    
    /**
     * 通用"领时长"关键词列表
     * 适用于所有APP的领时长功能
     */
    object Keywords {
        // 领时长按钮关键词（空闲状态检测）
        val CLAIM_TIME = listOf(
            "领时长",           // 通用
            "看广告领",         // 通用
            "看视频领",         // 通用
            "时长奖励",         // 通用
            "领取时长",         // 通用
            "得时长",           // 通用
            "时长翻倍",         // 通用
            "领会员",           // 会员时长
            "看视频领会员",     // 会员时长
            "广告奖励"          // 通用
        )
        
        // 领取按钮关键词（广告倒计时结束后）
        val REWARD_READY = listOf(
            "可领取奖励",
            "点击领取",
            "立即领取",
            "领取奖励",
            "看视频领取",
            "立即领取",
            "去领取"
        )
        
        // 领取成功关键词
        val REWARD_SUCCESS = listOf(
            "领取成功",
            "已领取",
            "恭喜获得",
            "奖励已到账",
            "奖励领取成功",
            "时长已到账",
            "领取完成",
            "获得奖励"
        )
        
        // 倒计时关键词
        val COUNTDOWN = listOf(
            "秒后可领取",
            "秒后可关闭",
            "秒后可跳过",
            "秒后关闭",
            "秒后可领",
            "秒后可看"
        )
        
        // 关闭按钮关键词
        val CLOSE_BUTTON = listOf(
            "×", "X", "✕", "✖",
            "关闭", "close", "skip", "跳过", "skip_ad",
            "iv_close", "btn_close", "close_btn"
        )
        
        // 弹窗继续关键词
        val POPUP_CONTINUE = listOf(
            "继续观看",
            "继续领取",
            "再领一次",
            "看广告",
            "继续看广告",
            "再领",
            "看视频"
        )
    }
    
    // ========== 检测区域配置 ==========
    // 全屏检测，适用于所有APP
    object DetectionArea {
        const val FULL_SCREEN = 0      // 全屏检测
        const val TOP_RIGHT = 1        // 右上角（广告页面）
        const val TOP_PORTION = 2      // 顶部区域
        const val BOTTOM_PORTION = 3   // 底部区域
        const val CENTER = 4           // 中央区域
        
        // 区域比例配置
        const val TOP_RIGHT_RIGHT_START = 0.75f  // 右上角：从右侧75%开始
        const val TOP_RIGHT_TOP_END = 0.40f       // 右上角：到顶部40%结束
        const val TOP_PORTION_HEIGHT = 0.50f       // 顶部区域：占上半部分
        const val BOTTOM_PORTION_HEIGHT = 0.50f   // 底部区域：占下半部分
    }
    
    // ========== 适配应用名单（仅供参考，不用于检测逻辑）==========
    // 这些APP都有"领时长"功能，但检测逻辑是通用的
    data class SupportedApp(
        val packageName: String,
        val appName: String,
        val description: String = "支持领时长自动任务", // 添加描述字段
        val hasTimeClaim: Boolean = true
    )
    
    val SUPPORTED_APPS = listOf(
        SupportedApp("com.netease.cloudmusic", "汽水音乐", "支持看视频领时长功能"),
        SupportedApp("com.tencent.qqmusic", "QQ音乐", "支持听歌领时长功能"),
        SupportedApp("com.tencent.videolite", "腾讯视频", "支持看视频领时长功能"),
        SupportedApp("com.dragon.read", "番茄免费小说", "支持看小说领时长功能"),
        SupportedApp("com.netease.cloudmusic", "网易云音乐", "支持听歌领时长功能"),  // 与汽水音乐相同包名
        SupportedApp("com.qiyi.video", "爱奇艺视频", "支持看视频领时长功能")
    )
    
    /**
     * 检查包名是否在已知适配列表中
     */
    fun isKnownApp(packageName: String): Boolean {
        return SUPPORTED_APPS.any { it.packageName == packageName }
    }
    
    /**
     * 检查应用是否支持领时长功能
     */
    fun isAppSupported(packageName: String): Boolean {
        return SUPPORTED_APPS.any { it.packageName == packageName }
    }
    
    /**
     * 根据包名获取支持的应用信息
     */
    fun getSupportedApp(packageName: String): SupportedApp? {
        return SUPPORTED_APPS.find { it.packageName == packageName }
    }
}
