package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.util.TimeUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class DoctorTimeHubUiState(
    val isOpenToday: Boolean = false,
    /** Calendar says clinic accepts patients today (ignores clock). */
    val scheduleOpenToday: Boolean = false,
    /** "HH:mm - HH:mm" when open with known hours, null when closed or hours unset. */
    val todayHours: String? = null,
    val appointmentsToday: Int = 0,
    /** "HH:mm" of the next appointment still ahead today, null if none left. */
    val nextQueueTime: String? = null,
    val isLoading: Boolean = false
)

/**
 * Summary strip on the Time & Appointment Management hub.
 *
 * Open/closed resolution order: a `doctor_special_dates` row for today wins outright (that is the
 * whole point of a special date), otherwise the recurring `doctor_availability` row for today's
 * day_of_week (0=Sunday..6=Saturday) decides.
 */
class DoctorTimeHubViewModel : ViewModel() {
    private val doctorRepo = DoctorRepository()

    private val _uiState = MutableStateFlow(DoctorTimeHubUiState())
    val uiState: StateFlow<DoctorTimeHubUiState> = _uiState

    private val clinicZone = ZoneId.of("Asia/Bangkok")

    fun load() {
        val doctorId = supabase.auth.currentUserOrNull()?.id ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val today = LocalDate.now(clinicZone)
            val now = LocalTime.now(clinicZone)

            val special = runCatching {
                supabase.from("doctor_special_dates").select {
                    filter {
                        eq("doctor_id", doctorId)
                        eq("special_date", today.toString())
                    }
                }.decodeList<DoctorSpecialDate>().firstOrNull()
            }.getOrNull()

            val weekly = doctorRepo.getAvailability(doctorId).getOrDefault(emptyList())
                .firstOrNull { it.dayOfWeek == today.dayOfWeek.value % 7 }  // java Mon=1..Sun=7 -> DB Sun=0..Sat=6

            val scheduleOpen: Boolean
            val start: String?
            val end: String?
            if (special != null) {
                scheduleOpen = !special.isClosed
                start = special.startHhMm()
                end = special.endHhMm()
            } else {
                scheduleOpen = weekly != null && !weekly.isDayOff
                start = weekly?.startTime?.take(5)
                end = weekly?.endTime?.take(5)
            }

            val isOpenToday = scheduleOpen && isWithinClinicHours(now, start, end)

            val appointments = doctorRepo.getAppointmentsForDoctor(doctorId).getOrDefault(emptyList())
            val todayAppointments = appointments.filter {
                TimeUtils.localDate(it.scheduledAt) == today.toString()
            }
            val nextQueue = todayAppointments
                .mapNotNull { TimeUtils.localTime(it.scheduledAt) }
                .filter { runCatching { LocalTime.parse(it) }.getOrNull()?.isAfter(now) == true }
                .minOrNull()

            _uiState.value = DoctorTimeHubUiState(
                isOpenToday = isOpenToday,
                scheduleOpenToday = scheduleOpen,
                todayHours = if (scheduleOpen && start != null && end != null) "$start - $end" else null,
                appointmentsToday = todayAppointments.size,
                nextQueueTime = nextQueue,
                isLoading = false
            )
        }
    }

    /** True when [now] falls in [start, end) on a day that is scheduled open; missing hours → no clock gate. */
    private fun isWithinClinicHours(now: LocalTime, start: String?, end: String?): Boolean {
        if (start.isNullOrBlank() || end.isNullOrBlank()) return true
        val openAt = runCatching { LocalTime.parse(start) }.getOrNull() ?: return true
        val closeAt = runCatching { LocalTime.parse(end) }.getOrNull() ?: return true
        return !now.isBefore(openAt) && now.isBefore(closeAt)
    }
}
