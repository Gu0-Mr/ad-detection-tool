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
import com.accessibility.adx.viewmodel.ServiceStatusViewModel

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
        
        // 全局 ViewModel 实例，用于服务与 UI 通信
        // 注意：实际使用时应该通过 Application 或共享 ViewModelProvider 获取
        var statusViewModel: ServiceStatusViewModel? = null
        
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

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "【服务创建】")
        initializeComponents()
        updateScreenSize()
        acquireWakeLock()
        
        // 更新 ViewModel 状态
        statusViewModel?.setServiceRunning(true)
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
        
        // 启动状态检查
        handler.post(stateCheckRunnable)
        
        Log.d(TAG, "【服务连接】无障碍服务已就绪")
        
        // 更新 ViewModel
        statusViewModel?.setServiceRunning(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        
        val packageName = event.packageName?.toString() ?: return
        
        // 忽略系统应用
        if (isSystemApp(packageName)) return
        
        // 检查是否支持当前应用
        val isSupported = Constants.isAppSupported(packageName)
        
        // 应用包名变化时更新状态
        if (packageName != lastPackageName) {
            lastPackageName = packageName
            currentLoopCount = 0
            
            // 更新 ViewModel
            statusViewModel?.updateAppState(packageName, isSupported)
        }
        
        // 非支持应用不处理
        if (!isSupported) return
        
        // 如果是窗口状态变化，可能需要重新检测
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            handler.post {
                performDetection()
            }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "【服务中断】")
        isProcessing = false
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // 停止所有Runnable
        handler.removeCallbacks(stateCheckRunnable)
        
        // 释放WakeLock
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        
        isRunning = false
        prefs.isAccessibilityEnabled = false
        
        // 更新 ViewModel
        statusViewModel?.setServiceRunning(false)
        
        Log.d(TAG, "【服务销毁】")
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
                    currentLoopCount++
                    detectionCount++
                    
                    // 更新统计数据
                    prefs.totalCount++
                    prefs.todayCount++
                    
                    // 通过 ViewModel 通知更新
                    statusViewModel?.apply {
                        updateDetectionResult(
                            result.state.name,
                            result.matchedText,
                            currentLoopCount
                        )
                        incrementTotalCount()
                        incrementTodayCount()
                    }
                    
                    // 执行点击
                    result.targetNode?.let { node ->
                        if (adDetector.performClick(node)) {
                            // 播放反馈
                            playFeedback()
                            
                            // 状态切换后重置循环计数
                            when (result.state) {
                                AdDetectorSimple.AdState.AD_SUCCESS,
                                AdDetectorSimple.AdState.AD_CLOSING,
                                AdDetectorSimple.AdState.TASK_COMPLETE -> {
                                    currentLoopCount = 0
                                }
                                else -> {}
                            }
                        }
                    }
                }
                
                // 定期输出状态
                if (currentLoopCount > 0 && currentLoopCount % 50 == 0) {
                    Log.d(TAG, "【状态】${adDetector.getCurrentState()} 循环:$currentLoopCount 检测:$detectionCount")
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
     * 处理状态变化
     */
    private fun handleStateChange(state: AdDetectorSimple.AdState, action: AdDetectorSimple.Action, matchedText: String) {
        Log.d(TAG, "【状态变化】$state - $action - $matchedText")
        
        // 更新 ViewModel
        statusViewModel?.apply {
            setDetectionState(state.name)
            setMatchedText(matchedText)
            setLoopCount(currentLoopCount)
        }
        
        when (action) {
            AdDetectorSimple.AdState.AD_SUCCESS -> {
                // 任务完成
                Log.d(TAG, "【任务完成】总计检测: $detectionCount")
            }
            else -> {}
        }
    }

    /**
     * 播放反馈
     */
    private fun playFeedback() {
        if (prefs.isVibrationEnabled) {
            vibrationManager.vibrate()
        }
        if (prefs.isSoundEnabled) {
            soundManager.playClick()
        }
    }

    /**
     * 检查是否是系统应用
     */
    private fun isSystemApp(packageName: String): Boolean {
        val systemApps = setOf(
            "com.android",
            "com.google.android.inputmethod",
            "com.google.android.gms",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.android.launcher2",
            "com.miui.home",
            "com.huawei.android.launcher",
            "com.oppo.launcher",
            "com.vivo.launcher",
            "com.oneplus.launcher",
            "com.samsung.android.launcher"
        )
        return systemApps.any { packageName.startsWith(it) }
    }
}
