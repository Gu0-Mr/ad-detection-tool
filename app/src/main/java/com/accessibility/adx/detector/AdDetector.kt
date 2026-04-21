package com.accessibility.adx.detector

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.accessibility.adx.PreferencesManager

/**
 * 广告检测器
 * 核心检测逻辑实现
 */
class AdDetector(private val prefs: PreferencesManager) {

    companion object {
        // 检测区域比例（屏幕百分比）
        private const val DETECTION_AREA_RIGHT_PERCENT = 0.15f  // 右侧15%
        private const val DETECTION_AREA_TOP_PERCENT = 0.20f     // 上方20%

        // 关闭按钮关键词
        private val CLOSE_KEYWORDS = listOf(
            "✕", "×", "X", "×",     // 符号
            "close", "关闭", "关",    // 关闭
            "skip", "跳过", "skip ad", // 跳过
            "dismiss", "不再显示",     // 忽略
            "x"                             // 小写x
        )

        // 关闭按钮ID关键词
        private val CLOSE_ID_KEYWORDS = listOf(
            "close", "dismiss", "skip", "cancel",
            "btn_close", "iv_close", "img_close",
            "close_btn", "close_icon", "close_image"
        )

        // 可疑的广告容器ID关键词
        private val AD_CONTAINER_ID_KEYWORDS = listOf(
            "ad_", "ads_", "advert", "banner",
            "ad_container", "ad_view", "ad_layout"
        )

        // 触发检测的时间间隔（毫秒）
        private const val DETECTION_INTERVAL = 500L
    }

    // 上次检测时间
    private var lastDetectionTime = 0L

    // 检测结果回调
    interface DetectionCallback {
        fun onAdDetected(nodeInfo: AccessibilityNodeInfo, detectionReason: String)
    }

    private var callback: DetectionCallback? = null

    /**
     * 设置检测回调
     */
    fun setCallback(callback: DetectionCallback?) {
        this.callback = callback
    }

    /**
     * 执行广告检测
     * @param rootNode 当前的根节点
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @return 是否检测到广告
     */
    fun detect(
        rootNode: AccessibilityNodeInfo?,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (rootNode == null) return false

        // 节流控制
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastDetectionTime < DETECTION_INTERVAL) {
            return false
        }
        lastDetectionTime = currentTime

        // 计算检测区域
        val detectionArea = calculateDetectionArea(screenWidth, screenHeight)

        // 在节点树中搜索关闭按钮
        return findCloseButton(rootNode, detectionArea)
    }

    /**
     * 计算检测区域
     * 屏幕右侧15% × 上方20%
     */
    private fun calculateDetectionArea(screenWidth: Int, screenHeight: Int): Rect {
        val rightStart = (screenWidth * (1 - DETECTION_AREA_RIGHT_PERCENT)).toInt()
        val topEnd = (screenHeight * DETECTION_AREA_TOP_PERCENT).toInt()

        return Rect(
            rightStart,    // left
            0,              // top
            screenWidth,    // right
            topEnd          // bottom
        )
    }

    /**
     * 在节点树中递归搜索关闭按钮
     */
    private fun findCloseButton(
        node: AccessibilityNodeInfo,
        detectionArea: Rect
    ): Boolean {
        // 检查当前节点是否符合关闭按钮的特征
        if (isCloseButton(node, detectionArea)) {
            callback?.onAdDetected(node, "关键词匹配")
            return true
        }

        // 递归检查子节点
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                if (findCloseButton(child, detectionArea)) {
                    return true
                }
            } finally {
                child.recycle()
            }
        }

        return false
    }

    /**
     * 检查节点是否为关闭按钮
     */
    private fun isCloseButton(node: AccessibilityNodeInfo, detectionArea: Rect): Boolean {
        // 获取节点边界
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        // 检查是否在检测区域内
        if (!detectionArea.intersect(bounds)) {
            return false
        }

        // 检查是否为可点击的控件
        if (!node.isClickable && !node.isLongClickable) {
            // ImageButton即使不可点击也可能触发
            if (node.className?.contains("ImageButton", ignoreCase = true) != true) {
                return false
            }
        }

        // 检查控件类型
        val className = node.className?.toString()?.lowercase() ?: ""
        val isValidType = className.contains("imagebutton") ||
                className.contains("button") ||
                className.contains("imageview") ||
                className.contains("textview")

        if (!isValidType) {
            return false
        }

        // 检查ID
        val nodeId = node.viewIdResourceName?.lowercase() ?: ""
        if (CLOSE_ID_KEYWORDS.any { nodeId.contains(it) }) {
            return true
        }

        // 检查文本内容
        val text = node.text?.toString()?.trim() ?: ""
        val contentDesc = node.contentDescription?.toString()?.trim() ?: ""

        if (text.isNotEmpty() && matchesKeyword(text)) {
            return true
        }

        if (contentDesc.isNotEmpty() && matchesKeyword(contentDesc)) {
            return true
        }

        // 检查子节点的文本
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                val childText = child.text?.toString()?.trim() ?: ""
                if (childText.isNotEmpty() && matchesKeyword(childText)) {
                    return true
                }
            } finally {
                child.recycle()
            }
        }

        return false
    }

    /**
     * 检查文本是否匹配关闭关键词
     */
    private fun matchesKeyword(text: String): Boolean {
        val lowerText = text.lowercase().trim()
        return CLOSE_KEYWORDS.any { keyword ->
            lowerText.contains(keyword.lowercase())
        }
    }

    /**
     * 检查节点是否在广告容器中
     */
    private fun isInAdContainer(node: AccessibilityNodeInfo): Boolean {
        var parent = node.parent
        while (parent != null) {
            val parentId = parent.viewIdResourceName?.lowercase() ?: ""
            if (AD_CONTAINER_ID_KEYWORDS.any { parentId.contains(it) }) {
                parent.recycle()
                return true
            }
            val temp = parent
            parent = parent.parent
            temp.recycle()
        }
        return false
    }

    /**
     * 执行点击操作
     */
    fun performClick(node: AccessibilityNodeInfo): Boolean {
        return try {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            } else {
                // 尝试在父节点上点击
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) {
                        val result = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        parent.recycle()
                        return result
                    }
                    val temp = parent
                    parent = parent.parent
                    temp.recycle()
                }
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 重置检测状态
     */
    fun reset() {
        lastDetectionTime = 0L
    }
}
