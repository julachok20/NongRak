package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.doctor.DoctorDriveFeatures
import com.lullabyai.app.data.doctor.DoctorVaccineBookSync
import com.lullabyai.app.data.model.Appointment
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.DoctorVisit
import com.lullabyai.app.data.model.GrowthRecord
import com.lullabyai.app.util.VisitReasonTemplates
import com.lullabyai.app.data.remote.DoctorPatientDriveHelper
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.data.repository.DoctorVisitRepository
import com.lullabyai.app.data.repository.HealthRepository
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ClinicalFormDraft(
    val appointmentId: String = "",
    val symptoms: String = "",
    val diagnosis: String = "",
    val doctorAdvice: String = "",
    val weight: String = "",
    val temperature: String = "",
    val height: String = "",
    val followUpDate: String = "",
    val noFollowUp: Boolean = false,
    val vaccineName: String = "",
    val vaccineRecordId: String? = null,
    val vaccineLot: String = "",
    val vaccineSite: String = "",
    val vaccineReaction: String = "",
    val medicineName: String = "",
    val medicineInstructions: String = "",
    val prescriptions: List<Pair<String, String>> = emptyList(),
    val savedVisitId: String? = null
) {
    /** True when the doctor has typed anything this session (not just symptoms). */
    fun hasSessionContent(): Boolean =
        symptoms.isNotBlank() || diagnosis.isNotBlank() || doctorAdvice.isNotBlank() ||
            weight.isNotBlank() || temperature.isNotBlank() || height.isNotBlank() ||
            followUpDate.isNotBlank() || vaccineName.isNotBlank() || vaccineLot.isNotBlank() ||
            vaccineSite.isNotBlank() || vaccineReaction.isNotBlank() ||
            medicineName.isNotBlank() || medicineInstructions.isNotBlank() ||
            prescriptions.isNotEmpty()
}

data class DoctorClinicalRecordEntryUiState(
    val appointment: Appointment? = null,
    val child: Child? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    /** Non-fatal: appointment completed but Drive detail upload failed. */
    val driveWarning: String? = null,
    /** File names already uploaded to medical-docs during this write-up. */
    val attachments: List<String> = emptyList(),
    val isSendingVaccineVerification: Boolean = false,
    val vaccineVerificationSent: Boolean = false,
    val isUploading: Boolean = false,
    /** Survives back from preview / certificate flow for the same appointment. */
    val draft: ClinicalFormDraft = ClinicalFormDraft()
)

/** Doctor-side visit write-up for one appointment.
 *  Detailed clinical notes → Google Drive ([DoctorRepository.uploadPatientDetailRecord]).
 *  Appointment completion + vitals SSOT (growth_records) stay on Supabase; doctor_visits keeps a
 *  short summary for parent/doctor list UX — full narrative lives in Drive. */
class DoctorClinicalRecordEntryViewModel : ViewModel() {
    private val doctorRepo = DoctorRepository()
    private val visitRepo = DoctorVisitRepository()
    private val childRepo = ChildRepository()
    private val healthRepo = HealthRepository()

    private val _uiState = MutableStateFlow(DoctorClinicalRecordEntryUiState())
    val uiState: StateFlow<DoctorClinicalRecordEntryUiState> = _uiState

    fun load(appointmentId: String) {
        viewModelScope.launch {
            val keepDraft = _uiState.value.draft.appointmentId == appointmentId &&
                _uiState.value.draft.hasSessionContent()
            _uiState.value = _uiState.value.copy(isLoading = true)
            val appt = doctorRepo.getAppointment(appointmentId).getOrNull()
            val child = appt?.let { childRepo.getChildById(it.childId).getOrNull() }
            val existingVisit = visitRepo.getVisitByAppointment(appointmentId).getOrNull()
            val bookedVaccine = com.lullabyai.app.util.AppointmentBookingMeta.parseVaccine(appt?.reason, appt?.notes)
            val draft = when {
                keepDraft -> _uiState.value.draft
                existingVisit != null -> draftFromVisit(appointmentId, existingVisit, appt)
                else -> ClinicalFormDraft(
                    appointmentId = appointmentId,
                    vaccineName = bookedVaccine.vaccineName ?: appt?.reason?.trim().orEmpty(),
                    vaccineRecordId = bookedVaccine.vaccineRecordId
                )
            }
            _uiState.value = DoctorClinicalRecordEntryUiState(
                appointment = appt,
                child = child,
                isLoading = false,
                attachments = if (keepDraft) _uiState.value.attachments else emptyList(),
                draft = draft
            )
        }
    }

