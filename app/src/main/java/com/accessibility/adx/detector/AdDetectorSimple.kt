package com.accessibility.adx.detector

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.accessibility.adx.PreferencesManager

/**
 * "领时长"全自动循环广告点击器
 * 核心功能：自动检测"领时长"按钮 → 进入广告 → 完成领取 → 返回继续检测
 * 作者：古封
 */
class AdDetectorSimple(private val prefs: PreferencesManager) {

    companion object {
        private const val TAG = "AdDetectorSimple"
        
        // ========== 检测区域配置 ==========
        private const val DETECTION_RIGHT_RATIO = 0.85f
        private const val DETECTION_TOP_RATIO = 0.35f
        
        // ========== 时间配置（毫秒）==========
        private const val DETECTION_INTERVAL = 500L      // 检测间隔：500ms快速响应
        private const val CLICK_COOLDOWN = 180000L      // 同一按钮冷却：3分钟
        private const val AD_TIMEOUT = 120000L          // 单个广告流程超时：120秒
        private const val FAIL_PAUSE_TIME = 600000L     // 连续失败暂停：10分钟
        private const val MAX_LOOP_COUNT = 20            // 最大循环次数
        private const val MAX_FAIL_COUNT = 5             // 连续失败上限
        
        // ========== "领时长"关键词 ==========
        private val CLAIM_TIME_KEYWORDS = listOf(
            "领时长",
            "看广告领时长",
            "时长奖励",
            "领取时长",
            "得时长",
            "时长翻倍",
            "看视频领时长"
        )
        
        // ========== 领取成功关键词 ==========
        private val REWARD_SUCCESS_KEYWORDS = listOf(
            "领取成功",
            "已领取",
            "恭喜获得",
            "奖励已到账",
            "奖励领取成功",
            "时长已到账",
            "领取完成"
        )
        
        // ========== 领取按钮关键词 ==========
        private val REWARD_READY_KEYWORDS = listOf(
            "可领取奖励",
            "点击领取",
            "立即领取",
            "领取奖励",
            "立即领取",
            "看视频领取"
        )
        
        // ========== 关闭按钮关键词 ==========
        private val CLOSE_BUTTON_KEYWORDS = listOf(
            "×", "X", "✕", "✖",
            "关闭", "close", "skip", "跳过", "skip_ad"
        )
        
        // ========== 倒计时关键词 ==========
        private val COUNTDOWN_KEYWORDS = listOf(
            "秒后可领取", "秒后可关闭", "秒后可跳过", "秒后关闭"
        )
        
        private val COUNTDOWN_NUMBER_KEYWORDS = listOf(
            "1秒", "2秒", "3秒", "4秒", "5秒", "6秒", "7秒", "8秒", "9秒"
        )
        
        // ========== 弹窗关键词 ==========
        private val POPUP_CONTINUE_KEYWORDS = listOf(
            "继续观看", "继续领取", "再领一次", "看广告", "继续看广告"
        )
        
        private val POPUP_EXIT_KEYWORDS = listOf(
            "坚持退出", "残忍离开", "确认退出"
        )
    }

    // ========== 状态枚举 ==========
    enum class AdState {
        IDLE,                    // 空闲：检测"领时长"
        DETECTING_CLAIM,         // 检测领时长按钮
        ENTERING_AD,             // 进入广告中
        AD_COUNTDOWN,            // 广告倒计时中
        AD_REWARD_READY,         // 广告可领取
        AD_CLICKING,             // 点击领取中
        AD_SUCCESS,              // 领取成功
        AD_CLOSING,              // 关闭广告中
        POPUP_HANDLING,          // 处理弹窗
        TASK_COMPLETE,           // 任务完成
        PAUSED                   // 暂停（失败过多）
    }

    // ========== 动作枚举 ==========
    enum class Action {
        NONE,
        CLICK_CLAIM_TIME,       // 点击"领时长"
        CLICK_REWARD,           // 点击领取
        CLICK_CLOSE,            // 点击关闭
        CLICK_POPUP,            // 点击弹窗
        WAIT,                   // 等待
        COMPLETE                // 完成
    }

