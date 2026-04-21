package com.accessibility.adx.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
 * "领时长"循环执行
 * 作者：古封
 */
class AdDetectionService : AccessibilityService() {

    companion object {
        private const val TAG = "AdDetectionService"
        
        // 服务状态
        @Volatile
        var isRunning = false
            private set
        
        // 事件类型过滤器（快速响应）
        private val EVENT_TYPES = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_VIEW_CLICKED or
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        
        // 检测间隔
        private const val DETECTION_INTERVAL = 500L
        
        // 任务完成等待时间
        private const val TASK_COMPLETE_WAIT = 1500L
    }

    // 偏好设置
    private val prefs by lazy { com.accessibility.adx.PreferencesManager.getInstance(this) }
    
    // 广告检测器
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
    
    // WakeLock保持CPU唤醒
    private var wakeLock: PowerManager.WakeLock? = null

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
    
    // 应用状态广播意图
    private val appStateIntent by lazy {
        Intent(Constants.ACTION_APP_STATE_CHANGED).apply {
            setPackage(packageName)
        }
    }

    // 上次检测的应用包名
    private var lastPackageName = ""
    
    // 循环计数显示
    private var currentLoopCount = 0

    // 状态检查Runnable
    private val stateCheckRunnable = object : Runnable {
        override fun run() {
            if (isRunning && !isProcessing) {
                performDetection()
            }
            handler.postDelayed(this, DETECTION_INTERVAL)
        }
    }

    // 服务保活Runnable
    private val keepAliveRunnable = object : Runnable {
        override fun run() {
            // 发送本地广播保活
            val intent = Intent(Constants.ACTION_KEEP_ALIVE).apply {
                setPackage(packageName)
            }
            sendBroadcast(intent)
            handler.postDelayed(this, 30000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "【服务创建】")
        initializeComponents()
        updateScreenSize()
        acquireWakeLock()
    }

    /**
     * 初始化组件
     */
    private fun initializeComponents() {
        // 初始化检测器
        adDetector = AdDetectorSimple(prefs)
        adDetector.setCallback(object : AdDetectorSimple.DetectionCallback {
            override fun onStateChanged(state: AdDetectorSimple.AdState, action: AdDetectorSimple.Action, matchedText: String) {
                handleStateChange(state, action, matchedText)
            }
            
            override fun onLog(log: String) {
                Log.d(TAG, log)
            }
        })

        // 初始化震动管理器
        vibrationManager = VibrationManager(this)
        
        // 初始化声音管理器
        soundManager = SoundManager(this)
    }

    /**
     * 获取屏幕尺寸
     */
    private fun updateScreenSize() {
        try {
            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            adDetector.updateScreenSize(screenWidth, screenHeight)
            Log.d(TAG, "【屏幕尺寸】${screenWidth}x${screenHeight}")
        } catch (e: Exception) {
            screenWidth = 1080
            screenHeight = 1920
            Log.e(TAG, "【屏幕尺寸获取失败】使用默认值")
        }
    }

    /**
     * 获取WakeLock保持CPU唤醒
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AdDetector::WakeLock"
            ).apply {
                acquire(10 * 60 * 60 * 1000L) // 最多10小时
            }
            Log.d(TAG, "【WakeLock】已获取")
        } catch (e: Exception) {
            Log.e(TAG, "【WakeLock获取失败】${e.message}")
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
        
        // 启动保活机制
        handler.post(keepAliveRunnable)
        
        // 发送状态变化广播
        sendBroadcast(Intent(Constants.ACTION_DETECTION_STATUS_CHANGED))
        
        Log.d(TAG, "【服务启动】作者：古封 - 全自动领时长服务已开启")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        // 切换应用时处理
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            if (packageName != lastPackageName) {
                lastPackageName = packageName
                
                // 检查是否是支持的APP
                val isSupported = Constants.isAppSupported(packageName)
                
                Log.d(TAG, "【应用切换】$packageName (${if (isSupported) "已适配" else "未适配"})")
                
                // 发送应用状态广播
                appStateIntent.putExtra(Constants.EXTRA_CURRENT_PACKAGE, packageName)
                appStateIntent.putExtra(Constants.EXTRA_IS_SUPPORTED, isSupported)
                sendBroadcast(appStateIntent)
                
                // 重置检测器
                adDetector.reset()
                adDetector.setSupportedApp(isSupported)
            }
        }
        
        // 事件触发立即检测（快速响应）
        if (event.eventType and EVENT_TYPES != 0) {
            performDetection()
        }
    }

    /**
     * 执行检测
     */
    private fun performDetection() {
        if (isProcessing) return
        if (adDetector.isPaused()) return
        
        // 如果当前APP不在适配名单中，跳过检测
        if (!adDetector.isCurrentAppSupported() && lastPackageName.isNotEmpty()) {
            return
        }
        
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
        } catch (e: Exception) {
            Log.e(TAG, "【检测异常】${e.message}")
        } finally {
            isProcessing = false
        }
    }

