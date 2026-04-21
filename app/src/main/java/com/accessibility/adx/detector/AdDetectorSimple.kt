package com.accessibility.adx.detector

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.accessibility.adx.PreferencesManager

/**
 * 全自动广告点击器
 * 从检测+提醒升级为自动执行完整广告领取流程
 */
class AdDetectorSimple(private val prefs: PreferencesManager) {

    companion object {
        private const val TAG = "AdDetectorSimple"
        
        // 检测区域：屏幕右上角
        private const val DETECTION_RIGHT_RATIO = 0.85f  // 从右侧85%开始
        private const val DETECTION_TOP_RATIO = 0.25f   // 到顶部25%结束
        
        // 检测间隔：500ms，更快响应
        private const val DETECTION_INTERVAL = 500L
        
        // 循环次数限制
        private const val MAX_LOOP_COUNT = 10
        
        // 点击间隔（防止重复点击）
        private const val CLICK_COOLDOWN = 3000L
        
        // ========== 状态2关键词（倒计时结束，可直接点击）==========
        private val REWARD_READY_KEYWORDS = listOf(
            "可领取奖励",
            "点击领取",
            "立即领取",
            "领取奖励"
        )
        
        // ========== 关闭按钮关键词 ==========
        private val CLOSE_BUTTON_KEYWORDS = listOf(
            "×", "X", "✕", "✖",
            "关闭", "close", "close_btn", "iv_close", "btn_close"
        )
        
        // ========== 状态1关键词（倒计时进行中）==========
        private val COUNTDOWN_KEYWORDS = listOf(
            "秒后可领取奖励",
            "秒后可关闭",
            "秒后可跳过",
            "秒后关闭",
            "秒后可领"
        )
        
        // 倒计时数字关键词
        private val COUNTDOWN_NUMBER_KEYWORDS = listOf(
            "1秒", "2秒", "3秒", "4秒", "5秒", "6秒", "7秒", "8秒", "9秒"
        )
        
        // ========== 领取成功关键词 ==========
        private val REWARD_SUCCESS_KEYWORDS = listOf(
            "领取成功",
            "已领取",
            "恭喜获得",
            "奖励已到账",
            "奖励领取成功"
        )
        
        // ========== 弹窗关键词（最高优先级）==========
        private val POPUP_CONTINUE_KEYWORDS = listOf(
            "继续观看",
            "继续领取",
            "再领一次",
            "看广告",
            "继续看广告",
            "点击领取",
            "立即领取",
            "坚持退出",
            "取消",
            "退出"
        )
        
        private val POPUP_REWARD_KEYWORDS = listOf(
            "领取奖励",
            "继续领取",
            "再领一次",
            "看广告领奖励"
        )
    }

    // 当前状态
    enum class AdState {
        IDLE,                    // 空闲状态，监听中
        COUNTDOWN,               // 倒计时进行中
        REWARD_READY,            // 可领取奖励
        CLICKING_REWARD,         // 正在点击领取
        REWARD_SUCCESS,          // 领取成功
        CLOSING_AD,              // 正在关闭广告
        POPUP_DETECTED,          // 检测到弹窗
        TASK_COMPLETE            // 任务完成
    }

    // 检测结果数据类
    data class DetectionResult(
        val state: AdState,
        val action: Action,
        val matchedText: String,
        val targetNode: AccessibilityNodeInfo? = null
    )
    
    // 操作动作
    enum class Action {
        NONE,           // 无需操作
        CLICK_REWARD,   // 点击领取奖励按钮
        CLICK_CLOSE,    // 点击关闭按钮
        CLICK_POPUP,    // 点击弹窗选项
        WAIT,           // 等待
        COMPLETE        // 任务完成
    }

    interface DetectionCallback {
        /**
         * 广告状态更新回调
         * @param state 当前状态
         * @param action 需要执行的动作
         * @param matchedText 匹配到的文字
         */
        fun onStateChanged(state: AdState, action: Action, matchedText: String)
    }

    private var callback: DetectionCallback? = null
    
    // 状态管理
    private var currentState: AdState = AdState.IDLE
    private var loopCount = 0
    private var lastClickTime = 0L
    private var lastMatchedText = ""
    private var lastDetectionTime = 0L
    
