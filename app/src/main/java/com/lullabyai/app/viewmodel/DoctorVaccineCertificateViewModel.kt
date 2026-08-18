package com.lullabyai.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.doctor.DoctorVaccineBookSync
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.ui.screens.doctor.clinicalAgeGender
import com.lullabyai.app.ui.screens.doctor.clinicalHn
import com.lullabyai.app.util.VaccineCertificatePdf
import io.github.jan.supabase.auth.auth
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Step 1 — Stitch 46 hub / preview: PDF generated, doctor reviews before send. */
data class VaccineCertificatePreviewData(
    val childId: String,
    val childName: String,
    val hn: String,
    val ageLine: String?,
    val vaccineName: String,
    val vaccineRecordId: String? = null,
    val doseLabel: String,
    val dateGiven: String,
    val lotNumber: String?,
    val injectionSite: String?,
    val reactionNotes: String?,
    val providerName: String,
    val recipientName: String,
    val pdfFile: File,
    val isThai: Boolean
)

/** Step 2 — Stitch 46 sent / complete screen. */
data class VaccineCertificateSentData(
    val childName: String,
    val vaccineName: String,
    val doseLabel: String?,
    val dateGiven: String,
    val providerName: String,
    val certificatePath: String?,
    val approved: Boolean = true
)

class DoctorVaccineCertificateViewModel : ViewModel() {
    private val healthRepo = HealthRepository()
    private val doctorRepo = DoctorRepository()
    private val childRepo = ChildRepository()

    private val _preview = MutableStateFlow<VaccineCertificatePreviewData?>(null)
    val preview: StateFlow<VaccineCertificatePreviewData?> = _preview

    private val _lastSent = MutableStateFlow<VaccineCertificateSentData?>(null)
    val lastSent: StateFlow<VaccineCertificateSentData?> = _lastSent

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    /** Generate PDF only — caller navigates to preview on [onReady]. */
    fun preparePreview(
        context: Context,
        childId: String,
        vaccineName: String,
        lotNumber: String,
        injectionSite: String,
        reactionNotes: String,
        isThai: Boolean,
        vaccineRecordId: String? = null,
        onReady: () -> Unit = {}
    ) {
        val doctorId = supabase.auth.currentUserOrNull()?.id
        val name = vaccineName.trim()
        if (doctorId == null) {
            _error.value = if (isThai) "ไม่พบเซสชันแพทย์" else "Doctor session missing"
            return
        }
        if (name.isBlank()) {
            _error.value = if (isThai) "กรุณาระบุชื่อวัคซีน" else "Please enter the vaccine name"
            return
        }
        viewModelScope.launch {
            _isWorking.value = true
            _error.value = null
            runCatching {
                val child = childRepo.getChildById(childId).getOrNull() ?: error("child_missing")
                val provider = doctorRepo.getProfileFullName(doctorId).getOrNull() ?: "-"
                val recipient = child.userId.takeIf { it.isNotBlank() }
                    ?.let { doctorRepo.getProfileFullName(it).getOrNull() }
                    ?.takeIf { it.isNotBlank() }
                    ?: if (isThai) "ผู้ปกครอง" else "Guardian"
                val all = healthRepo.getVaccineRecords(childId).getOrDefault(emptyList())
                val matching = all.filter { it.vaccineName.trim().equals(name, ignoreCase = true) }
                val doseLabel = DoctorVaccineBookSync.previewDoseLabel(matching, isThai)
                val dateGiven = java.time.LocalDate.now().toString()
                val hn = clinicalHn(child.id)
                val pdf = VaccineCertificatePdf.generate(
                    context,
                    VaccineCertificatePdf.CertificateData(
                        childName = child.name,
                        hn = hn,
                        vaccineName = name,
                        doseLabel = doseLabel,
                        dateGiven = dateGiven,
                        lotNumber = lotNumber.trim().ifBlank { null },
                        injectionSite = injectionSite.trim().ifBlank { null },
                        providerName = provider,
                        clinicName = doctorRepo.getDoctorProfile(doctorId).getOrNull()?.clinicName
                    ),
                    isThai
                )
                VaccineCertificatePreviewData(
                    childId = childId,
                    childName = child.name,
                    hn = hn,
                    ageLine = clinicalAgeGender(child.birthdate, child.gender, isThai),
                    vaccineName = name,
                    vaccineRecordId = vaccineRecordId,
                    doseLabel = doseLabel,
                    dateGiven = dateGiven,
                    lotNumber = lotNumber.trim().ifBlank { null },
                    injectionSite = injectionSite.trim().ifBlank { null },
                    reactionNotes = reactionNotes.trim().ifBlank { null },
                    providerName = provider,
                    recipientName = recipient,
                    pdfFile = pdf,
                    isThai = isThai
                )
            }.onSuccess {
                _preview.value = it
                _isWorking.value = false
                onReady()
            }.onFailure {
                _isWorking.value = false
                _error.value = it.message ?: if (isThai) "สร้างตัวอย่างใบรับรองไม่สำเร็จ" else "Preview failed"
            }
        }
    }

