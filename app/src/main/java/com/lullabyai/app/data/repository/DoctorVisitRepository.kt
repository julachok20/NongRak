package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.DoctorVisit
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/** "บันทึกการหาหมอ" (Doctor Appointment Record) — same conventions as HealthRepository: explicit
 *  JSON payloads for insert/update (DoctorVisit.id defaults to "" against a NOT NULL uuid column,
 *  so inserting the raw data class would send an invalid empty-string id). */
class DoctorVisitRepository {

    suspend fun getVisits(childId: String): Result<List<DoctorVisit>> = runCatching {
        supabase.from("doctor_visits").select {
            filter { eq("child_id", childId) }
            order("visit_datetime", Order.DESCENDING)
        }.decodeList<DoctorVisit>()
    }

    suspend fun getVisitByAppointment(appointmentId: String): Result<DoctorVisit?> = runCatching {
        supabase.from("doctor_visits").select {
            filter { eq("appointment_id", appointmentId) }
            limit(1)
        }.decodeList<DoctorVisit>().firstOrNull()
    }

    /** Insert (id == null) a scheduled appointment or a completed visit, or update (id != null)
     *  an existing row — one function for both since RecordVisitScreen edits in place. */
    suspend fun saveVisit(
        id: String?,
        childId: String,
        visitType: String,
        visitDatetime: String,
        facilityName: String?,
        doctorName: String?,
        doctorSpecialty: String?,
        reason: String?,
        symptoms: List<String>?,
        diagnosis: String?,
        treatmentPlan: String?,
        doctorAdvice: String?,
        weightKg: Double?,
        heightCm: Double?,
        temperatureC: Double?,
        bloodPressure: String?,
        nextAppointmentDate: String?,
        preparationNotes: String?,
        reminders: List<String>?,
        status: String,
        // Set when a real Doctor-role account (not a parent's manual log) authors this visit —
        // e.g. from DoctorClinicalRecordEntryScreen. Null for parent-authored entries.
        doctorId: String? = null,
        appointmentId: String? = null
    ): Result<DoctorVisit> = runCatching {
        val payload = buildJsonObject {
            put("child_id", JsonPrimitive(childId))
            put("visit_type", JsonPrimitive(visitType))
            put("visit_datetime", JsonPrimitive(visitDatetime))
            put("facility_name", facilityName?.let { JsonPrimitive(it) } ?: JsonNull)
            put("doctor_name", doctorName?.let { JsonPrimitive(it) } ?: JsonNull)
            put("doctor_specialty", doctorSpecialty?.let { JsonPrimitive(it) } ?: JsonNull)
            put("doctor_id", doctorId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("appointment_id", appointmentId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("reason", reason?.let { JsonPrimitive(it) } ?: JsonNull)
            put("symptoms", symptoms?.let { syms -> buildJsonArray { syms.forEach { add(JsonPrimitive(it)) } } } ?: JsonNull)
            put("diagnosis", diagnosis?.let { JsonPrimitive(it) } ?: JsonNull)
            put("treatment_plan", treatmentPlan?.let { JsonPrimitive(it) } ?: JsonNull)
            put("doctor_advice", doctorAdvice?.let { JsonPrimitive(it) } ?: JsonNull)
            put("weight_kg", weightKg?.let { JsonPrimitive(it) } ?: JsonNull)
            put("height_cm", heightCm?.let { JsonPrimitive(it) } ?: JsonNull)
            put("temperature_c", temperatureC?.let { JsonPrimitive(it) } ?: JsonNull)
            put("blood_pressure", bloodPressure?.let { JsonPrimitive(it) } ?: JsonNull)
            put("next_appointment_date", nextAppointmentDate?.let { JsonPrimitive(it) } ?: JsonNull)
            put("preparation_notes", preparationNotes?.let { JsonPrimitive(it) } ?: JsonNull)
            put("reminders", reminders?.let { rs -> buildJsonArray { rs.forEach { add(JsonPrimitive(it)) } } } ?: JsonNull)
            put("status", JsonPrimitive(status))
        }
        if (id == null) {
            supabase.from("doctor_visits").insert(payload) { select() }.decodeSingle<DoctorVisit>()
        } else {
            supabase.from("doctor_visits").update(payload) { filter { eq("id", id) }; select() }.decodeSingle<DoctorVisit>()
        }
    }

    suspend fun deleteVisit(id: String): Result<Unit> = runCatching {
        supabase.from("doctor_visits").delete { filter { eq("id", id) } }
    }
}
