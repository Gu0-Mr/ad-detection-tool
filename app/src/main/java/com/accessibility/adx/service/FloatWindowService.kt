package com.accessibility.adx.service

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import android.widget.ViewAnimator
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.accessibility.adx.AdXApplication
import com.accessibility.adx.PreferencesManager
import com.accessibility.adx.R
import com.accessibility.adx.ui.MainActivity
import com.accessibility.adx.util.NotificationHelper

/**
 * 悬浮窗服务
 * 在屏幕上显示可拖动的悬浮窗口
 */
class FloatWindowService : Service() {

    companion object {
        private const val TAG = "FloatWindowService"
        
        // 通知ID
        private const val NOTIFICATION_ID = 1002
        
        // 默认位置（internal，允许扩展属性访问）
        internal const val DEFAULT_X = 200
        internal const val DEFAULT_Y = 300
        
        // 拖动相关
        private const val HANDLE_MODE_NONE = 0
        private const val HANDLE_MODE_DRAG = 1
        
        @Volatile
        var isShowing = false
            private set
    }

    // 窗口管理器
    private lateinit var windowManager: WindowManager
    
    // 悬浮窗布局
    private var floatView: View? = null
    
    // 布局参数
    private var layoutParams: WindowManager.LayoutParams? = null
    
    // 偏好设置
    private lateinit var prefs: PreferencesManager
    
    // 触摸模式
    private var handleMode = HANDLE_MODE_NONE
    
    // 初始触摸位置
    private var lastX = 0f
    private var lastY = 0f
    private var firstX = 0f
    private var firstY = 0f
    
    // 视图引用
    private var tvStatus: TextView? = null
    private var tvCount: TextView? = null
    private var btnVibration: ImageButton? = null
    private var btnSound: ImageButton? = null
    private var btnClose: ImageButton? = null
    private var viewIndicator: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = PreferencesManager.getInstance(this)
        
        // 注册广播接收器
        registerReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 启动前台服务
        val notification = createNotification()
        startForeground(NotificationHelper.getNotificationId(), notification)
        
