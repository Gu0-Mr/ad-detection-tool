package com.accessibility.adx.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.accessibility.adx.Constants
import com.accessibility.adx.detector.AdDetectorSimple
import com.accessibility.adx.util.SoundManager
import com.accessibility.adx.util.VibrationManager

/**
 * 全自动广告点击服务
 * 自动执行广告领取完整流程
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
        
        // 状态检查间隔
        private const val STATE_CHECK_INTERVAL = 500L
        
        // 任务完成等待时间
        private const val TASK_COMPLETE_WAIT = 2000L
    }

    // 偏好设置
    private val prefs by lazy { com.accessibility.adx.PreferencesManager.getInstance(this) }
    
    // 广告检测器（全自动版）
    private lateinit var adDetector: AdDetectorSimple
    
    // 震动管理器
    private lateinit var vibrationManager: VibrationManager
    
    // 声音管理器
    private lateinit var soundManager: SoundManager
    
    // 屏幕尺寸
    private var screenWidth = 0
    private var screenHeight = 0
    
    // 检测计数
    private var detectionCount = 0
    
    // Handler用于定时检查
    private val handler = Handler(Looper.getMainLooper())
    private var isProcessing = false

    // 广播意图
    private val detectionIntent by lazy {
        Intent(Constants.ACTION_AD_DETECTED).apply {
            setPackage(packageName)
        }
    }

    // 状态广播意图
    private val stateIntent by lazy {
        Intent(Constants.ACTION_AD_STATE_CHANGED).apply {
            setPackage(packageName)
        }
    }

    // 上次检测的应用包名
    private var lastPackageName = ""

    // 状态检查Runnable
    private val stateCheckRunnable = object : Runnable {
        override fun run() {
            if (isRunning && !isProcessing) {
                performDetection()
            }
            handler.postDelayed(this, STATE_CHECK_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        initializeComponents()
        updateScreenSize()
    }

    /**
     * 初始化组件
     */
    private fun initializeComponents() {
        // 初始化全自动检测器
        adDetector = AdDetectorSimple(prefs)
        adDetector.setCallback(object : AdDetectorSimple.DetectionCallback {
            override fun onStateChanged(state: AdDetectorSimple.AdState, action: AdDetectorSimple.Action, matchedText: String) {
                handleStateChange(state, action, matchedText)
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
        
        // 启动状态检查循环
        handler.post(stateCheckRunnable)
        
        // 发送状态变化广播
        sendBroadcast(Intent(Constants.ACTION_DETECTION_STATUS_CHANGED))
        
        Log.d(TAG, "【服务启动】全自动广告点击服务已开启")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // 切换应用时重置
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            if (packageName != lastPackageName) {
                lastPackageName = packageName
                Log.d(TAG, "【应用切换】$packageName，重置检测器")
                adDetector.reset()
            }
        }
    }

    /**
     * 执行检测
     */
    private fun performDetection() {
        if (isProcessing) return
        
        isProcessing = true
        try {
            val rootNode = rootInActiveWindow ?: run {
                isProcessing = false
                return
            }
            
            try {
                val result = adDetector.detect(rootNode, screenWidth, screenHeight)
                if (result != null) {
                    executeAction(result)
                }
            } finally {
                rootNode.recycle()
            }
        } finally {
            isProcessing = false
        }
    }

    /**
     * 执行动作
     */
    private fun executeAction(result: AdDetectorSimple.DetectionResult) {
        when (result.action) {
            AdDetectorSimple.Action.CLICK_REWARD -> {
                Log.d(TAG, "【执行动作】点击领取奖励: ${result.matchedText}")
                result.targetNode?.let { node ->
                    if (adDetector.performClick(node)) {
                        detectionCount++
                        prefs.incrementCount()
                        vibrationManager.vibrate()
                        soundManager.playSound()
                        adDetector.updateState(AdDetectorSimple.AdState.CLICKING_REWARD)
                        broadcastState("点击领取奖励", result.matchedText)
                    }
                }
            }
            
            AdDetectorSimple.Action.CLICK_CLOSE -> {
                Log.d(TAG, "【执行动作】关闭广告: ${result.matchedText}")
                result.targetNode?.let { node ->
                    if (adDetector.performClick(node)) {
                        adDetector.updateState(AdDetectorSimple.AdState.CLOSING_AD)
                        vibrationManager.vibrate()
                        broadcastState("关闭广告", result.matchedText)
                        
                        // 延迟检查是否关闭成功
                        handler.postDelayed({
                            checkCloseSuccess()
                        }, TASK_COMPLETE_WAIT)
                    }
                }
            }
            
            AdDetectorSimple.Action.CLICK_POPUP -> {
                Log.d(TAG, "【执行动作】点击弹窗: ${result.matchedText}")
                result.targetNode?.let { node ->
                    if (adDetector.performClick(node)) {
                        vibrationManager.vibrate()
                        broadcastState("处理弹窗", result.matchedText)
                    }
                }
            }
            
            AdDetectorSimple.Action.WAIT -> {
                // 倒计时中，无需操作
                Log.d(TAG, "【等待中】${result.matchedText}")
            }
            
            AdDetectorSimple.Action.COMPLETE -> {
                Log.d(TAG, "【任务完成】${result.matchedText}")
                adDetector.reset()
                broadcastState("任务完成", result.matchedText)
                
                // 任务完成后短暂等待，然后继续监听
                handler.postDelayed({
                    broadcastState("开始新任务", "继续监听广告")
                }, TASK_COMPLETE_WAIT)
            }
            
            AdDetectorSimple.Action.NONE -> {
                // 无需操作
            }
        }
    }

    /**
     * 检查关闭是否成功
     */
    private fun checkCloseSuccess() {
        val currentState = adDetector.getCurrentState()
        Log.d(TAG, "【关闭检查】当前状态: $currentState")
        
        if (currentState == AdDetectorSimple.AdState.CLOSING_AD || 
            currentState == AdDetectorSimple.AdState.REWARD_SUCCESS) {
            // 还在这些状态，说明广告可能没关闭成功
            Log.d(TAG, "【关闭检查】广告可能未关闭，再次尝试")
            performDetection()
        } else {
            // 状态已变更，广告已关闭
            Log.d(TAG, "【关闭检查】广告已成功关闭")
            adDetector.reset()
        }
    }

    /**
     * 处理状态变化
     */
    private fun handleStateChange(state: AdDetectorSimple.AdState, action: AdDetectorSimple.Action, matchedText: String) {
        when (state) {
            AdDetectorSimple.AdState.COUNTDOWN -> {
                Log.d(TAG, "【状态更新】倒计时进行中: $matchedText")
            }
            AdDetectorSimple.AdState.REWARD_READY -> {
                Log.d(TAG, "【状态更新】可领取奖励: $matchedText")
                vibrationManager.vibrate()
                soundManager.playSound()
            }
            AdDetectorSimple.AdState.POPUP_DETECTED -> {
                Log.d(TAG, "【状态更新】检测到弹窗: $matchedText")
                vibrationManager.vibrate()
                vibrationManager.vibrate()
            }
            AdDetectorSimple.AdState.REWARD_SUCCESS -> {
                Log.d(TAG, "【状态更新】领取成功，准备关闭: $matchedText")
                vibrationManager.vibrate()
                vibrationManager.vibrate()
                soundManager.playSound()
            }
            AdDetectorSimple.AdState.TASK_COMPLETE -> {
                Log.d(TAG, "【状态更新】任务完成: $matchedText")
                soundManager.playSound()
                soundManager.playSound()
            }
            else -> {}
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "【服务中断】")
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(stateCheckRunnable)
        prefs.isAccessibilityEnabled = false
        adDetector.setCallback(null)
        soundManager.release()
        
        // 发送状态变化广播
        sendBroadcast(Intent(Constants.ACTION_DETECTION_STATUS_CHANGED))
        
        Log.d(TAG, "【服务停止】全自动广告点击服务已关闭")
    }

    /**
     * 广播检测事件
     */
    private fun broadcastDetection(matchedText: String) {
        try {
            detectionIntent.putExtra(Constants.EXTRA_DETECTION_COUNT, detectionCount)
            detectionIntent.putExtra(Constants.EXTRA_TOTAL_COUNT, prefs.totalCount)
            detectionIntent.putExtra(Constants.EXTRA_MATCHED_TEXT, matchedText)
            sendBroadcast(detectionIntent)
        } catch (e: Exception) {
            Log.e(TAG, "广播发送失败: ${e.message}")
        }
    }

    /**
     * 广播状态变化
     */
    private fun broadcastState(action: String, matchedText: String) {
        try {
            stateIntent.putExtra(Constants.EXTRA_DETECTION_STATE, action)
            stateIntent.putExtra(Constants.EXTRA_MATCHED_TEXT, matchedText)
            sendBroadcast(stateIntent)
        } catch (e: Exception) {
            Log.e(TAG, "状态广播发送失败: ${e.message}")
        }
    }

    /**
     * 更新设置
     */
    fun updateSettings() {
        soundManager.updateVolume()
    }
}
