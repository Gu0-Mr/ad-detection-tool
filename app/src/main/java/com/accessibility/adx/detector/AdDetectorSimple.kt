package com.accessibility.adx.detector

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.accessibility.adx.PreferencesManager

/**
 * 简化的广告检测器
 * 专注于右上角"可领取奖励"类倒计时结束提示的检测
 */
class AdDetectorSimple(private val prefs: PreferencesManager) {

    companion object {
        private const val TAG = "AdDetectorSimple"
        
        // 检测区域：屏幕右上角
        private const val DETECTION_RIGHT_RATIO = 0.85f  // 从右侧85%开始
        private const val DETECTION_TOP_RATIO = 0.25f   // 到顶部25%结束
        
        // 检测间隔：1秒，避免频繁触发
        private const val DETECTION_INTERVAL = 1000L
        
        // 状态2关键词（倒计时结束，可直接点击）- 主文字
        private val REWARD_READY_KEYWORDS = listOf(
            "可领取奖励",
            "点击领取",
            "立即领取",
            "领取奖励"
        )
        
        // 状态2关闭按钮关键词
        private val CLOSE_BUTTON_KEYWORDS = listOf(
            "×", "X", "✕", "✖",
            "关闭", "close", "close_btn", "iv_close", "btn_close"
        )
        
        // 状态1关键词（倒计时进行中）
        private val COUNTDOWN_KEYWORDS = listOf(
            "秒后可领取奖励",
            "秒后可关闭",
            "秒后可跳过",
            "秒后关闭",
            "秒后可领"
        )
        
        // 用于匹配"X秒"的正则（1-9之间的数字）
        private val COUNTDOWN_NUMERIC_PATTERN = Regex("""[1-9]秒""")
        
        // 用于匹配"X秒后可..."的正则
        private val COUNTDOWN_FULL_PATTERN = Regex("""[1-9]秒后(?:可|以)""")
    }
    
    // 倒计时数字关键词（单独匹配"X秒"时使用）
    private val COUNTDOWN_NUMBER_KEYWORDS = listOf(
        "1秒", "2秒", "3秒", "4秒", "5秒", "6秒", "7秒", "8秒", "9秒"
    )

    private var lastDetectionTime = 0L

    /**
     * 检测状态枚举
     */
    enum class DetectionState {
        STATE_READY,      // 状态2：倒计时结束，可直接点击
        STATE_COUNTDOWN,  // 状态1：倒计时进行中
        NONE              // 未检测到
    }

    /**
     * 检测结果数据类
     */
    data class DetectionResult(
        val state: DetectionState,
        val matchedText: String,
        val nodeInfo: AccessibilityNodeInfo?
    )

    interface DetectionCallback {
        /**
         * 广告检测回调
         * @param state 检测到的状态
         * @param matchedText 匹配到的文字
         */
        fun onAdDetected(state: DetectionState, matchedText: String)
    }

    private var callback: DetectionCallback? = null

    fun setCallback(callback: DetectionCallback?) {
        this.callback = callback
    }

    fun reset() {
        lastDetectionTime = 0L
    }

    /**
     * 执行广告检测
     * 优先检测状态2（倒计时结束），再检测状态1（倒计时进行中）
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
        val detectionArea = Rect(rightStart, 0, screenWidth, topEnd)
        
        Log.d(TAG, "检测区域: rightStart=$rightStart, topEnd=$topEnd, screenWidth=$screenWidth, screenHeight=$screenHeight")

        // 优先检测状态2（倒计时结束，可直接点击）
        val state2Result = detectState2Ready(rootNode, detectionArea)
        if (state2Result != null) {
            Log.d(TAG, "【检测成功-状态2】检测到倒计时结束，可领取奖励！matchedText=${state2Result.matchedText}")
            callback?.onAdDetected(state2Result.state, state2Result.matchedText)
            return state2Result
        }

        // 如果没有状态2，检测状态1（倒计时进行中）
        val state1Result = detectState1Countdown(rootNode, detectionArea)
        if (state1Result != null) {
            Log.d(TAG, "【检测成功-状态1】检测到广告倒计时中，matchedText=${state1Result.matchedText}")
            callback?.onAdDetected(state1Result.state, state1Result.matchedText)
            return state1Result
        }

        Log.d(TAG, "【检测结果】未检测到广告相关元素")
        return null
    }

    /**
     * 状态2检测：倒计时结束，可直接点击
     * 条件：主文字包含"可领取奖励"/"点击领取"，右侧有X关闭按钮
     */
    private fun detectState2Ready(
        node: AccessibilityNodeInfo,
        detectionArea: Rect
    ): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // 获取节点信息
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        val className = node.className?.toString()?.lowercase() ?: ""
        
        val fullText = "$text $contentDesc"

        // 检查是否包含状态2主文字关键词
        val matchedKeyword = REWARD_READY_KEYWORDS.find { keyword ->
            text.contains(keyword) || contentDesc.contains(keyword)
        }
        