    // ========== 检测结果 ==========
    data class DetectionResult(
        val state: AdState,
        val action: Action,
        val matchedText: String,
        val targetNode: AccessibilityNodeInfo? = null
    )

    // ========== 回调接口 ==========
    interface DetectionCallback {
        fun onStateChanged(state: AdState, action: Action, matchedText: String)
        fun onLog(log: String)
    }

    private var callback: DetectionCallback? = null
    
    // ========== 状态变量 ==========
    private var currentState: AdState = AdState.IDLE
    private var loopCount = 0
    private var failCount = 0
    private var lastClickTime = 0L
    private var lastClaimClickTime = 0L
    private var adStartTime = 0L
    private var lastMatchedText = ""
    private var lastDetectionTime = 0L
    private var lastClaimButtonText = ""  // 记录上次点击的领时长按钮文字
    
    // 当前APP是否在适配名单中
    private var isSupportedApp = true
    
    // 屏幕尺寸缓存
    private var cachedScreenWidth = 0
    private var cachedScreenHeight = 0

    fun setCallback(callback: DetectionCallback?) {
        this.callback = callback
    }

    fun reset() {
        val oldState = currentState
        currentState = AdState.IDLE
        loopCount = 0
        lastMatchedText = ""
        log("【状态重置】$oldState → IDLE")
    }

