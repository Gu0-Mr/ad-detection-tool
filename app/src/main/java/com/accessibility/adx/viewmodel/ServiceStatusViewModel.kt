package com.accessibility.adx.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * 服务状态 ViewModel
 * 使用 LiveData 替代 LocalBroadcastManager 进行服务与 UI 通信
 * 
 * 优势：
 * - 无需担心广播接收器生命周期
 * - 自动在主线程更新 UI
 * - 内存安全，避免内存泄漏
 */
class ServiceStatusViewModel : ViewModel() {

    // 服务运行状态
    private val _isServiceRunning = MutableLiveData(false)
    val isServiceRunning: LiveData<Boolean> = _isServiceRunning

    // 当前检测到的应用包名
    private val _currentPackageName = MutableLiveData("")
    val currentPackageName: LiveData<String> = _currentPackageName

    // 当前应用是否支持
    private val _isCurrentAppSupported = MutableLiveData(false)
    val isCurrentAppSupported: LiveData<Boolean> = _isCurrentAppSupported

    // 广告检测计数
    private val _detectionCount = MutableLiveData(0)
    val detectionCount: LiveData<Int> = _detectionCount

    // 总计数
    private val _totalCount = MutableLiveData(0)
    val totalCount: LiveData<Int> = _totalCount

    // 今日计数
    private val _todayCount = MutableLiveData(0)
    val todayCount: LiveData<Int> = _todayCount

    // 服务状态文本
    private val _statusText = MutableLiveData("")
    val statusText: LiveData<String> = _statusText

    // 检测状态（用于显示状态标签）
    private val _detectionState = MutableLiveData("")
    val detectionState: LiveData<String> = _detectionState

    // 匹配文本
    private val _matchedText = MutableLiveData("")
    val matchedText: LiveData<String> = _matchedText

    // 循环计数
    private val _loopCount = MutableLiveData(0)
    val loopCount: LiveData<Int> = _loopCount

    // 应用状态变化（用于触发即时更新）
    private val _appStateChanged = MutableLiveData(false)
    val appStateChanged: LiveData<Boolean> = _appStateChanged

    /**
     * 更新服务运行状态
     */
    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.postValue(running)
    }

    /**
     * 更新当前应用包名
     */
    fun setCurrentPackage(packageName: String) {
        _currentPackageName.postValue(packageName)
    }

    /**
     * 更新当前应用是否支持
     */
    fun setCurrentAppSupported(supported: Boolean) {
        _isCurrentAppSupported.postValue(supported)
    }

    /**
     * 更新检测计数
     */
    fun setDetectionCount(count: Int) {
        _detectionCount.postValue(count)
    }

    /**
     * 增加检测计数
     */
    fun incrementDetectionCount() {
        _detectionCount.postValue((_detectionCount.value ?: 0) + 1)
    }

    /**
     * 更新总计数
     */
    fun setTotalCount(count: Int) {
        _totalCount.postValue(count)
    }

    /**
     * 增加总计数
     */
    fun incrementTotalCount() {
        _totalCount.postValue((_totalCount.value ?: 0) + 1)
    }

    /**
     * 更新今日计数
     */
    fun setTodayCount(count: Int) {
        _todayCount.postValue(count)
    }

    /**
     * 增加今日计数
     */
    fun incrementTodayCount() {
        _todayCount.postValue((_todayCount.value ?: 0) + 1)
    }

    /**
     * 更新状态文本
     */
    fun setStatusText(text: String) {
        _statusText.postValue(text)
    }

    /**
     * 更新检测状态
     */
    fun setDetectionState(state: String) {
        _detectionState.postValue(state)
    }

    /**
     * 更新匹配文本
     */
    fun setMatchedText(text: String) {
        _matchedText.postValue(text)
    }

    /**
     * 更新循环计数
     */
    fun setLoopCount(count: Int) {
        _loopCount.postValue(count)
    }

    /**
     * 通知应用状态变化（触发即时更新）
     */
    fun notifyAppStateChanged() {
        _appStateChanged.postValue(true)
    }

    /**
     * 重置应用状态变化标志
     */
    fun resetAppStateChanged() {
        _appStateChanged.postValue(false)
    }

    /**
     * 批量更新应用状态
     */
    fun updateAppState(packageName: String, isSupported: Boolean) {
        _currentPackageName.postValue(packageName)
        _isCurrentAppSupported.postValue(isSupported)
        _appStateChanged.postValue(true)
    }

    /**
     * 批量更新检测结果
     */
    fun updateDetectionResult(state: String, matchedText: String, loopCount: Int) {
        _detectionState.postValue(state)
        _matchedText.postValue(matchedText)
        _loopCount.postValue(loopCount)
        incrementDetectionCount()
    }

    /**
     * 重置所有状态
     */
    fun reset() {
        _isServiceRunning.postValue(false)
        _currentPackageName.postValue("")
        _isCurrentAppSupported.postValue(false)
        _detectionCount.postValue(0)
        _statusText.postValue("")
        _detectionState.postValue("")
        _matchedText.postValue("")
        _loopCount.postValue(0)
    }
}
