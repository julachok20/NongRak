package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.DailyOralRoutine
import com.lullabyai.app.data.model.OralHealthEvent
import com.lullabyai.app.data.model.ToothRecord
import com.lullabyai.app.data.remote.ClaudeClient
import com.lullabyai.app.data.repository.OralHealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class OralHealthUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val child: Child? = null,
    val teeth: Map<String, ToothRecord> = emptyMap(),
    val events: List<OralHealthEvent> = emptyList(),
    val todayRoutine: DailyOralRoutine = DailyOralRoutine(),
    val consistency: Pair<Int, Int> = Pair(0, 7), // (daysWith2PlusBrushes, 7)
    val aiAdvice: String? = null,
    val aiAdviceLoading: Boolean = false,
    val activeToothIdForModal: String? = null,
    val error: String? = null
)

class OralHealthViewModel(
    private val repo: OralHealthRepository = OralHealthRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(OralHealthUiState())
    val uiState: StateFlow<OralHealthUiState> = _uiState.asStateFlow()

    fun loadData(child: Child?) {
        if (child == null) return
        _uiState.update { it.copy(child = child, isLoading = true, error = null) }
        viewModelScope.launch {
            val teethResult = repo.getTeethRecords(child.id)
            val eventsResult = repo.getOralHealthEvents(child.id)
            val routineResult = repo.getDailyRoutine(child.id)
            val consistencyResult = repo.get7DayConsistency(child.id)

            val teethMap = teethResult.getOrDefault(emptyList()).associateBy { it.toothId }
            val events = eventsResult.getOrDefault(emptyList())
            val routine = routineResult.getOrDefault(null) ?: DailyOralRoutine(childId = child.id, routineDate = LocalDate.now().toString())
            val consistency = consistencyResult.getOrDefault(Pair(0, 7))

            _uiState.update {
                it.copy(
                    isLoading = false,
                    teeth = teethMap,
                    events = events,
                    todayRoutine = routine,
                    consistency = consistency
                )
            }

            loadAiAdvice(child, teethMap.values.toList(), routine)
        }
    }

    private fun loadAiAdvice(child: Child, teeth: List<ToothRecord>, routine: DailyOralRoutine) {
        viewModelScope.launch {
            _uiState.update { it.copy(aiAdviceLoading = true) }
            val emergingCount = teeth.count { it.status == "emerging" }
            val attentionCount = teeth.count { it.status == "needs_care" }
            val healthyCount = teeth.count { it.status == "healthy" }

            val prompt = buildString {
                append("เด็กชื่อ ${child.name} อายุ ${child.birthdate} ")
                append("สถานะฟัน: ปกติ $healthyCount ซี่, กำลังขึ้น $emergingCount ซี่, ต้องดูแล $attentionCount ซี่ ")
                append("กิจวัตรวันนี้: เช้า=${routine.morning}, กลางวัน=${routine.lunch}, ก่อนนอน=${routine.bedtime}, ไหมขัดฟัน=${routine.flossing} ")
                append("ให้คำแนะนำทางการดูแลสุขภาพช่องปากและฟันสำหรับเด็กวัยนี้สั้นๆ 2-3 ประโยค อบอุ่น ตรงประเด็น ไม่มีคำทักทายหรือ emoji")
            }

            val fallback = if (emergingCount > 0) {
                "ฟันของลูกกำลังเริ่มขึ้น แนะนำให้ใช้ผ้าสะอาดชุบน้ำต้มสุกเช็ดเหงือกเบาๆ และใช้ยาสีฟันผสมฟลูออไรด์ 1000 ppm ปริมาณเท่าเมล็ดข้าวสาร แปรงฟันอย่างน้อยวันละ 2 ครั้งเพื่อสุขอนามัยที่ดีค่ะ"
            } else {
                "ควรช่วยแปรงฟันให้ลูกอย่างน้อยวันละ 2 ครั้ง เช้าและก่อนนอนด้วยยาสีฟันฟลูออไรด์ 1000 ppm และเริ่มใช้ไหมขัดฟันเมื่อฟันเริ่มขึ้นชิดกัน พร้อมพบทันตแพทย์เด็กเป็นประจำทุก 6 เดือนนะคะ"
            }

            val advice = runCatching {
                ClaudeClient.generateContent(
                    prompt = prompt,
                    useSearch = false,
                    maxTokens = 512,
                    system = "คุณคือ \"น้องรัก\" ผู้เชี่ยวชาญด้านสุขภาพช่องปากและฟันเด็ก ให้คำแนะนำสั้นๆ กระชับ อบอุ่น ถูกต้องตามมาตรฐานทันตกรรมเด็ก",
                    jsonMode = false
                )
            }.getOrDefault(fallback)

            _uiState.update { it.copy(aiAdvice = advice, aiAdviceLoading = false) }
        }
    }

    fun openToothModal(toothId: String) {
        _uiState.update { it.copy(activeToothIdForModal = toothId) }
    }

    fun dismissToothModal() {
        _uiState.update { it.copy(activeToothIdForModal = null) }
    }

    fun updateToothStatus(toothId: String, status: String) {
        val childId = _uiState.value.child?.id ?: return
        viewModelScope.launch {
            val prev = _uiState.value.teeth[toothId]
            val updated = (prev ?: ToothRecord(childId = childId, toothId = toothId)).copy(
                status = status,
                updatedAt = java.time.Instant.now().toString()
            )
            _uiState.update {
                it.copy(
                    teeth = it.teeth + (toothId to updated),
                    activeToothIdForModal = null
                )
            }
            repo.saveToothRecord(childId, toothId, status)
        }
    }

    fun toggleRoutineMorning(checked: Boolean) {
        val routine = _uiState.value.todayRoutine.copy(morning = checked)
        saveRoutine(routine)
    }

    fun toggleRoutineLunch(checked: Boolean) {
        val routine = _uiState.value.todayRoutine.copy(lunch = checked)
        saveRoutine(routine)
    }

    fun toggleRoutineBedtime(checked: Boolean) {
        val routine = _uiState.value.todayRoutine.copy(bedtime = checked)
        saveRoutine(routine)
    }

    fun toggleRoutineFlossing(checked: Boolean) {
        val routine = _uiState.value.todayRoutine.copy(flossing = checked)
        saveRoutine(routine)
    }

    private fun saveRoutine(routine: DailyOralRoutine) {
        val childId = _uiState.value.child?.id ?: return
        _uiState.update { it.copy(todayRoutine = routine) }
        viewModelScope.launch {
            repo.saveDailyRoutine(
                childId = childId,
                date = routine.routineDate.ifBlank { LocalDate.now().toString() },
                morning = routine.morning,
                lunch = routine.lunch,
                bedtime = routine.bedtime,
                flossing = routine.flossing
            )
            val consistencyResult = repo.get7DayConsistency(childId)
            consistencyResult.onSuccess { pair ->
                _uiState.update { it.copy(consistency = pair) }
            }
        }
    }

    fun saveEvent(
        category: String,
        eventName: String,
        eventDate: String,
        notes: String?,
        photoUrls: List<String>,
        onDone: () -> Unit
    ) {
        val childId = _uiState.value.child?.id ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            repo.addOralHealthEvent(
                childId = childId,
                category = category,
                eventName = eventName,
                eventDate = eventDate,
                notes = notes,
                photoUrls = photoUrls
            ).onSuccess { event ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        events = listOf(event) + it.events
                    )
                }
                onDone()
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            repo.deleteOralHealthEvent(eventId).onSuccess {
                _uiState.update { it.copy(events = it.events.filterNot { e -> e.id == eventId }) }
            }
        }
    }
}
