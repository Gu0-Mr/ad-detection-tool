package com.accessibility.adx.detector

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.accessibility.adx.Constants
import com.accessibility.adx.PreferencesManager

/**
 * 通用广告点击检测器
 * 
 * 设计理念：
 * - 不依赖特定APP的布局特征
 * - 使用多策略组合检测
 * - 自适应调整检测参数
 * 
 * 检测策略：
 * 1. 关键词匹配（最可靠）
 * 2. 页面结构分析（按钮+文字组合）
 * 3. 坐标区域定位（右上角、底部等）
 * 
 * 作者：古封
 */
class AdDetectorSimple(private val prefs: PreferencesManager) {

    companion object {
        private const val TAG = "AdDetectorSimple"
        
        // ========== 时间配置（毫秒）==========
        private const val DETECTION_INTERVAL = 500L       // 检测间隔：500ms
        private const val CLICK_COOLDOWN = 180000L         // 按钮冷却：3分钟
        private const val AD_TIMEOUT = 120000L              // 广告超时：120秒
        private const val FAIL_PAUSE_TIME = 600000L         // 失败暂停：10分钟
        private const val MAX_LOOP_COUNT = 20               // 最大循环次数
        private const val MAX_FAIL_COUNT = 5                // 最大连续失败次数
        
        // ========== 引用Keywords ==========
        private val CLAIM_TIME_KEYWORDS = Constants.Keywords.CLAIM_TIME
        private val REWARD_READY_KEYWORDS = Constants.Keywords.REWARD_READY
        private val REWARD_SUCCESS_KEYWORDS = Constants.Keywords.REWARD_SUCCESS
        private val COUNTDOWN_KEYWORDS = Constants.Keywords.COUNTDOWN
        private val CLOSE_BUTTON_KEYWORDS = Constants.Keywords.CLOSE_BUTTON
        private val POPUP_CONTINUE_KEYWORDS = Constants.Keywords.POPUP_CONTINUE
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
        CLICK_REWARD,          // 点击领取
        CLICK_CLOSE,           // 点击关闭
        CLICK_POPUP,           // 点击弹窗
        WAIT,                  // 等待
        COMPLETE               // 完成
    }

    // ========== 检测结果 ==========
    data class DetectionResult(
        val state: AdState,
        val action: Action,
        val matchedText: String,
        val confidence: Float = 1.0f,  // 置信度
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
    private var lastDetectionTime = 0L
    private var lastClaimButtonText = ""
    
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
        adStartTime = 0L
        log("【状态重置】$oldState → IDLE")
    }