    // 防止重复点击的标志
    private var hasClickedReward = false
    private var hasClosedAd = false

    fun setCallback(callback: DetectionCallback?) {
        this.callback = callback
    }

    fun reset() {
        currentState = AdState.IDLE
        loopCount = 0
        hasClickedReward = false
        hasClosedAd = false
        lastMatchedText = ""
        lastClickTime = 0L
        lastDetectionTime = 0L
        Log.d(TAG, "【状态重置】返回空闲监听状态")
    }

    /**
     * 执行广告自动点击检测
     * 返回需要执行的动作
     */
    fun detect(rootNode: AccessibilityNodeInfo?, screenWidth: Int, screenHeight: Int): DetectionResult? {
        if (rootNode == null) return null
        if (callback == null) return null

        // 时间控制
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < DETECTION_INTERVAL) {
            return null
        }
        lastDetectionTime = currentTime

        // 计算检测区域：右上角
        val rightStart = (screenWidth * DETECTION_RIGHT_RATIO).toInt()
        val topEnd = (screenHeight * DETECTION_TOP_RATIO).toInt()
        val fullScreen = Rect(0, 0, screenWidth, screenHeight)
        val detectionArea = Rect(rightStart, 0, screenWidth, topEnd)
        
        Log.d(TAG, "【状态】$currentState | 循环次数: $loopCount")

        // ========== 优先级1：弹窗检测（最高优先级）==========
        val popupResult = detectPopup(rootNode, fullScreen)
        if (popupResult != null) {
            currentState = AdState.POPUP_DETECTED
            return popupResult
        }

        // ========== 优先级2：领取成功 + 关闭 ==========
        val successResult = detectRewardSuccessAndClose(rootNode, detectionArea)
        if (successResult != null) {
            currentState = AdState.REWARD_SUCCESS
            return successResult
        }

        // ========== 优先级3：领取按钮点击 ==========
        if (currentState == AdState.IDLE || currentState == AdState.COUNTDOWN) {
            val rewardResult = detectRewardButton(rootNode, detectionArea)
            if (rewardResult != null) {
                currentState = AdState.REWARD_READY
                return rewardResult
            }
        }

        // ========== 优先级4：倒计时检测（最低优先级）==========
        val countdownResult = detectCountdown(rootNode, detectionArea)
        if (countdownResult != null) {
            currentState = AdState.COUNTDOWN
            return countdownResult
        }

        // 无检测结果，保持当前状态或返回IDLE
        if (currentState == AdState.COUNTDOWN) {
            // 倒计时期间没有检测到任何东西，可能是广告已关闭
            Log.d(TAG, "【倒计时期间无检测】广告可能已关闭")
        }
        
