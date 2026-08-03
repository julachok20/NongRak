package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Appointment
import com.lullabyai.app.data.model.ClinicBookingGate
import com.lullabyai.app.data.model.DoctorAppointmentSlot
import com.lullabyai.app.data.model.DoctorProfile
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.remote.StorageHelper
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.data.repository.DoctorRepository.Companion.isFutureSlot
import com.lullabyai.app.data.repository.PediaConnectLinkRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DoctorReservationUiState(
    val loading: Boolean = false,
    val profile: Profile? = null,
    val doctorProfile: DoctorProfile? = null,
    val photoUrl: String? = null,
    val slots: List<DoctorAppointmentSlot> = emptyList(),
    val booking: Boolean = false,
    val bookedAppointment: Appointment? = null,
    val linkGate: ClinicBookingGate? = null,
    val error: String? = null
)

/** Backs DoctorReservationScreen: doctor profile + bookable slots; submits a **pending** appointment per booking. */
class DoctorReservationViewModel : ViewModel() {
    private val repo = DoctorRepository()
    private val linkRepo = PediaConnectLinkRepository()
    private val _ui = MutableStateFlow(DoctorReservationUiState())
    val ui: StateFlow<DoctorReservationUiState> = _ui

    private var loadedDoctorId: String? = null

    fun load(doctorId: String) {
        val profileStale = loadedDoctorId != doctorId
        loadedDoctorId = doctorId
        viewModelScope.launch {
            if (profileStale) {
                _ui.value = _ui.value.copy(loading = true, error = null)
                val doctorProfileResult = repo.getDoctorProfile(doctorId)
                val parentProfile = repo.searchDoctors(null).getOrDefault(emptyList())
                    .firstOrNull { it.first.id == doctorId }?.first
                val doctorProfile = doctorProfileResult.getOrNull()
                val photo = StorageHelper.signedAvatarUrl(doctorProfile?.clinicPhotoUrl)
                _ui.value = _ui.value.copy(
                    profile = parentProfile,
                    doctorProfile = doctorProfile,
                    photoUrl = photo,
                    error = if (doctorProfileResult.isFailure) doctorProfileResult.exceptionOrNull()?.message else null
                )
            }
            val openSlots = repo.getBookableSlotsForParent(doctorId).getOrDefault(emptyList())
            _ui.value = _ui.value.copy(loading = false, slots = openSlots)
        }
    }

    /** Re-fetch open slots (e.g. after doctor updated availability). */
    fun refreshSlots(doctorId: String) {
        viewModelScope.launch {
            val openSlots = repo.getBookableSlotsForParent(doctorId).getOrDefault(emptyList())
            _ui.value = _ui.value.copy(slots = openSlots)
        }
    }

    fun refreshLinkGate(doctorId: String, childId: String?) {
        viewModelScope.launch {
            val gate = linkRepo.bookingGate(doctorId, childId)
            _ui.value = _ui.value.copy(linkGate = gate, error = null)
        }
    }

    fun book(
        doctorId: String,
        childId: String,
        slotId: String?,
        reason: String?,
        isEmergency: Boolean,
        isThai: Boolean
    ) {
        if (_ui.value.booking) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(booking = true, error = null)
            val gate = linkRepo.bookingGate(doctorId, childId)
            _ui.value = _ui.value.copy(linkGate = gate)
            val gateMessage = bookingGateMessage(gate, isThai)
            if (gateMessage != null) {
                _ui.value = _ui.value.copy(booking = false, error = gateMessage)
                return@launch
            }
            if (!isEmergency) {
                val slot = slotId?.let { id -> _ui.value.slots.find { it.id == id } }
                if (slotId != null && (slot == null || !isFutureSlot(slot))) {
                    _ui.value = _ui.value.copy(
                        booking = false,
                        error = if (isThai) "ไม่สามารถจองคิวที่ผ่านมาแล้วได้" else "Cannot book a past time slot."
                    )
                    return@launch
                }
            }
            repo.requestAppointment(doctorId, childId, if (isEmergency) null else slotId, reason, isEmergency)
                .onSuccess { _ui.value = _ui.value.copy(booking = false, bookedAppointment = it) }
                .onFailure { _ui.value = _ui.value.copy(booking = false, error = it.message) }
        }
    }

    private fun bookingGateMessage(gate: ClinicBookingGate, isThai: Boolean): String? = when (gate) {
        ClinicBookingGate.CAN_BOOK -> null
        ClinicBookingGate.NEED_REGISTRATION -> if (isThai) {
            "กรุณาลงทะเบียนลูกใน Pedia Connect ก่อนจองคิว"
        } else {
            "Register your child on Pedia Connect before booking."
        }
    }
}
