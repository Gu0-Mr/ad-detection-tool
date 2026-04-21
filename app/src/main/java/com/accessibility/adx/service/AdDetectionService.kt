package com.accessibility.adx.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.accessibility.adx.Constants.ACTION_AD_DETECTED
import com.accessibility.adx.Constants.EXTRA_DETECTION_COUNT
import com.accessibility.adx.Constants.EXTRA_TOTAL_COUNT
import com.accessibility.adx.detector.AdDetector
import com.accessibility.adx.util.SoundManager
import com.accessibility.adx.util.VibrationManager

/**
 * 广告检测无障碍服务
 * 监听屏幕内容变化，检测广告关闭按钮
 */
class AdDetectionService : AccessibilityService() {

    companion object {
        private const val TAG = "AdDetectionService"
        
        // 服务状态
        @Volatile
        var isRunning = false
            private set
        
        // 事件类型过滤器
        private const val EVENT_TYPES = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED
    }

    // 偏好设置
    private val prefs by lazy { com.accessibility.adx.PreferencesManager.getInstance(this) }
    
    // 广告检测器
    private lateinit var adDetector: AdDetector
    
    // 震动管理器
    private lateinit var vibrationManager: VibrationManager
    
    // 声音管理器
    private lateinit var soundManager: SoundManager
    
    // 屏幕尺寸
    private var screenWidth = 0
    private var screenHeight = 0
    
    // 本地检测计数
    private var detectionCount = 0

    // 广播意图
    private val detectionIntent by lazy {
        Intent(ACTION_AD_DETECTED).apply {
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
        // 初始化检测器
        adDetector = AdDetector(prefs)
        adDetector.setCallback(object : AdDetector.DetectionCallback {
            override fun onAdDetected(nodeInfo: AccessibilityNodeInfo, detectionReason: String) {
                handleAdDetection(nodeInfo, detectionReason)
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
            }
        }

        // 执行检测
        val rootNode = event.source ?: return
        try {
            val detected = adDetector.detect(rootNode, screenWidth, screenHeight)
            if (detected) {
                detectionCount++
                prefs.incrementCount()
                broadcastDetection()
            }
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 处理广告检测结果
     */
    private fun handleAdDetection(nodeInfo: AccessibilityNodeInfo, reason: String) {
        // 发送广播
        sendBroadcast(detectionIntent)
        
        // 震动反馈
        vibrationManager.vibrate()
        
        // 声音提示
        soundManager.playSound()
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
    }

    /**
     * 广播检测事件
     */
    private fun broadcastDetection() {
        try {
            detectionIntent.putExtra(EXTRA_DETECTION_COUNT, detectionCount)
            detectionIntent.putExtra(EXTRA_TOTAL_COUNT, prefs.totalCount)
            sendBroadcast(detectionIntent)
        } catch (e: Exception) {
            // 忽略广播发送失败
        }
    }

    /**
     * 更新设置
     */
    fun updateSettings() {
        soundManager.updateVolume()
    }
}