        return null
    }

    /**
     * 执行点击操作
     */
    fun performClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime < CLICK_COOLDOWN) {
            Log.d(TAG, "【点击冷却中】跳过重复点击")
            return false
        }
        
        try {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            lastClickTime = currentTime
            Log.d(TAG, "【点击成功】${node.text ?: node.contentDescription}")
            return clicked
        } catch (e: Exception) {
            Log.e(TAG, "【点击失败】${e.message}")
            return false
        }
    }

    /**
     * 更新状态
     */
    fun updateState(newState: AdState) {
        val oldState = currentState
        currentState = newState
        Log.d(TAG, "【状态变更】$oldState -> $newState")
        
        when (newState) {
            AdState.CLICKING_REWARD -> {
                hasClickedReward = true
                loopCount++
                if (loopCount >= MAX_LOOP_COUNT) {
                    Log.w(TAG, "【循环次数超限】$loopCount >= $MAX_LOOP_COUNT，强制结束任务")
                    callback?.onStateChanged(AdState.TASK_COMPLETE, Action.COMPLETE, "循环次数超限")
                }
            }
            AdState.CLOSING_AD -> {
                hasClosedAd = true
            }
            AdState.TASK_COMPLETE -> {
                reset()
            }
            else -> {}
        }
    }

    // ========== 优先级1：弹窗检测 ==========
    private fun detectPopup(node: AccessibilityNodeInfo, screen: Rect): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        // 跳过不在屏幕内的节点
        if (!screen.contains(bounds)) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    val result = detectPopup(child, screen)
                    if (result != null) return result
                } finally {
                    child.recycle()
                }
            }
            return null
        }
        
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val fullText = "$text $contentDesc"
        
        // 检测弹窗关键词
        val hasPopupKeyword = POPUP_CONTINUE_KEYWORDS.any { fullText.contains(it) }
        val hasRewardKeyword = POPUP_REWARD_KEYWORDS.any { fullText.contains(it) }
        
        if (hasPopupKeyword && hasRewardKeyword) {
            Log.d(TAG, "【弹窗检测-领取】找到弹窗奖励选项: $fullText")
            callback?.onStateChanged(AdState.POPUP_DETECTED, Action.CLICK_POPUP, fullText)
            return DetectionResult(AdState.POPUP_DETECTED, Action.CLICK_POPUP, fullText, node)
        }
        
        if (hasPopupKeyword) {
            // 检查是否是"取消"或"退出"按钮（优先级较低）
            if (text.contains("取消") || contentDesc.contains("取消") ||
                text.contains("退出") || contentDesc.contains("退出")) {
                Log.d(TAG, "【弹窗检测-退出】找到退出选项: $fullText")
                // 不自动点击退出，让广告继续
                return null
            }
            
            Log.d(TAG, "【弹窗检测-其他】: $fullText")
            callback?.onStateChanged(AdState.POPUP_DETECTED, Action.WAIT, fullText)
            return DetectionResult(AdState.POPUP_DETECTED, Action.WAIT, fullText, node)
        }
        
        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = detectPopup(child, screen)
                if (result != null) return result
            } finally {
                child.recycle()
            }
        }
        
        return null
    }

    // ========== 优先级2：领取成功 + 关闭 ==========
    private fun detectRewardSuccessAndClose(node: AccessibilityNodeInfo, area: Rect): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!area.contains(bounds)) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    val result = detectRewardSuccessAndClose(child, area)
                    if (result != null) return result
                } finally {
                    child.recycle()
                }
            }
            return null
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
            Log.d(TAG, "【领取成功】检测到: $fullText")
            
            // 优先找关闭按钮
            if (hasCloseKeyword && (className.contains("button") || className.contains("image"))) {
                Log.d(TAG, "【领取成功+关闭按钮】准备关闭广告")
                callback?.onStateChanged(AdState.REWARD_SUCCESS, Action.CLICK_CLOSE, fullText)
                return DetectionResult(AdState.REWARD_SUCCESS, Action.CLICK_CLOSE, fullText, node)
            }
            
            // 如果找到关闭按钮但不在当前节点，递归查找
            val closeNode = findCloseButton(node, area)
            if (closeNode != null) {
                Log.d(TAG, "【领取成功】找到关闭按钮，准备关闭")
                callback?.onStateChanged(AdState.REWARD_SUCCESS, Action.CLICK_CLOSE, fullText)
                return DetectionResult(AdState.REWARD_SUCCESS, Action.CLICK_CLOSE, fullText, closeNode)
            }
            
            // 如果找不到关闭按钮，点击整个领取成功区域
            Log.d(TAG, "【领取成功】无明确关闭按钮，点击领取成功区域")
            callback?.onStateChanged(AdState.REWARD_SUCCESS, Action.CLICK_CLOSE, fullText)
            return DetectionResult(AdState.REWARD_SUCCESS, Action.CLICK_CLOSE, fullText, node)
        }
        
        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = detectRewardSuccessAndClose(child, area)
                if (result != null) return result
            } finally {
                child.recycle()
            }
        }
        
        return null
    }

    // ========== 优先级3：领取按钮点击 ==========
    private fun detectRewardButton(node: AccessibilityNodeInfo, area: Rect): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!area.contains(bounds)) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    val result = detectRewardButton(child, area)
                    if (result != null) return result
                } finally {
                    child.recycle()
                }
            }
            return null
        }
        
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        val fullText = "$text $contentDesc"
        
        // 检查是否包含领取奖励关键词
        val matchedKeyword = REWARD_READY_KEYWORDS.find { 
            text.contains(it) || contentDesc.contains(it)
        }
        
        if (matchedKeyword != null) {
            // 防止重复点击
            if (hasClickedReward) {
                Log.d(TAG, "【领取按钮】已点击过，跳过: $matchedKeyword")
                return null
            }
            
            Log.d(TAG, "【领取按钮】检测到: $matchedKeyword")
            
            // 确认是按钮类型
            if (className.contains("button") || className.contains("image") || 
                className.contains("textview") || className.contains("relativelayout")) {
                callback?.onStateChanged(AdState.CLICKING_REWARD, Action.CLICK_REWARD, matchedKeyword)
                return DetectionResult(AdState.CLICKING_REWARD, Action.CLICK_REWARD, matchedKeyword, node)
            }
            
            // 如果不是按钮类型，找父节点或同级的按钮
            val parentNode = findClickableParent(node)
            if (parentNode != null) {
                callback?.onStateChanged(AdState.CLICKING_REWARD, Action.CLICK_REWARD, matchedKeyword)
                return DetectionResult(AdState.CLICKING_REWARD, Action.CLICK_REWARD, matchedKeyword, parentNode)
            }
        }
        
        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = detectRewardButton(child, area)
                if (result != null) return result
            } finally {
                child.recycle()
            }
        }
        
        return null
    }

    // ========== 优先级4：倒计时检测 ==========
    private fun detectCountdown(node: AccessibilityNodeInfo, area: Rect): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (!area.contains(bounds)) {
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    val result = detectCountdown(child, area)
                    if (result != null) return result
                } finally {
                    child.recycle()
                }
            }
            return null
        }
        
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val fullText = "$text $contentDesc"
        
        // 完整倒计时模式
        val fullMatch = COUNTDOWN_KEYWORDS.find { fullText.contains(it) }
        if (fullMatch != null) {
            Log.d(TAG, "【倒计时进行中】$fullMatch")
            callback?.onStateChanged(AdState.COUNTDOWN, Action.WAIT, fullMatch)
            return DetectionResult(AdState.COUNTDOWN, Action.WAIT, fullMatch, node)
        }
        
        // 数字+后模式
        if (fullText.contains(Regex("""[1-9]秒.*后"""))) {
            val match = Regex("""[1-9]秒.*""").find(fullText)?.value?.take(20) ?: "倒计时中"
            Log.d(TAG, "【倒计时进行中】$match")
            callback?.onStateChanged(AdState.COUNTDOWN, Action.WAIT, match)
            return DetectionResult(AdState.COUNTDOWN, Action.WAIT, match, node)
        }
        
        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = detectCountdown(child, area)
                if (result != null) return result
            } finally {
                child.recycle()
            }
        }
        
        return null
    }

    // ========== 辅助方法 ==========
    
    /**
     * 在指定区域内查找关闭按钮
     */
    private fun findCloseButton(node: AccessibilityNodeInfo, area: Rect): AccessibilityNodeInfo? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        
        if (area.contains(bounds)) {
            val text = node.text?.toString()?.trim() ?: ""
            val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
            val className = node.className?.toString()?.lowercase() ?: ""
            
            val hasCloseKeyword = CLOSE_BUTTON_KEYWORDS.any { 
                text.contains(it, ignoreCase = true) || contentDesc.contains(it, ignoreCase = true)
            }
            
            if (hasCloseKeyword && (className.contains("button") || className.contains("image"))) {
                return node
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = findCloseButton(child, area)
                if (result != null) return result
            } finally {
                child.recycle()
            }
        }
        
        return null
    }
    
    /**
     * 查找可点击的父节点
     */
    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current
            }
            current = current.parent
        }
        return null
    }

    /**
     * 获取当前状态
     */
    fun getCurrentState(): AdState = currentState
    
    /**
     * 是否正在执行任务
     */
    fun isTaskRunning(): Boolean = currentState != AdState.IDLE && currentState != AdState.TASK_COMPLETE
    
    /**
     * 获取循环次数
     */
    fun getLoopCount(): Int = loopCount
}