        if (matchedKeyword != null) {
            Log.d(TAG, "状态2匹配：找到关键词'$matchedKeyword', text='$text', contentDesc='$contentDesc'")
            
            // 检查是否是按钮类型（如果是纯文字区域，可能需要找子节点中的关闭按钮）
            if (className.contains("button") || className.contains("image") || className.contains("textview")) {
                callback?.onAdDetected(DetectionState.STATE_READY, matchedKeyword)
                return DetectionResult(DetectionState.STATE_READY, matchedKeyword, node)
            }
            
            // 如果不是按钮类型，尝试递归查找关闭按钮
            return findCloseButtonWithRewardContext(node, detectionArea, matchedKeyword)
        }

        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = detectState2Ready(child, detectionArea)
                if (result != null) {
                    return result
                }
            } finally {
                child.recycle()
            }
        }

        return null
    }

    /**
     * 在状态2区域查找关闭按钮（确保有X按钮可点击）
     */
    private fun findCloseButtonWithRewardContext(
        parentNode: AccessibilityNodeInfo,
        detectionArea: Rect,
        rewardText: String
    ): DetectionResult? {
        // 在父节点的所有子节点中查找关闭按钮
        for (i in 0 until parentNode.childCount) {
            val child = parentNode.getChild(i) ?: continue
            try {
                val bounds = Rect()
                child.getBoundsInScreen(bounds)
                
                if (!detectionArea.contains(bounds)) continue
                
                val text = child.text?.toString()?.trim() ?: ""
                val contentDesc = child.contentDescription?.toString()?.trim() ?: ""
                val className = child.className?.toString()?.lowercase() ?: ""
                
                // 检查是否是关闭按钮
                val isCloseButton = CLOSE_BUTTON_KEYWORDS.any { keyword ->
                    text.contains(keyword, ignoreCase = true) || 
                    contentDesc.contains(keyword, ignoreCase = true)
                }
                
                if (isCloseButton && (className.contains("button") || className.contains("image"))) {
                    Log.d(TAG, "状态2确认：找到关闭按钮'$text|$contentDesc'，配合奖励文字'$rewardText'")
                    callback?.onAdDetected(DetectionState.STATE_READY, "$rewardText + 关闭按钮")
                    return DetectionResult(DetectionState.STATE_READY, "$rewardText + 关闭按钮", child)
                }
            } finally {
                child.recycle()
            }
        }
        
        // 如果没找到关闭按钮，但有奖励文字，仍然触发（某些APP可能只有一个可点击区域）
        Log.d(TAG, "状态2确认：找到奖励文字'$rewardText'（无明确关闭按钮）")
        callback?.onAdDetected(DetectionState.STATE_READY, rewardText)
        return DetectionResult(DetectionState.STATE_READY, rewardText, parentNode)
    }

    /**
     * 状态1检测：倒计时进行中
     * 检测包含"X秒后可..."的文字
     */
    private fun detectState1Countdown(
        node: AccessibilityNodeInfo,
        detectionArea: Rect
    ): DetectionResult? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // 获取节点信息
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""
        
        val fullText = "$text $contentDesc"

        // 优先检查完整的"X秒后可..."模式
        val fullMatch = COUNTDOWN_KEYWORDS.find { keyword ->
            fullText.contains(keyword)
        }
        
        if (fullMatch != null) {
            Log.d(TAG, "状态1匹配：找到完整倒计时关键词'$fullMatch', text='$text'")
            callback?.onAdDetected(DetectionState.STATE_COUNTDOWN, fullMatch)
            return DetectionResult(DetectionState.STATE_COUNTDOWN, fullMatch, node)
        }
        
        // 检查"X秒"数字模式（需要同时包含"后"字表示倒计时语义）
        if (fullText.contains(Regex("""[1-9]秒.*后"""))) {
            val match = fullText.match(Regex("""[1-9]秒.*""")
            )?.value?.take(20) ?: "倒计时中"
            Log.d(TAG, "状态1匹配：找到倒计时数字模式'$match', text='$text'")
            callback?.onAdDetected(DetectionState.STATE_COUNTDOWN, match)
            return DetectionResult(DetectionState.STATE_COUNTDOWN, match, node)
        }
        
        // 检查单独的"X秒"（配合"可领取"/"可关闭"等）
        val hasCountdown = COUNTDOWN_NUMBER_KEYWORDS.any { keyword -> fullText.contains(keyword) }
        val hasRewardContext = fullText.contains("领取") || fullText.contains("关闭") || fullText.contains("跳过")
        if (hasCountdown && hasRewardContext) {
            val countdownNum = COUNTDOWN_NUMBER_KEYWORDS.find { fullText.contains(it) } ?: "X秒"
            Log.d(TAG, "状态1匹配：倒计时数字'$countdownNum'配合奖励语义, text='$text'")
            callback?.onAdDetected(DetectionState.STATE_COUNTDOWN, "$countdownNum + 奖励提示")
            return DetectionResult(DetectionState.STATE_COUNTDOWN, "$countdownNum + 奖励提示", node)
        }

        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val result = detectState1Countdown(child, detectionArea)
                if (result != null) {
                    return result
                }
            } finally {
                child.recycle()
            }
        }

        return null
    }

    /**
     * 兼容旧API的简单检测方法
     */
    fun detectSimple(rootNode: AccessibilityNodeInfo?, screenWidth: Int, screenHeight: Int): Boolean {
        return detect(rootNode, screenWidth, screenHeight) != null
    }
}