    fun pause() {
        currentState = AdState.PAUSED
        failCount++
        log("【服务暂停】连续失败$failCount次，暂停${FAIL_PAUSE_TIME/1000/60}分钟")
        callback?.onStateChanged(AdState.PAUSED, Action.NONE, "暂停服务")
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (currentState == AdState.PAUSED) {
                failCount = 0
                currentState = AdState.IDLE
                log("【服务恢复】继续工作")
                callback?.onStateChanged(AdState.IDLE, Action.NONE, "服务恢复")
            }
        }, FAIL_PAUSE_TIME)
    }

    fun updateScreenSize(width: Int, height: Int) {
        cachedScreenWidth = width
        cachedScreenHeight = height
    }

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
                    log("【循环超限】强制结束")
                    updateState(AdState.TASK_COMPLETE)
                }
            }
            AdState.AD_SUCCESS, AdState.AD_CLOSING -> {
                failCount = 0
            }
            AdState.TASK_COMPLETE -> {
                log("【任务完成】")
                loopCount = 0
                adStartTime = 0L
            }
            else -> {}
        }
        
        if (oldState != newState) {
            log("【状态变更】$oldState → $newState")
        }
    }

    // ========== 主检测入口 ==========
    fun detect(rootNode: AccessibilityNodeInfo?, screenWidth: Int, screenHeight: Int): DetectionResult? {
        if (rootNode == null) return null
        if (callback == null) return null

        if (screenWidth > 0 && screenHeight > 0) {
            cachedScreenWidth = screenWidth
            cachedScreenHeight = screenHeight
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < DETECTION_INTERVAL) {
            return null
        }
        lastDetectionTime = currentTime

        // 计算检测区域
        val rightStart = (cachedScreenWidth * 0.75f).toInt()
        val topEnd = (cachedScreenHeight * 0.40f).toInt()
        val fullScreen = Rect(0, 0, cachedScreenWidth, cachedScreenHeight)
        val topRightArea = Rect(rightStart, 0, cachedScreenWidth, topEnd)

        // 定期输出状态日志
        if (loopCount % 20 == 0) {
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

        // ========== 优先级1：弹窗检测（最高）==========
        if (currentState != AdState.PAUSED) {
            detectPopup(rootNode, fullScreen)?.let { return it }
        }

        // ========== 优先级2：领取成功 + 关闭 ==========
        if (currentState != AdState.IDLE && currentState != AdState.DETECTING_CLAIM && currentState != AdState.PAUSED) {
            detectRewardSuccessAndClose(rootNode, topRightArea)?.let { return it }
        }

        // ========== 优先级3：领取按钮 ==========
        if (currentState == AdState.AD_COUNTDOWN || currentState == AdState.AD_REWARD_READY) {
            detectRewardButton(rootNode, topRightArea)?.let { return it }
        }

        // ========== 优先级4：倒计时检测 ==========
        if (currentState == AdState.ENTERING_AD || currentState == AdState.AD_COUNTDOWN) {
            detectCountdown(rootNode, topRightArea)?.let { return it }
        }

        // ========== 优先级5：检测"领时长"按钮（仅空闲状态）==========
        if (currentState == AdState.IDLE) {
            detectClaimTimeButton(rootNode, fullScreen)?.let { return it }
        }

        return null
    }

    // ========== 策略1：检测"领时长"按钮 ==========
    /**
     * 通用"领时长"按钮检测
     * 适用于所有APP的领时长功能入口
     */
    private fun detectClaimTimeButton(node: AccessibilityNodeInfo, screen: Rect): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!isInScreen(bounds, screen)) {
            return scanChildren(node, screen, ::detectClaimTimeButton)
        }
        
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        val fullText = "$text $contentDesc"
        
        // 关键词匹配
        val matchedKeyword = CLAIM_TIME_KEYWORDS.find { 
            text.contains(it) || contentDesc.contains(it)
        }
        
        if (matchedKeyword != null) {
            // 防重复点击
            if (matchedKeyword == lastClaimButtonText && 
                System.currentTimeMillis() - lastClaimClickTime < CLICK_COOLDOWN) {
                return null
            }
            
            // 检查可点击
            if (isClickable(node)) {
                log("【领时长】$matchedKeyword")
                lastClaimButtonText = matchedKeyword
                lastClaimClickTime = System.currentTimeMillis()
                
                updateState(AdState.DETECTING_CLAIM)
                callback?.onStateChanged(AdState.DETECTING_CLAIM, Action.CLICK_CLAIM_TIME, matchedKeyword)
                return DetectionResult(AdState.DETECTING_CLAIM, Action.CLICK_CLAIM_TIME, matchedKeyword, node = node)
            }
            
            // 找可点击父节点
            findClickableParent(node)?.let { parent ->
                log("【领时长】$matchedKeyword (父节点)")
                lastClaimButtonText = matchedKeyword
                lastClaimClickTime = System.currentTimeMillis()
                
                updateState(AdState.DETECTING_CLAIM)
                callback?.onStateChanged(AdState.DETECTING_CLAIM, Action.CLICK_CLAIM_TIME, matchedKeyword)
                return DetectionResult(AdState.DETECTING_CLAIM, Action.CLICK_CLAIM_TIME, matchedKeyword, node = parent)
            }
        }
        
        return scanChildren(node, screen, ::detectClaimTimeButton)
    }

    // ========== 策略2：检测领取成功 + 关闭 ==========
    /**
     * 通用"领取成功"检测
     * 检测到后自动关闭广告
     */
    private fun detectRewardSuccessAndClose(node: AccessibilityNodeInfo, area: Rect): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!area.contains(bounds)) {
            return scanChildren(node, area, ::detectRewardSuccessAndClose)
        }
        
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        val fullText = "$text $contentDesc"
        
        // 检测"领取成功"
        val hasSuccess = REWARD_SUCCESS_KEYWORDS.any { fullText.contains(it) }
        
        // 检测关闭按钮
        val hasClose = CLOSE_BUTTON_KEYWORDS.any { 
            text.contains(it, ignoreCase = true) || contentDesc.contains(it, ignoreCase = true)
        }
        
        if (hasSuccess) {
            log("【领取成功】$fullText")
            
            // 优先找关闭按钮
            findCloseButton(node, area)?.let { closeNode ->
                log("【关闭】找到关闭按钮")
                updateState(AdState.AD_CLOSING)
                callback?.onStateChanged(AdState.AD_CLOSING, Action.CLICK_CLOSE, fullText)
                return DetectionResult(AdState.AD_CLOSING, Action.CLICK_CLOSE, fullText, node = closeNode)
            }
            
            // 点击成功区域
            log("【关闭】点击成功区域")
            updateState(AdState.AD_CLOSING)
            callback?.onStateChanged(AdState.AD_CLOSING, Action.CLICK_CLOSE, fullText)
            return DetectionResult(AdState.AD_CLOSING, Action.CLICK_CLOSE, fullText, node = node)
        }
        
        // 单独检测关闭按钮
        if (hasClose && isClickable(node)) {
            log("【关闭按钮】$fullText")
            updateState(AdState.AD_CLOSING)
            callback?.onStateChanged(AdState.AD_CLOSING, Action.CLICK_CLOSE, fullText)
            return DetectionResult(AdState.AD_CLOSING, Action.CLICK_CLOSE, fullText, node = node)
        }
        
        return scanChildren(node, area, ::detectRewardSuccessAndClose)
    }

    // ========== 策略3：检测领取按钮 ==========
    /**
     * 通用"领取"按钮检测
     * 广告倒计时结束后出现
     */
    private fun detectRewardButton(node: AccessibilityNodeInfo, area: Rect): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!area.contains(bounds)) {
            return scanChildren(node, area, ::detectRewardButton)
        }
        
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        val fullText = "$text $contentDesc"
        
        val matchedKeyword = REWARD_READY_KEYWORDS.find { 
            text.contains(it) || contentDesc.contains(it)
        }
        
        if (matchedKeyword != null) {
            if (isClickable(node)) {
                log("【可领取】$matchedKeyword")
                updateState(AdState.AD_REWARD_READY)
                callback?.onStateChanged(AdState.AD_REWARD_READY, Action.CLICK_REWARD, matchedKeyword)
                return DetectionResult(AdState.AD_REWARD_READY, Action.CLICK_REWARD, matchedKeyword, node = node)
            }
            
            findClickableParent(node)?.let { parent ->
                log("【可领取】$matchedKeyword (父节点)")
                updateState(AdState.AD_REWARD_READY)
                callback?.onStateChanged(AdState.AD_REWARD_READY, Action.CLICK_REWARD, matchedKeyword)
                return DetectionResult(AdState.AD_REWARD_READY, Action.CLICK_REWARD, matchedKeyword, node = parent)
            }
        }
        
        return scanChildren(node, area, ::detectRewardButton)
    }

    // ========== 策略4：检测倒计时 ==========
    /**
     * 通用倒计时检测
     * "X秒后可领取"等
     */
    private fun detectCountdown(node: AccessibilityNodeInfo, area: Rect): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!area.contains(bounds)) {
            return scanChildren(node, area, ::detectCountdown)
        }
        
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val fullText = "$text $contentDesc"
        
        // 完整倒计时模式
        COUNTDOWN_KEYWORDS.find { fullText.contains(it) }?.let {
            updateState(AdState.AD_COUNTDOWN)
            return DetectionResult(AdState.AD_COUNTDOWN, Action.WAIT, it, node = node)
        }
        
        // 数字+后模式
        if (fullText.contains(Regex("""[1-9]秒.*后"""))) {
            updateState(AdState.AD_COUNTDOWN)
            return DetectionResult(AdState.AD_COUNTDOWN, Action.WAIT, "倒计时中", node = node)
        }
        
        return scanChildren(node, area, ::detectCountdown)
    }

    // ========== 策略5：检测弹窗 ==========
    /**
     * 通用弹窗检测
     * "继续领取"等
     */
    private fun detectPopup(node: AccessibilityNodeInfo, screen: Rect): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!isInScreen(bounds, screen)) {
            return scanChildren(node, screen, ::detectPopup)
        }
        
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val fullText = "$text $contentDesc"
        
        val hasContinue = POPUP_CONTINUE_KEYWORDS.any { fullText.contains(it) }
        
        if (hasContinue) {
            if (isClickable(node)) {
                log("【弹窗】$fullText")
                updateState(AdState.POPUP_HANDLING)
                callback?.onStateChanged(AdState.POPUP_HANDLING, Action.CLICK_POPUP, fullText)
                return DetectionResult(AdState.POPUP_HANDLING, Action.CLICK_POPUP, fullText, node = node)
            }
            
            findClickableParent(node)?.let { parent ->
                log("【弹窗】$fullText (父节点)")
                updateState(AdState.POPUP_HANDLING)
                callback?.onStateChanged(AdState.POPUP_HANDLING, Action.CLICK_POPUP, fullText)
                return DetectionResult(AdState.POPUP_HANDLING, Action.CLICK_POPUP, fullText, node = parent)
            }
        }
        
        return scanChildren(node, screen, ::detectPopup)
    }

    // ========== 执行点击 ==========
    fun performClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < 1000) {
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

    // ========== 辅助方法 ==========
    
    private fun isInScreen(bounds: Rect, screen: Rect): Boolean {
        return bounds.left < screen.right && bounds.right > screen.left &&
               bounds.top < screen.bottom && bounds.bottom > screen.top
    }
    
    private fun isClickable(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString()?.lowercase() ?: ""
        return node.isClickable || 
               className.contains("button") || 
               className.contains("image") ||
               className.contains("relativelayout") ||
               className.contains("linearlayout")
    }
    
    private inline fun scanChildren(
        node: AccessibilityNodeInfo, 
        area: Rect, 
        scanner: (AccessibilityNodeInfo, Rect) -> DetectionResult?
    ): DetectionResult? {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                scanner(child, area)?.let { return it }
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
            
            if (CLOSE_BUTTON_KEYWORDS.any { text.contains(it, ignoreCase = true) || contentDesc.contains(it, ignoreCase = true) }) {
                if (isClickable(node)) {
                    return node
                }
            }
        }
        
        return scanChildren(node, area, ::findCloseButton)
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