    /**
     * 执行动作
     */
    private fun executeAction(result: AdDetectorSimple.DetectionResult) {
        when (result.action) {
            AdDetectorSimple.Action.CLICK_CLAIM_TIME -> {
                Log.d(TAG, "【执行动作】点击领时长: ${result.matchedText}")
                result.targetNode?.let { node ->
                    if (adDetector.performClick(node)) {
                        detectionCount++
                        prefs.incrementCount()
                        vibrationManager.vibrate()
                        soundManager.playSound()
                        adDetector.updateState(AdDetectorSimple.AdState.ENTERING_AD)
                        currentLoopCount = adDetector.getLoopCount()
                        broadcastState("进入广告", result.matchedText, currentLoopCount)
                    }
                }
            }
            
            AdDetectorSimple.Action.CLICK_REWARD -> {
                Log.d(TAG, "【执行动作】点击领取: ${result.matchedText}")
                result.targetNode?.let { node ->
                    if (adDetector.performClick(node)) {
                        vibrationManager.vibrate()
                        soundManager.playSound()
                        adDetector.updateState(AdDetectorSimple.AdState.AD_CLICKING)
                        broadcastState("领取奖励", result.matchedText)
                    }
                }
            }
            
            AdDetectorSimple.Action.CLICK_CLOSE -> {
                Log.d(TAG, "【执行动作】关闭广告: ${result.matchedText}")
                result.targetNode?.let { node ->
                    if (adDetector.performClick(node)) {
                        adDetector.updateState(AdDetectorSimple.AdState.TASK_COMPLETE)
                        vibrationManager.vibrate()
                        vibrationManager.vibrate()
                        soundManager.playSound()
                        broadcastState("广告关闭", result.matchedText)
                        
                        // 延迟检查并继续监听
                        handler.postDelayed({
                            adDetector.reset()
                            Log.d(TAG, "【返回监听】继续检测领时长按钮")
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
            }
            
            AdDetectorSimple.Action.COMPLETE -> {
                Log.d(TAG, "【任务完成】${result.matchedText}")
                adDetector.reset()
                broadcastState("任务完成", result.matchedText)
            }
            
            AdDetectorSimple.Action.NONE -> {
                // 无需操作
            }
        }
    }

    /**
     * 处理状态变化
     */
    private fun handleStateChange(state: AdDetectorSimple.AdState, action: AdDetectorSimple.Action, matchedText: String) {
        when (state) {
            AdDetectorSimple.AdState.IDLE -> {
                Log.d(TAG, "【监听中】等待领时长按钮")
            }
            AdDetectorSimple.AdState.AD_COUNTDOWN -> {
                Log.d(TAG, "【倒计时】${matchedText}")
            }
            AdDetectorSimple.AdState.AD_REWARD_READY -> {
                Log.d(TAG, "【可领取】${matchedText}")
                vibrationManager.vibrate()
            }
            AdDetectorSimple.AdState.POPUP_HANDLING -> {
                Log.d(TAG, "【弹窗】${matchedText}")
                vibrationManager.vibrate()
            }
            AdDetectorSimple.AdState.AD_SUCCESS -> {
                Log.d(TAG, "【成功】${matchedText}")
            }
            AdDetectorSimple.AdState.TASK_COMPLETE -> {
                Log.d(TAG, "【完成】${matchedText}")
                soundManager.playSound()
            }
            AdDetectorSimple.AdState.PAUSED -> {
                Log.w(TAG, "【暂停】${matchedText}")
            }
            else -> {}
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "【服务中断】尝试重连...")
        // 延迟重连
        handler.postDelayed({
            if (!isRunning) {
                Log.d(TAG, "【重连检测】")
            }
        }, 1000L)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        
        // 移除所有回调
        handler.removeCallbacks(stateCheckRunnable)
        handler.removeCallbacks(keepAliveRunnable)
        
        // 释放WakeLock
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
            Log.d(TAG, "【WakeLock】已释放")
        } catch (e: Exception) {
            Log.e(TAG, "【WakeLock释放失败】${e.message}")
        }
        
        prefs.isAccessibilityEnabled = false
        adDetector.setCallback(null)
        soundManager.release()
        
        // 发送状态变化广播
        sendBroadcast(Intent(Constants.ACTION_DETECTION_STATUS_CHANGED))
        
        Log.d(TAG, "【服务停止】全自动领时长服务已关闭")
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
    private fun broadcastState(action: String, matchedText: String, loopCount: Int = 0) {
        try {
            stateIntent.putExtra(Constants.EXTRA_DETECTION_STATE, action)
            stateIntent.putExtra(Constants.EXTRA_MATCHED_TEXT, matchedText)
            if (loopCount > 0) {
                stateIntent.putExtra(Constants.EXTRA_LOOP_COUNT, loopCount)
            }
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
        updateScreenSize()
    }
}