        // 显示悬浮窗
        showFloatWindow()
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        hideFloatWindow()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver)
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, AdXApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_float_window)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    /**
     * 显示悬浮窗
     */
    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun showFloatWindow() {
        if (floatView != null) return

        try {
            // 加载布局
            floatView = LayoutInflater.from(this).inflate(R.layout.layout_float_window, null)
            
            // 获取视图引用
            tvStatus = floatView?.findViewById(R.id.tvFloatStatus)
            tvCount = floatView?.findViewById(R.id.tvFloatCount)
            btnVibration = floatView?.findViewById(R.id.btnFloatVibration)
            btnSound = floatView?.findViewById(R.id.btnFloatSound)
            btnClose = floatView?.findViewById(R.id.btnFloatClose)
            viewIndicator = floatView?.findViewById(R.id.viewIndicator)

            // 设置点击事件
            setupClickListeners()
            
            // 设置拖动事件
            setupTouchListener()

            // 创建布局参数
            layoutParams = createLayoutParams()

            // 添加到窗口
            windowManager.addView(floatView, layoutParams)
            
            isShowing = true
            
            // 更新UI状态
            updateUI()
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 创建布局参数
     */
    private fun createLayoutParams(): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.x ?: DEFAULT_X
            y = prefs.y ?: DEFAULT_Y
        }
        return params
    }

    /**
     * 设置点击事件
     */
    private fun setupClickListeners() {
        // 点击打开主界面
        floatView?.setOnClickListener {
            openMainActivity()
        }

        // 震动开关
        btnVibration?.setOnClickListener {
            prefs.isVibrationEnabled = !prefs.isVibrationEnabled
            updateVibrationButton()
        }

        // 声音开关
        btnSound?.setOnClickListener {
            prefs.isSoundEnabled = !prefs.isSoundEnabled
            updateSoundButton()
        }

        // 关闭悬浮窗
        btnClose?.setOnClickListener {
            stopSelf()
        }
    }

    /**
     * 设置触摸事件（拖动）
     */
    private fun setupTouchListener() {
        floatView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    handleMode = HANDLE_MODE_DRAG
                    lastX = event.rawX
                    lastY = event.rawY
                    firstX = event.rawX
                    firstY = event.rawY
                    true
                }
                
                MotionEvent.ACTION_MOVE -> {
                    if (handleMode == HANDLE_MODE_DRAG) {
                        val dx = event.rawX - lastX
                        val dy = event.rawY - lastY
                        
                        // 检查是否真的在拖动
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            layoutParams?.let { params ->
                                params.x += dx.toInt()
                                params.y += dy.toInt()
                                windowManager.updateViewLayout(floatView, params)
                            }
                            lastX = event.rawX
                            lastY = event.rawY
                        }
                    }
                    true
                }
                
                MotionEvent.ACTION_UP -> {
                    val movedX = Math.abs(event.rawX - firstX)
                    val movedY = Math.abs(event.rawY - firstY)
                    
                    // 如果移动距离很小，视为点击
                    if (movedX < 10 && movedY < 10) {
                        // 由setOnClickListener处理
                    } else {
                        // 保存位置
                        layoutParams?.let { params ->
                            prefs.x = params.x
                            prefs.y = params.y
                        }
                    }
                    
                    handleMode = HANDLE_MODE_NONE
                    true
                }
                
                else -> false
            }
        }
    }

    /**
     * 隐藏悬浮窗
     */
    private fun hideFloatWindow() {
        try {
            floatView?.let {
                windowManager.removeView(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            floatView = null
            layoutParams = null
            isShowing = false
        }
    }

    /**
     * 打开主界面
     */
    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    /**
     * 更新UI状态
     */
    private fun updateUI() {
        updateStatus()
        updateCount()
        updateVibrationButton()
        updateSoundButton()
        updateIndicator()
    }

    /**
     * 更新状态文本
     */
    private fun updateStatus() {
        val statusText = if (AdDetectionService.isRunning) {
            getString(R.string.float_detecting)
        } else {
            getString(R.string.status_stopped)
        }
        tvStatus?.text = statusText
    }

    /**
     * 更新计数
     */
    private fun updateCount() {
        val countText = getString(R.string.float_count, prefs.totalCount)
        tvCount?.text = countText
    }

    /**
     * 更新震动按钮状态
     */
    private fun updateVibrationButton() {
        btnVibration?.alpha = if (prefs.isVibrationEnabled) 1.0f else 0.5f
    }

    /**
     * 更新声音按钮状态
     */
    private fun updateSoundButton() {
        btnSound?.alpha = if (prefs.isSoundEnabled) 1.0f else 0.5f
    }

    /**
     * 更新状态指示器
     */
    private fun updateIndicator() {
        val colorRes = if (AdDetectionService.isRunning) {
            android.R.color.holo_green_light
        } else {
            android.R.color.darker_gray
        }
        viewIndicator?.setBackgroundColor(getColor(colorRes))
    }

    /**
     * 更新计数（供外部调用）
     */
    fun refreshCount() {
        updateCount()
    }

    /**
     * 更新检测状态（供外部调用）
     */
    fun refreshStatus() {
        updateStatus()
        updateIndicator()
    }

    // ==================== 广播接收器 ====================
    
    private val broadcastReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_AD_DETECTED -> {
                    refreshCount()
                }
                ACTION_DETECTION_STATUS_CHANGED -> {
                    refreshStatus()
                }
            }
        }
    }

    private fun registerReceiver() {
        val manager = LocalBroadcastManager.getInstance(this)
        manager.registerReceiver(
            broadcastReceiver,
            android.content.IntentFilter(ACTION_AD_DETECTED)
        )
        manager.registerReceiver(
            broadcastReceiver,
            android.content.IntentFilter(ACTION_DETECTION_STATUS_CHANGED)
        )
    }
}

// 扩展：偏好设置的位置保存
private var PreferencesManager.x: Int
    get() = try {
        val field = javaClass.getDeclaredField("prefs")
        field.isAccessible = true
        val prefs = field.get(this) as android.content.SharedPreferences
        prefs.getInt("float_x", FloatWindowService.DEFAULT_X)
    } catch (e: Exception) { FloatWindowService.DEFAULT_X }
    set(value) {
        try {
            val field = javaClass.getDeclaredField("prefs")
            field.isAccessible = true
            val prefs = field.get(this) as android.content.SharedPreferences
            prefs.edit().putInt("float_x", value).apply()
        } catch (e: Exception) { }
    }

private var PreferencesManager.y: Int
    get() = try {
        val field = javaClass.getDeclaredField("prefs")
        field.isAccessible = true
        val prefs = field.get(this) as android.content.SharedPreferences
        prefs.getInt("float_y", FloatWindowService.DEFAULT_Y)
    } catch (e: Exception) { FloatWindowService.DEFAULT_Y }
    set(value) {
        try {
            val field = javaClass.getDeclaredField("prefs")
            field.isAccessible = true
            val prefs = field.get(this) as android.content.SharedPreferences
            prefs.edit().putInt("float_y", value).apply()
        } catch (e: Exception) { }
    }
