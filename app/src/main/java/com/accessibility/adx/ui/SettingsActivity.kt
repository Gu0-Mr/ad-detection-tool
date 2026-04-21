package com.accessibility.adx.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.accessibility.adx.BuildConfig
import com.accessibility.adx.PreferencesManager
import com.accessibility.adx.R
import com.accessibility.adx.databinding.ActivitySettingsBinding
import com.accessibility.adx.util.SoundManager

/**
 * 设置界面Activity
 * 调整应用的各种参数设置
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: PreferencesManager
    private lateinit var soundManager: SoundManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager.getInstance(this)
        soundManager = SoundManager(this)

        setupViews()
        setupListeners()
        loadSettings()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundManager.release()
    }

    /**
     * 初始化视图
     */
    private fun setupViews() {
        // 版本信息
        binding.tvVersion.text = getString(R.string.about_version, BuildConfig.VERSION_NAME)
    }

    /**
     * 设置监听器
     */
    private fun setupListeners() {
        // 返回按钮
        binding.btnBack.setOnClickListener {
            finish()
        }

        // 震动强度调节
        binding.seekBarVibrationIntensity.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        prefs.vibrationIntensity = progress
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        // 震动时长调节
        binding.seekBarVibrationDuration.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        // 最小值为50ms
                        prefs.vibrationDuration = progress + 50
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        // 声音音量调节
        binding.seekBarSoundVolume.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        prefs.soundVolume = progress
                        soundManager.updateVolume()
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )
    }

    /**
     * 加载设置
     */
    private fun loadSettings() {
        // 震动强度
        binding.seekBarVibrationIntensity.progress = prefs.vibrationIntensity

        // 震动时长
        binding.seekBarVibrationDuration.progress = prefs.vibrationDuration - 50

        // 声音音量
        binding.seekBarSoundVolume.progress = prefs.soundVolume
    }
}