    fun patchDraft(transform: (ClinicalFormDraft) -> ClinicalFormDraft) {
        _uiState.value = _uiState.value.copy(draft = transform(_uiState.value.draft))
    }

    fun acknowledgeSaved() {
        _uiState.value = _uiState.value.copy(saved = false)
    }

    /** Stitch "อัปโหลดเอกสาร" — pushes a picked file into the child's medical-docs bucket so the
     *  scan lives with the patient record instead of only on the doctor's phone. */
    fun attachDocument(childId: String, fileName: String, mime: String?, bytes: ByteArray, isThai: Boolean) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null)
            val result = childRepo.uploadMedicalDocument(
                childId = childId,
                fileName = fileName,
                mime = mime,
                bytes = bytes,
                category = "other"
            )
            _uiState.value = if (result.isSuccess) {
                _uiState.value.copy(
                    isUploading = false,
                    attachments = _uiState.value.attachments + fileName
                )
            } else {
                _uiState.value.copy(
                    isUploading = false,
                    error = if (isThai) "อัปโหลดเอกสารไม่สำเร็จ: ${result.exceptionOrNull()?.message}"
                    else "Document upload failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun saveRecord(
        appointmentId: String,
        childId: String,
        symptoms: String,
        diagnosis: String,
        treatmentPlan: String,
        weightKg: Double?,
        heightCm: Double?,
        temperatureC: Double?,
        nextAppointmentDate: String?,
        doctorAdvice: String? = null,
        isThai: Boolean = true,
        /** When true, auto-mark the dose completed in the parent's vaccine book on save. */
        isVaccineVisit: Boolean = false,
        vaccineName: String = "",
        vaccineLot: String = "",
        vaccineSite: String = "",
        vaccineReaction: String = ""
    ) {
        val doctorId = supabase.auth.currentUserOrNull()?.id
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true, error = null, driveWarning = null)
            if (doctorId == null) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = if (isThai) "ไม่พบเซสชันแพทย์" else "Doctor session missing"
                )
                return@launch
            }

            // 1) Detailed record → Google Drive (optional; gated until OAuth verification complete)
            val driveResult = if (DoctorDriveFeatures.IS_READY) {
                doctorRepo.uploadPatientDetailRecord(
                    doctorId = doctorId,
                    childId = childId,
                    appointmentId = appointmentId,
                    childName = _uiState.value.child?.name,
                    symptoms = symptoms,
                    diagnosis = diagnosis,
                    treatmentPlan = treatmentPlan,
                    weightKg = weightKg,
                    heightCm = heightCm,
                    temperatureC = temperatureC,
                    nextAppointmentDate = nextAppointmentDate
                )
            } else {
                Result.success("")
            }
            val driveWarning = driveResult.exceptionOrNull()?.let { e ->
                if (e.message == DoctorPatientDriveHelper.NO_TOKEN || e.message?.contains("DRIVE") == true)
                    DoctorPatientDriveHelper.userMessage(isThai)
                else
                    (if (isThai) "อัปโหลดรายละเอียดไป Google Drive ไม่สำเร็จ: " else "Drive detail upload failed: ") +
                        (e.message ?: "")
            }

            // 2) doctor_visits — the row the PARENT reads (Lab Results + บันทึกการหาหมอ). Every column
            // those screens render must be filled here, or the parent silently sees less than the
            // doctor typed. Clinic + doctor identity come from the doctor's own profile rather than
            // being retyped per visit.
            val myProfile = doctorRepo.getDoctorProfile(doctorId).getOrNull()
            val myName = doctorRepo.getProfileFullName(doctorId).getOrNull()
            val existingVisitId = _uiState.value.draft.savedVisitId
                ?: visitRepo.getVisitByAppointment(appointmentId).getOrNull()?.id
            val result = visitRepo.saveVisit(
                id = existingVisitId,
                childId = childId,
                visitType = "completed",
                visitDatetime = java.time.Instant.now().toString(),
                facilityName = myProfile?.clinicName?.ifBlank { null },
                doctorName = myName?.ifBlank { null },
                doctorSpecialty = myProfile?.specialty?.ifBlank { null },
                reason = symptoms.take(200).ifBlank { null },
                // Full text, not just the 200-char `reason` excerpt — the parent's visit record showed
                // a truncated complaint because this was hardcoded null. The column is a text[]; the
                // doctor types one free-text narrative, so it goes in as a single element rather than
                // being split on punctuation and mangling clinical phrasing.
                symptoms = symptoms.ifBlank { null }?.let { listOf(it) },
                diagnosis = diagnosis.ifBlank { null },
                treatmentPlan = treatmentPlan.take(500).ifBlank { null },
                doctorAdvice = doctorAdvice?.take(2000)?.ifBlank { null },
                weightKg = weightKg,
                heightCm = heightCm,
                temperatureC = temperatureC,
                bloodPressure = null,
                nextAppointmentDate = nextAppointmentDate,
                preparationNotes = null,
                reminders = null,
                status = if (nextAppointmentDate != null) "follow_up" else "completed",
                doctorId = doctorId,
                appointmentId = appointmentId
            )
            if (result.isSuccess) {
                val savedVisit = result.getOrNull()
                if (weightKg != null || heightCm != null) {
                    if (existingVisitId == null) {
                        healthRepo.addGrowthRecord(
                            GrowthRecord(
                                childId = childId,
                                weightKg = weightKg,
                                heightCm = heightCm,
                                recordedAt = java.time.LocalDate.now().toString(),
                                recordType = "doctor_visit"
                            )
                        )
                    }
                }
                if (isVaccineVisit && vaccineName.trim().isNotBlank()) {
                    DoctorVaccineBookSync.upsertDose(
                        healthRepo,
                        childId = childId,
                        vaccineName = vaccineName,
                        lotNumber = vaccineLot,
                        injectionSite = vaccineSite,
                        reactionNotes = vaccineReaction,
                        isThai = isThai,
                        includeDoctorVerifiedNote = false,
                        targetVaccineRecordId = _uiState.value.draft.vaccineRecordId
                    )
                }
                doctorRepo.updateAppointmentStatus(appointmentId, "completed")
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saved = true,
                    driveWarning = driveWarning,
                    draft = _uiState.value.draft.copy(savedVisitId = savedVisit?.id)
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = result.exceptionOrNull()?.message ?: driveWarning
                )
            }
        }
    }
}

