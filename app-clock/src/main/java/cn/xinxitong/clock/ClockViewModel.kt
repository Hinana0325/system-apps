package cn.xinxitong.clock

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ClockViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AlarmStore(application)
    private val appContext = application.applicationContext

    // ---- 闹钟 ----
    val alarms: StateFlow<List<AlarmItem>> = store.alarms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _canScheduleExact = MutableStateFlow(AlarmScheduler.canScheduleExactAlarms(appContext))
    val canScheduleExact: StateFlow<Boolean> = _canScheduleExact.asStateFlow()

    fun refreshExactAlarmPermission() {
        _canScheduleExact.value = AlarmScheduler.canScheduleExactAlarms(appContext)
    }

    fun saveAlarm(alarm: AlarmItem) {
        viewModelScope.launch {
            store.upsert(alarm)
            if (alarm.enabled) AlarmScheduler.schedule(appContext, alarm)
            else AlarmScheduler.cancel(appContext, alarm)
        }
    }

    fun deleteAlarm(alarm: AlarmItem) {
        viewModelScope.launch {
            AlarmScheduler.cancel(appContext, alarm)
            store.delete(alarm.id)
        }
    }

    fun setAlarmEnabled(alarm: AlarmItem, enabled: Boolean) {
        viewModelScope.launch {
            store.setEnabled(alarm.id, enabled)
            val updated = alarm.copy(enabled = enabled)
            if (enabled) AlarmScheduler.schedule(appContext, updated)
            else AlarmScheduler.cancel(appContext, updated)
        }
    }

    // ---- 秒表：真实经过时间（SystemClock.elapsedRealtime，含休眠） ----
    private val _stopwatchElapsed = MutableStateFlow(0L)
    val stopwatchElapsed: StateFlow<Long> = _stopwatchElapsed.asStateFlow()

    private val _stopwatchRunning = MutableStateFlow(false)
    val stopwatchRunning: StateFlow<Boolean> = _stopwatchRunning.asStateFlow()

    private val _laps = MutableStateFlow<List<Long>>(emptyList())
    val laps: StateFlow<List<Long>> = _laps.asStateFlow()

    private var stopwatchBase = 0L
    private var stopwatchStartedAt = 0L
    private var stopwatchJob: Job? = null

    fun startStopwatch() {
        if (_stopwatchRunning.value) return
        stopwatchStartedAt = SystemClock.elapsedRealtime()
        _stopwatchRunning.value = true
        stopwatchJob = viewModelScope.launch {
            while (isActive) {
                _stopwatchElapsed.value = stopwatchBase + (SystemClock.elapsedRealtime() - stopwatchStartedAt)
                delay(50)
            }
        }
    }

    fun pauseStopwatch() {
        if (!_stopwatchRunning.value) return
        stopwatchBase = _stopwatchElapsed.value
        _stopwatchRunning.value = false
        stopwatchJob?.cancel()
        stopwatchJob = null
    }

    fun resetStopwatch() {
        stopwatchJob?.cancel()
        stopwatchJob = null
        stopwatchBase = 0L
        _stopwatchElapsed.value = 0L
        _stopwatchRunning.value = false
        _laps.value = emptyList()
    }

    fun lapStopwatch() {
        if (_stopwatchRunning.value) {
            _laps.value = _laps.value + _stopwatchElapsed.value
        }
    }

    // ---- 倒计时 ----
    private val _timerTotal = MutableStateFlow(0L)
    val timerTotal: StateFlow<Long> = _timerTotal.asStateFlow()

    private val _timerRemaining = MutableStateFlow(0L)
    val timerRemaining: StateFlow<Long> = _timerRemaining.asStateFlow()

    private val _timerRunning = MutableStateFlow(false)
    val timerRunning: StateFlow<Boolean> = _timerRunning.asStateFlow()

    private val _timerFinished = MutableStateFlow(false)
    val timerFinished: StateFlow<Boolean> = _timerFinished.asStateFlow()

    private var timerEndAt = 0L
    private var timerJob: Job? = null

    fun setTimerTotal(millis: Long) {
        if (_timerRunning.value) return
        _timerTotal.value = millis.coerceAtLeast(0)
        _timerRemaining.value = millis.coerceAtLeast(0)
        _timerFinished.value = false
    }

    fun startTimer() {
        if (_timerRunning.value) return
        val remaining = _timerRemaining.value
        if (remaining <= 0) return
        timerEndAt = SystemClock.elapsedRealtime() + remaining
        _timerRunning.value = true
        _timerFinished.value = false
        timerJob = viewModelScope.launch {
            while (isActive) {
                val left = timerEndAt - SystemClock.elapsedRealtime()
                if (left <= 0) {
                    _timerRemaining.value = 0L
                    _timerRunning.value = false
                    _timerFinished.value = true
                    showClockNotification(appContext, 2000, "计时器", "计时结束")
                    break
                }
                _timerRemaining.value = left
                delay(100)
            }
        }
    }

    fun pauseTimer() {
        if (!_timerRunning.value) return
        _timerRemaining.value = (timerEndAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        _timerRunning.value = false
        timerJob?.cancel()
        timerJob = null
    }

    fun resetTimer() {
        timerJob?.cancel()
        timerJob = null
        _timerRunning.value = false
        _timerFinished.value = false
        _timerRemaining.value = _timerTotal.value
    }

    override fun onCleared() {
        stopwatchJob?.cancel()
        timerJob?.cancel()
        super.onCleared()
    }
}
