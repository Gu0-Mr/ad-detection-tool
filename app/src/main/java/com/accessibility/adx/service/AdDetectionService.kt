package com.accessibility.adx.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.accessibility.adx.Constants
import com.accessibility.adx.Constants.ACTION_AD_DETECTED
import com.accessibility.adx.Constants.ACTION_AD_COUNTDOWN
import com.accessibility.adx.Constants.ACTION_DETECTION_STATUS_CHANGED
import com.accessibility.adx.Constants.EXTRA_DETECTION_COUNT
import com.accessibility.adx.Constants.EXTRA_TOTAL_COUNT
import com.accessibility.adx.Constants.EXTRA_DETECTION_STATE
import com.accessibility.adx.Constants.EXTRA_MATCHED_TEXT
import com.accessibility.adx.detector.AdDetectorSimple
import com.accessibility.adx.util.SoundManager
import com.accessibility.adx.util.VibrationManager

/**
 * 广告检测无障碍服务
 * 监听屏幕内容变化，检测广告倒计时结束后的"可领取奖励"按钮
 */
class AdDetectionService : AccessibilityService() {

    companion object {
        private const val TAG = "AdDetectionService"
        
        // 服务状态
        @Volatile
        var isRunning = false
            private set
        
        // 事件类型过滤器
        private val EVENT_TYPES = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
    }

    // 偏好设置
    private val prefs by lazy { com.accessibility.adx.PreferencesManager.getInstance(this) }
    
    // 广告检测器（简化版）
    private lateinit var adDetector: AdDetectorSimple
    
    // 震动管理器
    private lateinit var vibrationManager: VibrationManager
    
    // 声音管理器
    private lateinit var soundManager: SoundManager
    
    // 屏幕尺寸
    private var screenWidth = 0
    private var screenHeight = 0
    
    // 本地检测计数
    private var detectionCount = 0
    
    // 上次检测到的文字（用于去重）
    private var lastMatchedText = ""

    // 广播意图
    private val detectionIntent by lazy {
        Intent(ACTION_AD_DETECTED).apply {
            setPackage(packageName)
        }
    }

    // 倒计时广播意图
    private val countdownIntent by lazy {
        Intent(ACTION_AD_COUNTDOWN).apply {
            setPackage(packageName)
        }
    }

    // 上次检测的应用包名
    private var lastPackageName = ""

    override fun onCreate() {
        super.onCreate()
        initializeComponents()
        updateScreenSize()
    }

    /**
     * 初始化组件
     */
    private fun initializeComponents() {
        // 初始化简化版检测器
        adDetector = AdDetectorSimple(prefs)
        adDetector.setCallback(object : AdDetectorSimple.DetectionCallback {
            override fun onAdDetected(state: AdDetectorSimple.DetectionState, matchedText: String) {
                handleAdDetection(state, matchedText)
            }
        })

        // 初始化震动管理器
        vibrationManager = VibrationManager(this)
        
        // 初始化声音管理器
        soundManager = SoundManager(this)
    }

    /**
     * 更新屏幕尺寸
     */
    private fun updateScreenSize() {
        try {
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
        } catch (e: Exception) {
            // 使用默认尺寸
            screenWidth = 1080
            screenHeight = 1920
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        
        // 配置服务信息
        val info = serviceInfo
        info.notificationTimeout = 100
        info.eventTypes = EVENT_TYPES
        serviceInfo = info
        
        prefs.isAccessibilityEnabled = true
        
        // 发送状态变化广播
        sendBroadcast(Intent(Constants.ACTION_DETECTION_STATUS_CHANGED))
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // 只检测应用窗口变化
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            if (packageName != lastPackageName) {
                lastPackageName = packageName
                // 切换应用时重置检测器
                adDetector.reset()
                lastMatchedText = ""
            }
        }

        // 执行检测
        val rootNode = event.source ?: return
        try {
            val result = adDetector.detect(rootNode, screenWidth, screenHeight)
            if (result != null) {
                // 根据检测状态处理
                when (result.state) {
                    AdDetectorSimple.DetectionState.STATE_READY -> {
                        // 倒计时结束，可领取奖励
                        // 去重：只有文字变化时才触发
                        if (result.matchedText != lastMatchedText) {
                            lastMatchedText = result.matchedText
                            detectionCount++
                            prefs.incrementCount()
                            broadcastDetection(result.state, result.matchedText)
                            Log.d(TAG, "【倒计时结束】可领取奖励: ${result.matchedText}")
                        }
                    }
                    AdDetectorSimple.DetectionState.STATE_COUNTDOWN -> {
                        // 倒计时进行中（不计数，只记录日志）
                        if (result.matchedText != lastMatchedText) {
                            lastMatchedText = result.matchedText
                            broadcastCountdown(result.matchedText)
                            Log.d(TAG, "【倒计时进行中】: ${result.matchedText}")
                        }
                    }
                    else -> {}
                }
            }
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 处理广告检测结果（倒计时结束状态）
     */
    private fun handleAdDetection(state: AdDetectorSimple.DetectionState, matchedText: String) {
        if (state == AdDetectorSimple.DetectionState.STATE_READY) {
            // 震动提醒（强烈）
            vibrationManager.vibrate()
            vibrationManager.vibrate() // 再次震动加强提醒
            
            // 声音提醒
            soundManager.playSound()
            
            Log.d(TAG, "检测到可领取奖励: $matchedText")
        }
    }

    override fun onInterrupt() {
        // 服务中断时的处理
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        prefs.isAccessibilityEnabled = false
        adDetector.setCallback(null)
        soundManager.release()
        
        // 发送状态变化广播
        sendBroadcast(Intent(Constants.ACTION_DETECTION_STATUS_CHANGED))
    }

    /**
     * 广播检测事件（倒计时结束）
     */
    private fun broadcastDetection(state: AdDetectorSimple.DetectionState, matchedText: String) {
        try {
            detectionIntent.putExtra(EXTRA_DETECTION_COUNT, detectionCount)
            detectionIntent.putExtra(EXTRA_TOTAL_COUNT, prefs.totalCount)
            detectionIntent.putExtra(EXTRA_DETECTION_STATE, state.name)
            detectionIntent.putExtra(EXTRA_MATCHED_TEXT, matchedText)
            sendBroadcast(detectionIntent)
            
            // 震动和声音提醒
            vibrationManager.vibrate()
            vibrationManager.vibrate()
            soundManager.playSound()
        } catch (e: Exception) {
            Log.e(TAG, "广播发送失败: ${e.message}")
        }
    }

    /**
     * 广播倒计时状态（倒计时进行中）
     */
    private fun broadcastCountdown(matchedText: String) {
        try {
            countdownIntent.putExtra(EXTRA_MATCHED_TEXT, matchedText)
            sendBroadcast(countdownIntent)
        } catch (e: Exception) {
            Log.e(TAG, "倒计时广播发送失败: ${e.message}")
        }
    }

    /**
     * 更新设置
     */
    fun updateSettings() {
        soundManager.updateVolume()
    }
}
