package com.accessibility.adx.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.accessibility.adx.BuildConfig
import com.accessibility.adx.Constants
import com.accessibility.adx.PreferencesManager
import com.accessibility.adx.PermissionUtils
import com.accessibility.adx.R
import com.accessibility.adx.databinding.ActivityMainBinding
import com.accessibility.adx.service.AdDetectionService
import com.accessibility.adx.service.DetectionService
import com.accessibility.adx.service.FloatWindowService
import com.accessibility.adx.Constants.ACTION_AD_DETECTED
import com.accessibility.adx.Constants.ACTION_DETECTION_STATUS_CHANGED
import com.accessibility.adx.Constants.ACTION_STOP
import com.accessibility.adx.Constants.ACTION_APP_STATE_CHANGED

/**
 * 主界面Activity
 * 控制应用的主要功能和状态显示
 * 作者：古封
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var localBroadcastManager: LocalBroadcastManager
    
    // 当前应用状态
    private var currentPackageName = ""
    private var isCurrentAppSupported = false

    // 权限请求
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startServices()
        } else {
            Toast.makeText(this, "通知权限被拒绝，部分功能可能受限", Toast.LENGTH_SHORT).show()
            startServices()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager.getInstance(this)
        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        setupViews()
        setupClickListeners()
        registerBroadcastReceiver()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        localBroadcastManager.unregisterReceiver(broadcastReceiver)
    }

    /**
     * 初始化视图
     */
    private fun setupViews() {
        // 版本信息（显示作者）
        binding.tvVersion.text = getString(R.string.app_version_format, BuildConfig.VERSION_NAME)
    }

    /**
     * 设置点击监听
     */
    private fun setupClickListeners() {
        // 无障碍服务开关
        binding.switchAccessibility.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (PermissionUtils.isAccessibilityServiceEnabled(this, AdDetectionService::class.java.name)) {
                    enableAccessibility()
                } else {
                    binding.switchAccessibility.isChecked = false
                    PermissionUtils.openAccessibilitySettings(this)
                }
            } else {
                disableAccessibility()
            }
        }

        // 悬浮窗开关
        binding.switchFloatWindow.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (PermissionUtils.canDrawOverlays(this)) {
                    prefs.isFloatWindowEnabled = true
                    startFloatWindowService()
                } else {
                    binding.switchFloatWindow.isChecked = false
                    PermissionUtils.openOverlaySettings(this)
                }
            } else {
                prefs.isFloatWindowEnabled = false
                stopFloatWindowService()
            }
        }

        // 震动开关
        binding.switchVibration.setOnCheckedChangeListener { _, isChecked ->
            prefs.isVibrationEnabled = isChecked
        }

        // 声音开关
        binding.switchSound.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSoundEnabled = isChecked
        }

        // 设置按钮
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // 关于按钮
        binding.btnClearStats.setOnClickListener {
            showAboutDialog()
        }

        // 清除统计（长按）
        binding.tvTotalCount.setOnLongClickListener {
            showClearStatsDialog()
            true
        }

        // 权限提示点击
        binding.tvAccessibilityHint.setOnClickListener {
            PermissionUtils.openAccessibilitySettings(this)
        }
        
        // 适配名单按钮点击
        binding.tvTotalCount.setOnClickListener {
            // 打开适配名单
            startActivity(Intent(this, SupportedAppsActivity::class.java))
        }
    }

    /**
     * 显示关于对话框
     */
    private fun showAboutDialog() {
        val aboutMessage = """
            ${getString(R.string.app_name_full)}
            
            ${getString(R.string.about_author)}
            
            ${getString(R.string.about_description)}
            
            版本：${BuildConfig.VERSION_NAME}
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_about_title))
            .setMessage(aboutMessage)
            .setPositiveButton(R.string.dialog_confirm, null)
            .setNeutralButton(R.string.btn_supported_apps) { _, _ ->
                startActivity(Intent(this, SupportedAppsActivity::class.java))
            }
            .show()
    }

    /**
     * 注册广播接收器
     */
    private fun registerBroadcastReceiver() {
        localBroadcastManager.registerReceiver(
            broadcastReceiver,
            IntentFilter(ACTION_AD_DETECTED)
        )
        localBroadcastManager.registerReceiver(
            broadcastReceiver,
            IntentFilter(ACTION_DETECTION_STATUS_CHANGED)
        )
        localBroadcastManager.registerReceiver(
            broadcastReceiver,
            IntentFilter(ACTION_APP_STATE_CHANGED)
        )
    }

    /**
     * 广播接收器
     */
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_AD_DETECTED -> {
                    updateStatistics()
                }
                ACTION_DETECTION_STATUS_CHANGED -> {
                    updateStatusText()
                    updateFloatWindowSwitch()
                }
                ACTION_APP_STATE_CHANGED -> {
                    // 应用状态变化，更新当前应用信息
                    currentPackageName = intent.getStringExtra(Constants.EXTRA_CURRENT_PACKAGE) ?: ""
                    isCurrentAppSupported = intent.getBooleanExtra(Constants.EXTRA_IS_SUPPORTED, false)
                    updateStatusText()
                }
            }
        }
    }

    /**
     * 更新UI状态
     */
    private fun updateUI() {
        // 无障碍服务状态
        val accessibilityEnabled = PermissionUtils.isAccessibilityServiceEnabled(
            this, AdDetectionService::class.java.name
        )
        binding.switchAccessibility.isChecked = accessibilityEnabled
        prefs.isAccessibilityEnabled = accessibilityEnabled

        // 显示/隐藏权限提示
        binding.tvAccessibilityHint.visibility = if (accessibilityEnabled) View.GONE else View.VISIBLE

        // 悬浮窗状态
        binding.switchFloatWindow.isChecked = prefs.isFloatWindowEnabled && PermissionUtils.canDrawOverlays(this)

        // 震动和声音状态
        binding.switchVibration.isChecked = prefs.isVibrationEnabled
        binding.switchSound.isChecked = prefs.isSoundEnabled

        // 更新状态文本
        updateStatusText()

        // 更新统计数据
        updateStatistics()
    }

    /**
     * 更新状态文本
     */
    private fun updateStatusText() {
        val statusText = when {
            AdDetectionService.isRunning && isCurrentAppSupported -> {
                val appName = Constants.getSupportedApp(currentPackageName)?.appName ?: currentPackageName
                getString(R.string.status_listening, appName)
            }
            AdDetectionService.isRunning && !isCurrentAppSupported && currentPackageName.isNotEmpty() -> {
                getString(R.string.status_not_supported_app)
            }
            prefs.isAccessibilityEnabled -> getString(R.string.status_running)
            else -> getString(R.string.status_disabled)
        }
        binding.tvStatus.text = statusText
        
        // 根据状态设置颜色
        if (isCurrentAppSupported) {
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_enabled))
        } else if (currentPackageName.isNotEmpty()) {
            binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_warning))
        }
    }

    /**
     * 更新统计数据
     */
    private fun updateStatistics() {
        binding.tvTotalCount.text = prefs.totalCount.toString()
        binding.tvTodayCount.text = prefs.todayCount.toString()
    }

    /**
     * 更新悬浮窗开关状态
     */
    private fun updateFloatWindowSwitch() {
        val canShow = prefs.isFloatWindowEnabled && PermissionUtils.canDrawOverlays(this)
        binding.switchFloatWindow.isChecked = canShow
    }

    /**
     * 启用无障碍服务
     */
    private fun enableAccessibility() {
        prefs.isAccessibilityEnabled = true
        binding.tvAccessibilityHint.visibility = View.GONE
        
        // 请求通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        
        startServices()
    }

    /**
     * 禁用无障碍服务
     */
    private fun disableAccessibility() {
        prefs.isAccessibilityEnabled = false
        binding.tvAccessibilityHint.visibility = View.VISIBLE
        stopServices()
    }

    /**
     * 启动服务
     */
    private fun startServices() {
        // 启动检测服务
        val detectionIntent = Intent(this, DetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(detectionIntent)
        } else {
            startService(detectionIntent)
        }

        // 启动悬浮窗服务
        if (prefs.isFloatWindowEnabled) {
            startFloatWindowService()
        }
    }

    /**
     * 停止服务
     */
    private fun stopServices() {
        // 停止检测服务
        val detectionIntent = Intent(this, DetectionService::class.java).apply {
            action = Constants.ACTION_STOP
        }
        startService(detectionIntent)

        // 停止悬浮窗服务
        stopFloatWindowService()
    }

    /**
     * 启动悬浮窗服务
     */
    private fun startFloatWindowService() {
        if (!PermissionUtils.canDrawOverlays(this)) {
            PermissionUtils.openOverlaySettings(this)
            return
        }
        
        val intent = Intent(this, FloatWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    /**
     * 停止悬浮窗服务
     */
    private fun stopFloatWindowService() {
        val intent = Intent(this, FloatWindowService::class.java)
        stopService(intent)
    }

    /**
     * 显示清除统计对话框
     */
    private fun showClearStatsDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_clear_title)
            .setMessage(R.string.dialog_clear_message)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                prefs.clearStatistics()
                updateStatistics()
                Toast.makeText(this, R.string.tip_stats_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }
}
