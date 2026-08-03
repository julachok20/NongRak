package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.repository.SleepRepository
import com.lullabyai.app.data.sleep.SleepSoundEvent
import com.lullabyai.app.util.CrySoundDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SleepMonitorUiState(
    val events: List<SleepSoundEvent> = emptyList(),
    val monitoring: Boolean = false,
    val liveDb: Float = 0f,
    val liveLabel: String = "quiet",
    val todayCryCount: Int = 0,
    val todayLoudCount: Int = 0,
    val todaySnoreCount: Int = 0,
    val lastError: String? = null
)

/**
 * Owns mic-based cry/sound monitoring + event history for the Pro Sleep Tracker.
 * Kept separate from [ChildViewModel] so parallel agents don't contend on that file.
 */
class SleepMonitorViewModel(
    private val sleepRepo: SleepRepository = SleepRepository()
) : ViewModel() {

    private val detector = CrySoundDetector()
    private val _ui = MutableStateFlow(SleepMonitorUiState())
    val uiState: StateFlow<SleepMonitorUiState> = _ui.asStateFlow()

    private var childId: String? = null
    private var activeSleepLogId: String? = null

    init {
        viewModelScope.launch {
            detector.level.collect { lv ->
                _ui.update {
                    it.copy(monitoring = lv.monitoring, liveDb = lv.dbApprox, liveLabel = lv.label)
                }
            }
        }
        detector.onEvent = eventHandler@{ ev ->
            val cid = childId ?: return@eventHandler
            viewModelScope.launch {
                sleepRepo.insertSoundEvent(
                    childId = cid,
                    eventType = ev.type,
                    intensity = ev.intensity,
                    peakDb = ev.peakDb,
                    durationMs = ev.durationMs,
                    sleepLogId = activeSleepLogId
                ).onSuccess { loadEvents(cid) }
            }
        }
    }

    fun bind(childId: String?, activeSleepLogId: String?) {
        this.childId = childId
        this.activeSleepLogId = activeSleepLogId
        if (childId != null) loadEvents(childId)
    }

    fun loadEvents(childId: String) {
        viewModelScope.launch {
            sleepRepo.getSoundEvents(childId).onSuccess { list ->
                val todayPrefix = java.time.LocalDate.now().toString()
                // detected_at is UTC ISO — count by local calendar day via TimeUtils when available.
                val today = list.filter {
                    com.lullabyai.app.util.TimeUtils.parseInstant(it.detectedAt)
                        ?.atZone(java.time.ZoneId.systemDefault())
                        ?.toLocalDate()
                        ?.toString() == todayPrefix
                }
                _ui.update {
                    it.copy(
                        events = list,
                        todayCryCount = today.count { e -> e.eventType == "cry" },
                        todayLoudCount = today.count { e -> e.eventType == "loud" },
                        todaySnoreCount = today.count { e -> e.eventType == "snore_like" }
                    )
                }
            }
        }
    }

    fun startMonitoring(): Boolean {
        val ok = detector.start()
        if (!ok) _ui.update { it.copy(lastError = "mic_unavailable") }
        return ok
    }

    fun stopMonitoring() = detector.stop()

    fun setQualityStars(logId: String, stars: Int, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            sleepRepo.updateQualityStars(logId, stars)
            onDone()
        }
    }

    override fun onCleared() {
        detector.release()
        super.onCleared()
    }
}
