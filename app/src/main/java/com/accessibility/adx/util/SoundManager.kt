package com.accessibility.adx.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.provider.Settings
import android.util.Log
import com.accessibility.adx.PreferencesManager
import com.accessibility.adx.R

/**
 * 声音管理器
 * 控制声音提示播放
 */
class SoundManager(private val context: Context) {

    private val prefs = PreferencesManager.getInstance(context)
    private var toneGenerator: ToneGenerator? = null
    private var mediaPlayer: MediaPlayer? = null
    
    private val tag = "SoundManager"

    init {
        initToneGenerator()
    }

    /**
     * 初始化ToneGenerator
     */
    private fun initToneGenerator() {
        try {
            val volume = calculateVolume()
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, volume)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize ToneGenerator", e)
        }
    }

    /**
     * 播放提示音
     */
    fun playSound() {
        if (!prefs.isSoundEnabled) return

        try {
            // 优先使用系统提示音
            playToneSound()
        } catch (e: Exception) {
            Log.e(tag, "Failed to play sound", e)
        }
    }

    /**
     * 播放Tone提示音
     */
    private fun playToneSound() {
        try {
            // 停止之前的播放
            toneGenerator?.stopTone()
            
            // 播放提示音 - ToneGenerator.TONE_PROP_BEEP 是较为明显的提示音
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        } catch (e: Exception) {
            Log.e(tag, "Tone playback failed", e)
        }
    }

    /**
     * 播放自定义音效
     */
    fun playCustomSound() {
        if (!prefs.isSoundEnabled) return

        try {
            releaseMediaPlayer()
            
            // 使用系统默认通知音效
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                // 使用系统通知音效URI
                setDataSource(context, Settings.System.DEFAULT_NOTIFICATION_URI)
                setVolume(calculateVolume() / 100f, calculateVolume() / 100f)
                setOnCompletionListener { release() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(tag, "Custom sound playback failed", e)
        }
    }

    /**
     * 计算音量（0-100 -> 0-100）
     */
    private fun calculateVolume(): Int {
        return prefs.soundVolume.coerceIn(0, 100)
    }

    /**
     * 释放资源
     */
    fun release() {
        try {
            toneGenerator?.release()
            toneGenerator = null
            releaseMediaPlayer()
        } catch (e: Exception) {
            Log.e(tag, "Release failed", e)
        }
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(tag, "MediaPlayer release failed", e)
        }
    }

    /**
     * 更新音量设置
     */
    fun updateVolume() {
        try {
            toneGenerator?.release()
            initToneGenerator()
        } catch (e: Exception) {
            Log.e(tag, "Update volume failed", e)
        }
    }
}