    /**
     * 暂停服务（失败过多时）
     */
    fun pause() {
        currentState = AdState.PAUSED
        log("【服务暂停】连续失败$failCount次，暂停${FAIL_PAUSE_TIME/1000/60}分钟")
        callback?.onStateChanged(AdState.PAUSED, Action.NONE, "暂停服务")
        
        // 延迟恢复
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (currentState == AdState.PAUSED) {
                failCount = 0
                currentState = AdState.IDLE
                log("【服务恢复】可以继续工作")
                callback?.onStateChanged(AdState.IDLE, Action.NONE, "服务恢复")
            }
        }, FAIL_PAUSE_TIME)
    }

    /**
     * 更新屏幕尺寸缓存
     */
    fun updateScreenSize(width: Int, height: Int) {
        cachedScreenWidth = width
        cachedScreenHeight = height
    }
    
    /**
     * 设置当前APP是否在适配名单中
     */
    fun setSupportedApp(isSupported: Boolean) {
        isSupportedApp = isSupported
        log("【适配状态】${if (isSupported) "已适配应用" else "非适配应用"}")
    }
    
    /**
     * 检查当前APP是否在适配名单中
     */
    fun isCurrentAppSupported(): Boolean = isSupportedApp

    /**
     * 主检测入口
     */
    fun detect(rootNode: AccessibilityNodeInfo?, screenWidth: Int, screenHeight: Int): DetectionResult? {
        if (rootNode == null) return null
        if (callback == null) return null

        // 更新屏幕尺寸缓存
        if (screenWidth > 0 && screenHeight > 0) {
            cachedScreenWidth = screenWidth
            cachedScreenHeight = screenHeight
        }

        // 时间控制
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < DETECTION_INTERVAL) {
            return null
        }
        lastDetectionTime = currentTime

        // 计算检测区域
        val rightStart = (cachedScreenWidth * DETECTION_RIGHT_RATIO).toInt()
        val topEnd = (cachedScreenHeight * DETECTION_TOP_RATIO).toInt()
        val fullScreen = Rect(0, 0, cachedScreenWidth, cachedScreenHeight)
        val cornerArea = Rect(rightStart, 0, cachedScreenWidth, topEnd)

        // 日志输出当前状态（每10次输出一次）
        if (loopCount % 10 == 0) {
            log("【状态:${currentState}】循环:$loopCount 失败:$failCount")
        }

        // ========== 超时检测 ==========
        if (adStartTime > 0 && (currentTime - adStartTime) > AD_TIMEOUT) {
            log("【超时】广告流程超过${AD_TIMEOUT/1000}秒，强制重置")
            failCount++
            loopCount = 0
            adStartTime = 0
            currentState = AdState.IDLE
            if (failCount >= MAX_FAIL_COUNT) {
                pause()
            }
            return null
        }

        // ========== 优先级1：弹窗处理 ==========
        if (currentState != AdState.PAUSED) {
            val popupResult = detectPopup(rootNode, fullScreen)
            if (popupResult != null) return popupResult
        }

        // ========== 优先级2：领取成功 + 关闭 ==========
        if (currentState != AdState.IDLE && currentState != AdState.DETECTING_CLAIM && currentState != AdState.PAUSED) {
            val successResult = detectAndCloseReward(rootNode, cornerArea)
            if (successResult != null) return successResult
        }

        // ========== 优先级3：领取按钮 ==========
        if (currentState == AdState.AD_COUNTDOWN || currentState == AdState.AD_REWARD_READY) {
            val rewardResult = detectRewardButton(rootNode, cornerArea)
            if (rewardResult != null) return rewardResult
        }

        // ========== 优先级4：倒计时 ==========
        if (currentState == AdState.ENTERING_AD || currentState == AdState.AD_COUNTDOWN) {
            val countdownResult = detectCountdown(rootNode, cornerArea)
            if (countdownResult != null) return countdownResult
        }

        // ========== 优先级5：检测"领时长"按钮（仅在空闲状态）==========
        if (currentState == AdState.IDLE) {
            val claimResult = detectClaimTimeButton(rootNode, fullScreen)
            if (claimResult != null) return claimResult
        }

        return null
    }

    /**
     * 执行点击
     */
    fun performClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < 1000) { // 1秒内防抖
            log("【点击防抖】跳过")
            return false
        }
        
        try {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            lastClickTime = currentTime
            log("【点击成功】${node.text ?: node.contentDescription}")
            return clicked
        } catch (e: Exception) {
            log("【点击失败】${e.message}")
            return false
        }
    }

    /**
     * 更新状态
     */
    fun updateState(newState: AdState) {
        val oldState = currentState
        currentState = newState
        
        when (newState) {
            AdState.DETECTING_CLAIM, AdState.ENTERING_AD -> {
                if (oldState == AdState.IDLE) {
                    adStartTime = System.currentTimeMillis()
                }
                loopCount++
            }
            AdState.AD_CLICKING -> {
                loopCount++
                if (loopCount >= MAX_LOOP_COUNT) {
                    log("【循环超限】强制结束任务")
                    updateState(AdState.TASK_COMPLETE)
                }
            }
            AdState.AD_SUCCESS, AdState.AD_CLOSING -> {
                failCount = 0 // 成功后重置失败计数
            }
            AdState.TASK_COMPLETE -> {
                log("【任务完成】关闭广告流程")
                loopCount = 0
                adStartTime = 0
            }
            else -> {}
        }
        
        log("【状态变更】$oldState → $newState")
    }

    // ========== "领时长"按钮检测 ==========
    private fun detectClaimTimeButton(node: AccessibilityNodeInfo, screen: Rect): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!screen.contains(bounds) && !isWithinBounds(bounds, screen)) {
            return scanChildren(node, screen) { child -> detectClaimTimeButton(child, screen) }
        }
        
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        val fullText = "$text $contentDesc"
        
        // 检测"领时长"关键词
        val matchedKeyword = CLAIM_TIME_KEYWORDS.find { 
            text.contains(it) || contentDesc.contains(it)
        }
        
        if (matchedKeyword != null) {
            // 防重复点击：同一按钮3分钟内不重复点击
            if (matchedKeyword == lastClaimButtonText && 
                System.currentTimeMillis() - lastClaimClickTime < CLICK_COOLDOWN) {
                log("【领时长按钮】$matchedKeyword 冷却中，跳过")
                return null
            }
            
            // 确认可点击
            if (className.contains("button") || className.contains("image") || 
                className.contains("textview") || node.isClickable) {
                log("【检测到领时长】$matchedKeyword")
                lastClaimButtonText = matchedKeyword
                lastClaimClickTime = System.currentTimeMillis()
                
                currentState = AdState.DETECTING_CLAIM
                callback?.onStateChanged(AdState.DETECTING_CLAIM, Action.CLICK_CLAIM_TIME, matchedKeyword)
                return DetectionResult(AdState.DETECTING_CLAIM, Action.CLICK_CLAIM_TIME, matchedKeyword, node)
            }
            
            // 找可点击父节点
            val parentNode = findClickableParent(node)
            if (parentNode != null) {
                log("【检测到领时长】$matchedKeyword (父节点)")
                lastClaimButtonText = matchedKeyword
                lastClaimClickTime = System.currentTimeMillis()
                
                currentState = AdState.DETECTING_CLAIM
                callback?.onStateChanged(AdState.DETECTING_CLAIM, Action.CLICK_CLAIM_TIME, matchedKeyword)
                return DetectionResult(AdState.DETECTING_CLAIM, Action.CLICK_CLAIM_TIME, matchedKeyword, parentNode)
            }
        }
        
        return scanChildren(node, screen) { child -> detectClaimTimeButton(child, screen) }
    }

    // ========== 领取成功 + 关闭 ==========
    private fun detectAndCloseReward(node: AccessibilityNodeInfo, area: Rect): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!area.contains(bounds)) {
            return scanChildren(node, area) { detectAndCloseReward(it, area) }
        }
        
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        val fullText = "$text $contentDesc"
        
        // 检测"领取成功"
        val hasSuccessKeyword = REWARD_SUCCESS_KEYWORDS.any { fullText.contains(it) }
        
        // 检测关闭按钮
        val hasCloseKeyword = CLOSE_BUTTON_KEYWORDS.any { 
            text.contains(it, ignoreCase = true) || contentDesc.contains(it, ignoreCase = true)
        }
        
        if (hasSuccessKeyword) {
            log("【领取成功】$fullText")
            
            // 优先找关闭按钮
            val closeNode = findCloseButton(node, area)
            if (closeNode != null) {
                log("【准备关闭】找到关闭按钮")
                currentState = AdState.AD_CLOSING
                callback?.onStateChanged(AdState.AD_CLOSING, Action.CLICK_CLOSE, fullText)
                return DetectionResult(AdState.AD_CLOSING, Action.CLICK_CLOSE, fullText, closeNode)
            }
            
            // 点击整个成功区域
            log("【准备关闭】点击成功区域")
            currentState = AdState.AD_CLOSING
            callback?.onStateChanged(AdState.AD_CLOSING, Action.CLICK_CLOSE, fullText)
            return DetectionResult(AdState.AD_CLOSING, Action.CLICK_CLOSE, fullText, node)
        }
        
        // 单独检测关闭按钮
        if (hasCloseKeyword && (className.contains("button") || className.contains("image"))) {
            log("【检测到关闭按钮】$fullText")
            currentState = AdState.AD_CLOSING
            callback?.onStateChanged(AdState.AD_CLOSING, Action.CLICK_CLOSE, fullText)
            return DetectionResult(AdState.AD_CLOSING, Action.CLICK_CLOSE, fullText, node)
        }
        
        return scanChildren(node, area) { detectAndCloseReward(it, area) }
    }

    // ========== 领取按钮检测 ==========
    private fun detectRewardButton(node: AccessibilityNodeInfo, area: Rect): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!area.contains(bounds)) {
            return scanChildren(node, area) { detectRewardButton(it, area) }
        }
        
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        val fullText = "$text $contentDesc"
        
        val matchedKeyword = REWARD_READY_KEYWORDS.find { 
            text.contains(it) || contentDesc.contains(it)
        }
        
        if (matchedKeyword != null) {
            if (className.contains("button") || className.contains("image") || 
                className.contains("textview") || node.isClickable) {
                log("【可领取】$matchedKeyword")
                currentState = AdState.AD_REWARD_READY
                callback?.onStateChanged(AdState.AD_REWARD_READY, Action.CLICK_REWARD, matchedKeyword)
                return DetectionResult(AdState.AD_REWARD_READY, Action.CLICK_REWARD, matchedKeyword, node)
            }
            
            val parentNode = findClickableParent(node)
            if (parentNode != null) {
                log("【可领取】$matchedKeyword (父节点)")
                currentState = AdState.AD_REWARD_READY
                callback?.onStateChanged(AdState.AD_REWARD_READY, Action.CLICK_REWARD, matchedKeyword)
                return DetectionResult(AdState.AD_REWARD_READY, Action.CLICK_REWARD, matchedKeyword, parentNode)
            }
        }
        
        return scanChildren(node, area) { detectRewardButton(it, area) }
    }

    // ========== 倒计时检测 ==========
    private fun detectCountdown(node: AccessibilityNodeInfo, area: Rect): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!area.contains(bounds)) {
            return scanChildren(node, area) { detectCountdown(it, area) }
        }
        
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val fullText = "$text $contentDesc"
        
        // 完整倒计时模式
        val fullMatch = COUNTDOWN_KEYWORDS.find { fullText.contains(it) }
        if (fullMatch != null) {
            currentState = AdState.AD_COUNTDOWN
            return DetectionResult(AdState.AD_COUNTDOWN, Action.WAIT, fullMatch, node)
        }
        
        // 数字+后模式
        if (fullText.contains(Regex("""[1-9]秒.*后"""))) {
            currentState = AdState.AD_COUNTDOWN
            return DetectionResult(AdState.AD_COUNTDOWN, Action.WAIT, "倒计时中", node)
        }
        
        return scanChildren(node, area) { detectCountdown(it, area) }
    }

    // ========== 弹窗检测 ==========
    private fun detectPopup(node: AccessibilityNodeInfo, screen: Rect): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!screen.contains(bounds) && !isWithinBounds(bounds, screen)) {
            return scanChildren(node, screen) { detectPopup(it, screen) }
        }
        
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        val fullText = "$text $contentDesc"
        
        // 优先检测"继续领取"类弹窗
        val hasContinueKeyword = POPUP_CONTINUE_KEYWORDS.any { fullText.contains(it) }
        
        if (hasContinueKeyword) {
            if (className.contains("button") || className.contains("image") || node.isClickable) {
                log("【弹窗-继续领取】$fullText")
                currentState = AdState.POPUP_HANDLING
                callback?.onStateChanged(AdState.POPUP_HANDLING, Action.CLICK_POPUP, fullText)
                return DetectionResult(AdState.POPUP_HANDLING, Action.CLICK_POPUP, fullText, node)
            }
            
            val parentNode = findClickableParent(node)
            if (parentNode != null) {
                log("【弹窗-继续领取】$fullText (父节点)")
                currentState = AdState.POPUP_HANDLING
                callback?.onStateChanged(AdState.POPUP_HANDLING, Action.CLICK_POPUP, fullText)
                return DetectionResult(AdState.POPUP_HANDLING, Action.CLICK_POPUP, fullText, parentNode)
            }
        }
        
        return scanChildren(node, screen) { detectPopup(it, screen) }
    }

    // ========== 辅助方法 ==========
    
    private fun isWithinBounds(bounds: Rect, container: Rect): Boolean {
        return bounds.left < container.right && bounds.right > container.left &&
               bounds.top < container.bottom && bounds.bottom > container.top
    }
    
    private inline fun scanChildren(
        node: AccessibilityNodeInfo, 
        area: Rect, 
        scanner: (AccessibilityNodeInfo) -> DetectionResult?
    ): DetectionResult? {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = scanner(child)
                if (result != null) return result
            } finally {
                child.recycle()
            }
        }
        return null
    }
    
    private fun findCloseButton(node: AccessibilityNodeInfo, area: Rect): AccessibilityNodeInfo? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (area.contains(bounds)) {
            val text = node.text?.toString()?.trim() ?: ""
            val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
            val className = node.className?.toString()?.lowercase() ?: ""
            
            if (CLOSE_BUTTON_KEYWORDS.any { text.contains(it, ignoreCase = true) || contentDesc.contains(it, ignoreCase = true) }) {
                if (className.contains("button") || className.contains("image") || node.isClickable) {
                    return node
                }
            }
        }
        
        return scanChildren(node, area) { findCloseButton(it, area) }
    }
    
    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node.parent
        while (current != null) {
            if (current.isClickable) {
                return current
            }
            val parent = current.parent
            current.recycle()
            current = parent
        }
        return null
    }
    
    private fun log(message: String) {
        Log.d(TAG, message)
        callback?.onLog(message)
    }

    // ========== 状态查询 ==========
    fun getCurrentState(): AdState = currentState
    fun getLoopCount(): Int = loopCount
    fun getFailCount(): Int = failCount
    fun isPaused(): Boolean = currentState == AdState.PAUSED
}