    /** Step 2 — upsert book, upload PDF, notify parent, publish sent data. */
    fun approveAndSend(context: Context, onSuccess: () -> Unit) {
        val draft = _preview.value ?: return
        val doctorId = supabase.auth.currentUserOrNull()?.id
        if (doctorId == null) {
            _error.value = if (draft.isThai) "ไม่พบเซสชันแพทย์" else "Doctor session missing"
            return
        }
        viewModelScope.launch {
            _isWorking.value = true
            _error.value = null
            runCatching {
                val record = DoctorVaccineBookSync.upsertDose(
                    healthRepo,
                    draft.childId,
                    draft.vaccineName,
                    draft.lotNumber.orEmpty(),
                    draft.injectionSite.orEmpty(),
                    draft.reactionNotes.orEmpty(),
                    draft.isThai,
                    includeDoctorVerifiedNote = true,
                    targetVaccineRecordId = draft.vaccineRecordId
                )
                val doseLabel = DoctorVaccineBookSync.doseLabel(record, draft.isThai) ?: draft.doseLabel
                val dateGiven = record.dateGiven ?: draft.dateGiven
                val pdf = VaccineCertificatePdf.generate(
                    context,
                    VaccineCertificatePdf.CertificateData(
                        childName = draft.childName,
                        hn = draft.hn,
                        vaccineName = draft.vaccineName,
                        doseLabel = doseLabel,
                        dateGiven = dateGiven,
                        lotNumber = draft.lotNumber,
                        injectionSite = draft.injectionSite,
                        providerName = draft.providerName,
                        clinicName = doctorRepo.getDoctorProfile(doctorId).getOrNull()?.clinicName
                    ),
                    draft.isThai
                )
                val certPath = healthRepo.uploadVaccineCertificate(
                    draft.childId, record.id, pdf.readBytes(), "pdf"
                ).getOrThrow()
                healthRepo.registerVaccineCertificateInVault(
                    childId = draft.childId,
                    filePath = certPath,
                    vaccineName = draft.vaccineName,
                    fileSizeBytes = pdf.length().toInt()
                )
                healthRepo.updateVaccineRecord(
                    record.copy(certificateUrl = certPath, source = "doctor_verified")
                ).getOrThrow()
                val message = if (draft.isThai) {
                    "แพทย์ส่งใบรับรองวัคซีน ${draft.vaccineName}"
                } else {
                    "Doctor sent vaccine certificate for ${draft.vaccineName}"
                }
                doctorRepo.sendVaccineVerificationToParent(
                    doctorId, draft.childId, record.id, message
                ).getOrThrow()
                VaccineCertificateSentData(
                    childName = draft.childName,
                    vaccineName = draft.vaccineName,
                    doseLabel = doseLabel,
                    dateGiven = dateGiven,
                    providerName = draft.providerName,
                    certificatePath = certPath,
                    approved = true
                )
            }.onSuccess { sent ->
                _lastSent.value = sent
                _preview.value = null
                _isWorking.value = false
                onSuccess()
            }.onFailure {
                _isWorking.value = false
                val raw = it.message.orEmpty()
                _error.value = when {
                    raw.contains("row-level security", ignoreCase = true) ->
                        if (draft.isThai) "ส่งไม่สำเร็จ — สิทธิ์เข้าถึงเอกสาร กรุณาลองใหม่หรือติดต่อผู้ดูแลระบบ"
                        else "Send failed — document permission error. Retry or contact support."
                    raw.contains("access_denied", ignoreCase = true) ->
                        if (draft.isThai) "ไม่มีสิทธิ์ส่งใบรับรองให้คนไข้รายนี้" else "No permission to send a certificate for this patient."
                    else -> raw.ifBlank {
                        if (draft.isThai) "ส่งใบรับรองไม่สำเร็จ" else "Certificate send failed"
                    }
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clear() {
        _preview.value = null
        _lastSent.value = null
        _error.value = null
    }
}