private fun draftFromVisit(
    appointmentId: String,
    visit: DoctorVisit,
    appt: Appointment?
): ClinicalFormDraft {
    val parsedVaccine = VisitReasonTemplates.parseVaccineAdvice(visit.doctorAdvice)
    val bookedVaccine = com.lullabyai.app.util.AppointmentBookingMeta.parseVaccine(appt?.reason, appt?.notes)
    return ClinicalFormDraft(
        appointmentId = appointmentId,
        symptoms = visit.symptoms?.joinToString("\n")?.ifBlank { null } ?: visit.reason.orEmpty(),
        diagnosis = visit.diagnosis.orEmpty(),
        doctorAdvice = parsedVaccine?.advice ?: visit.doctorAdvice.orEmpty(),
        weight = visit.weightKg?.toString().orEmpty(),
        temperature = visit.temperatureC?.toString().orEmpty(),
        height = visit.heightCm?.toString().orEmpty(),
        followUpDate = visit.nextAppointmentDate.orEmpty(),
        noFollowUp = visit.nextAppointmentDate.isNullOrBlank(),
        vaccineName = parsedVaccine?.vaccineName ?: bookedVaccine.vaccineName ?: appt?.reason?.trim().orEmpty(),
        vaccineRecordId = bookedVaccine.vaccineRecordId,
        vaccineLot = parsedVaccine?.lot.orEmpty(),
        vaccineSite = parsedVaccine?.site.orEmpty(),
        vaccineReaction = parsedVaccine?.reaction.orEmpty(),
        prescriptions = parsePrescriptionsFromPlan(visit.treatmentPlan),
        savedVisitId = visit.id.takeIf { it.isNotBlank() }
    )
}

private fun parsePrescriptionsFromPlan(plan: String?): List<Pair<String, String>> {
    if (plan.isNullOrBlank()) return emptyList()
    return plan.lineSequence().mapNotNull { line ->
        val trimmed = line.trim().removePrefix("•").trim()
        if (trimmed.isBlank()) return@mapNotNull null
        val dash = trimmed.indexOf(" — ")
        if (dash > 0) trimmed.substring(0, dash).trim() to trimmed.substring(dash + 3).trim()
        else trimmed to ""
    }.toList()
}
