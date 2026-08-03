package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.Child

/** Resolve a patient row for the doctor UI when [ChildRepository] is blocked by RLS. */
suspend fun resolveChildForDoctorView(childId: String, appointmentNotes: String? = null): Child? {
    val childRepo = ChildRepository()
    childRepo.getChildById(childId).getOrNull()?.let { return it }
    val intake = PediaConnectRepository().patientRecordForChild(childId).getOrNull()
    if (intake != null) {
        return Child(
            id = childId,
            name = intake.name,
            birthdate = intake.birthdate ?: "",
            gender = intake.gender ?: "unspecified",
            allergies = intake.allergies,
            bloodGroup = intake.bloodGroup
        )
    }
    val fromNotes = intakeNameFromNotes(appointmentNotes)
    if (fromNotes != null) {
        return Child(id = childId, name = fromNotes, birthdate = "", gender = "unspecified")
    }
    return null
}

/** Name embedded in booking/intake notes when profile tables are not yet readable. */
fun intakeNameFromNotes(notes: String?): String? {
    if (notes.isNullOrBlank()) return null
    return notes.lineSequence()
        .firstOrNull { it.startsWith("Patient:") }
        ?.substringAfter("Patient:")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}
