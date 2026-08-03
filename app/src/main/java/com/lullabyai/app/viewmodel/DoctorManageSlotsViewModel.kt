package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.DoctorAppointmentSlot
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ClinicDaySchedule
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.data.repository.Quadruple
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class SlotRepeatMode { ONCE, DAILY, WEEKLY }

data class DoctorManageSlotsUiState(
    val slots: List<DoctorAppointmentSlot> = emptyList(),
    val isLoading: Boolean = false,
    val daySchedule: ClinicDaySchedule? = null,
    val openDateOptions: List<LocalDate> = emptyList(),
    val statusMessage: String? = null
)

/** Doctor's bookable time-slot manager — list/create/open-close, backed by
 *  doctor_appointment_slots via DoctorRepository. */
class DoctorManageSlotsViewModel : ViewModel() {
    private val doctorRepo = DoctorRepository()

    private val _uiState = MutableStateFlow(DoctorManageSlotsUiState())
    val uiState: StateFlow<DoctorManageSlotsUiState> = _uiState

    private var lastFromDate: String? = null

    private fun doctorId() = supabase.auth.currentUserOrNull()?.id

    fun load(fromDate: String? = LocalDate.now().toString()) {
        val doctorId = doctorId() ?: return
        lastFromDate = fromDate
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null)
            val anchor = runCatching { LocalDate.parse(fromDate) }.getOrDefault(LocalDate.now())
            val schedule = doctorRepo.resolveClinicScheduleForDate(doctorId, anchor).getOrNull()
            val openDates = doctorRepo.upcomingOpenClinicDates(doctorId, LocalDate.now(), 60).getOrDefault(emptyList())
            val slots = doctorRepo.getSlots(doctorId, fromDate).getOrDefault(emptyList())
            _uiState.value = DoctorManageSlotsUiState(
                slots = slots,
                isLoading = false,
                daySchedule = schedule,
                openDateOptions = openDates
            )
        }
    }

    private fun reload() = load(lastFromDate)

    fun addManualSlot(slotDate: String, startTime: String, intervalMinutes: Int) {
        val start = runCatching { java.time.LocalTime.parse(startTime.trim().take(5)) }.getOrNull() ?: return
        val end = start.plusMinutes(intervalMinutes.coerceAtLeast(1).toLong())
        val hhmm = "%02d:%02d".format(start.hour, start.minute)
        val endStr = "%02d:%02d".format(end.hour, end.minute)
        val doctorId = doctorId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            doctorRepo.createSlot(doctorId, slotDate, hhmm, endStr, 1)
            reload()
        }
    }

    fun clearDeletableSlotsForDay(slotDate: String) {
        val doctorId = doctorId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null)
            doctorRepo.deleteUnbookedSlotsForDay(doctorId, slotDate)
            reload()
        }
    }

    fun setStatus(slotId: String, status: String) {
        viewModelScope.launch {
            doctorRepo.setSlotStatus(slotId, status)
            reload()
        }
    }

    fun deleteSlot(slotId: String) {
        viewModelScope.launch {
            doctorRepo.deleteSlot(slotId)
            reload()
        }
    }

    fun autoGenerateWithRepeat(
        anchorDate: String,
        startTime: String,
        intervalMinutes: Int,
        repeat: SlotRepeatMode
    ) {
        val doctorId = doctorId() ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, statusMessage = null)
            val anchor = runCatching { LocalDate.parse(anchorDate) }.getOrNull() ?: run {
                _uiState.value = _uiState.value.copy(isLoading = false)
                return@launch
            }
            val targets = when (repeat) {
                SlotRepeatMode.ONCE -> listOf(anchor)
                SlotRepeatMode.WEEKLY -> (0 until 4).map { anchor.plusWeeks(it.toLong()) }
                SlotRepeatMode.DAILY -> doctorRepo.upcomingOpenClinicDates(doctorId, anchor, 14).getOrDefault(listOf(anchor))
            }
            val weekly = doctorRepo.getAvailability(doctorId).getOrDefault(emptyList()).associateBy { it.dayOfWeek }
            val existingByDate = doctorRepo.getSlots(doctorId, anchorDate).getOrDefault(emptyList())
                .groupBy { it.slotDate }
                .mapValues { (_, rows) -> rows.map { it.startTime.take(5) }.toSet() }
            val batch = mutableListOf<Quadruple<String, String, String, Int>>()
            targets.forEach { day ->
                val schedule = doctorRepo.resolveClinicScheduleForDate(doctorId, day).getOrNull()
                    ?: weekly[day.dayOfWeek.value % 7]?.let {
                        if (it.isDayOff) null else ClinicDaySchedule(true, it.startTime?.take(5), it.endTime?.take(5))
                    }
                if (schedule?.isOpen != true) return@forEach
                val dateStr = day.toString()
                val existing = existingByDate[dateStr].orEmpty()
                batch += buildSlotDrafts(dateStr, startTime, intervalMinutes, schedule, existing)
            }
            if (batch.isNotEmpty()) {
                doctorRepo.createSlotsBatch(doctorId, batch)
            }
            reload()
        }
    }

    private fun buildSlotDrafts(
        slotDate: String,
        startTime: String,
        intervalMinutes: Int,
        schedule: ClinicDaySchedule,
        existing: Set<String>
    ): List<Quadruple<String, String, String, Int>> {
        val start = runCatching { java.time.LocalTime.parse(startTime) }.getOrNull() ?: return emptyList()
        val close = schedule.endTime?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
        val out = mutableListOf<Quadruple<String, String, String, Int>>()
        var cursor = start
        var made = 0
        while (made < MAX_GENERATED_SLOTS) {
            val next = cursor.plusMinutes(intervalMinutes.toLong())
            if (close != null) {
                if (next > close) break
            } else if (made >= FALLBACK_SLOT_COUNT) break
            val hhmm = "%02d:%02d".format(cursor.hour, cursor.minute)
            if (hhmm !in existing) {
                out += Quadruple(slotDate, hhmm, "%02d:%02d".format(next.hour, next.minute), 1)
            }
            cursor = next
            made++
        }
        return out
    }

    private companion object {
        const val FALLBACK_SLOT_COUNT = 8
        const val MAX_GENERATED_SLOTS = 120
    }
}
