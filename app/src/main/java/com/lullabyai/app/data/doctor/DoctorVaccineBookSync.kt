package com.lullabyai.app.data.doctor

import com.lullabyai.app.data.model.VaccineRecord
import com.lullabyai.app.data.repository.HealthRepository

/** Shared upsert for doctor → parent vaccine book (clinical save + certificate approve). */
object DoctorVaccineBookSync {

    suspend fun upsertDose(
        healthRepo: HealthRepository,
        childId: String,
        vaccineName: String,
        lotNumber: String,
        injectionSite: String,
        reactionNotes: String,
        isThai: Boolean,
        includeDoctorVerifiedNote: Boolean,
        targetVaccineRecordId: String? = null
    ): VaccineRecord {
        val stamp = java.time.LocalDate.now().toString()
        val trimmedName = vaccineName.trim()
        val all = healthRepo.getVaccineRecords(childId).getOrDefault(emptyList())

        // 1. If explicit vaccine record ID is passed from booking / selection, resolve that record first
        val exactRecord = if (!targetVaccineRecordId.isNullOrBlank()) {
            all.find { it.id == targetVaccineRecordId }
        } else null

        // 2. Otherwise match by name
        val matching = all.filter { it.vaccineName.trim().equals(trimmedName, ignoreCase = true) }
        val existing = exactRecord
            ?: matching.firstOrNull { it.status != "completed" && it.status != "skipped" }
            ?: matching.firstOrNull()

        val noteParts = buildList {
            existing?.notes?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
            lotNumber.trim().takeIf { it.isNotBlank() }?.let { add("Lot: $it") }
            injectionSite.trim().takeIf { it.isNotBlank() }?.let {
                add(if (isThai) "ตำแหน่ง: $it" else "Site: $it")
            }
            reactionNotes.trim().takeIf { it.isNotBlank() }?.let { add(it) }
            if (includeDoctorVerifiedNote) {
                add(if (isThai) "ยืนยันโดยแพทย์ ($stamp)" else "Doctor verified ($stamp)")
            }
        }
        val notes = noteParts.joinToString("\n")
        return if (existing != null) {
            val updated = existing.copy(
                status = "completed",
                dateGiven = existing.dateGiven ?: stamp,
                notes = notes,
                source = "doctor_verified"
            )
            healthRepo.updateVaccineRecord(updated)
            updated
        } else {
            val nextOrder = matching.maxOfOrNull { it.ageOrder ?: 0 }?.plus(1) ?: 0
            healthRepo.addVaccineRecord(
                VaccineRecord(
                    childId = childId,
                    vaccineName = trimmedName,
                    dateGiven = stamp,
                    status = "completed",
                    notes = notes,
                    source = "doctor_verified",
                    ageOrder = nextOrder
                )
            ).getOrThrow()
        }
    }

    fun doseLabel(record: VaccineRecord, isThai: Boolean): String? =
        record.ageOrder?.let { ord -> if (isThai) "ครั้งที่ ${ord + 1}" else "Dose ${ord + 1}" }

    /** Label shown on preview before the row exists — estimates next dose from book. */
    fun previewDoseLabel(
        matching: List<VaccineRecord>,
        isThai: Boolean
    ): String {
        val open = matching.firstOrNull { it.status != "completed" && it.status != "skipped" }
        open?.ageOrder?.let { return if (isThai) "ครั้งที่ ${it + 1}" else "Dose ${it + 1}" }
        val next = matching.maxOfOrNull { it.ageOrder ?: 0 }?.plus(1) ?: 0
        return if (isThai) "ครั้งที่ ${next + 1}" else "Dose ${next + 1}"
    }
}
