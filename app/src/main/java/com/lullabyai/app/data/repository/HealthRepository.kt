package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.Allergy
import com.lullabyai.app.data.model.GrowthRecord
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.data.model.VaccineRecord
import com.lullabyai.app.data.remote.StorageHelper
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

class HealthRepository {

    suspend fun getVaccineRecords(childId: String): Result<List<VaccineRecord>> = runCatching {
        supabase.from("vaccine_records").select {
            filter { eq("child_id", childId) }
            order("age_order", Order.ASCENDING)
        }.decodeList<VaccineRecord>()
    }

    suspend fun updateVaccineStatus(id: String, status: String, dateGiven: String?): Result<Unit> = runCatching {
        supabase.from("vaccine_records").update(
            kotlinx.serialization.json.buildJsonObject {
                put("status", kotlinx.serialization.json.JsonPrimitive(status))
                if (dateGiven != null) put("date_given", kotlinx.serialization.json.JsonPrimitive(dateGiven))
            }
        ) { filter { eq("id", id) } }
    }

    suspend fun addVaccineRecord(record: VaccineRecord): Result<VaccineRecord> = runCatching {
        supabase.from("vaccine_records").insert(record) {
            select()
        }.decodeSingle<VaccineRecord>()
    }

    suspend fun updateVaccineRecord(record: VaccineRecord): Result<Unit> = runCatching {
        supabase.from("vaccine_records").update(record) {
            filter { eq("id", record.id) }
        }
    }

    suspend fun deleteVaccineRecord(id: String): Result<Unit> = runCatching {
        supabase.from("vaccine_records").delete { filter { eq("id", id) } }
    }

    /**
     * Keeps vaccine statuses current as the child ages. Recomputes every NON-completed vaccine
     * from its scheduled date vs today, so the "recommended next" vaccine advances each month:
     *   • past due  → "overdue"     (recommended now / missed)
     *   • ≤ 2 months → "appointment" (recommended soon)
     *   • later     → "upcoming"
     * Completed vaccines are never changed. Returns true if anything changed.
     */
    suspend fun refreshVaccineStatuses(childId: String): Boolean {
        val records = getVaccineRecords(childId).getOrDefault(emptyList())
        if (records.isEmpty()) return false
        val today = java.time.LocalDate.now()
        var changed = false
        for (r in records) {
            if (r.status == "completed" || r.status == "skipped") continue   // never touch user decisions
            val due = r.scheduledDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            val newStatus = when {
                due == null -> r.status ?: "scheduled"
                due.isBefore(today) -> "overdue"
                due.isBefore(today.plusMonths(2)) -> "appointment"
                else -> "upcoming"
            }
            if (newStatus != r.status) {
                updateVaccineStatus(r.id, newStatus, null)
                changed = true
            }
        }
        return changed
    }

    /** Reschedule / edit a vaccine: name, scheduled date, description, place. Uses explicit JSON so
     *  every field is always sent (data-class inserts/updates drop fields equal to defaults). */
    suspend fun updateVaccineSchedule(
        id: String, vaccineName: String, scheduledDate: String?, description: String?, place: String?
    ): Result<Unit> = runCatching {
        supabase.from("vaccine_records").update(
            kotlinx.serialization.json.buildJsonObject {
                put("vaccine_name", kotlinx.serialization.json.JsonPrimitive(vaccineName))
                put("scheduled_date", kotlinx.serialization.json.JsonPrimitive(scheduledDate))
                put("description", kotlinx.serialization.json.JsonPrimitive(description))
                put("place", place?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
            }
        ) { filter { eq("id", id) } }
    }

    /** Mark a vaccine as skipped, storing the parent's reason (shown on the card). */
    suspend fun skipVaccine(id: String, reason: String?): Result<Unit> = runCatching {
        supabase.from("vaccine_records").update(
            kotlinx.serialization.json.buildJsonObject {
                put("status", kotlinx.serialization.json.JsonPrimitive("skipped"))
                put("notes", reason?.let { kotlinx.serialization.json.JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
            }
        ) { filter { eq("id", id) } }
    }

    // ---------------- ALLERGIES ----------------

    suspend fun getAllergies(childId: String): Result<List<Allergy>> = runCatching {
        supabase.from("allergies").select {
            filter { eq("child_id", childId) }
            order("recorded_at", Order.DESCENDING)
        }.decodeList<Allergy>()
    }

    suspend fun addAllergy(childId: String, name: String, type: String, severity: String, symptoms: String?): Result<Unit> = runCatching {
        supabase.from("allergies").insert(
            buildJsonObject {
                put("child_id", JsonPrimitive(childId))
                put("name", JsonPrimitive(name))
                put("type", JsonPrimitive(type))
                put("severity", JsonPrimitive(severity))
                put("symptoms", symptoms?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        )
    }

    suspend fun updateAllergy(id: String, name: String, type: String, severity: String, symptoms: String?): Result<Unit> = runCatching {
        supabase.from("allergies").update(
            buildJsonObject {
                put("name", JsonPrimitive(name))
                put("type", JsonPrimitive(type))
                put("severity", JsonPrimitive(severity))
                put("symptoms", symptoms?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        ) { filter { eq("id", id) } }
    }

    suspend fun deleteAllergy(id: String): Result<Unit> = runCatching {
        supabase.from("allergies").delete { filter { eq("id", id) } }
    }

    suspend fun getGrowthRecords(childId: String): Result<List<GrowthRecord>> = runCatching {
        supabase.from("growth_records").select {
            filter { eq("child_id", childId) }
            order("recorded_at", Order.DESCENDING)
        }.decodeList<GrowthRecord>()
    }

    suspend fun addGrowthRecord(record: GrowthRecord): Result<GrowthRecord> = runCatching {
        supabase.from("growth_records").insert(record) {
            select()
        }.decodeSingle<GrowthRecord>()
    }

    suspend fun getHealthRecords(childId: String): Result<List<HealthRecord>> = runCatching {
        supabase.from("health_records").select {
            filter { eq("child_id", childId) }
            order("recorded_at", Order.DESCENDING)
        }.decodeList<HealthRecord>()
    }

    suspend fun addHealthRecord(record: HealthRecord): Result<HealthRecord> = runCatching {
        supabase.from("health_records").insert(record) {
            select()
        }.decodeSingle<HealthRecord>()
    }

    /** Edit an existing health record from the detail screen. Explicit JSON so every field is
     *  always sent (data-class updates drop fields equal to their default). */
    suspend fun updateHealthRecord(
        id: String,
        title: String,
        description: String?,
        additionalSymptoms: List<String>,
        photoUrl: String?,
        temperature: Double?
    ): Result<Unit> = runCatching {
        supabase.from("health_records").update(
            buildJsonObject {
                put("title", JsonPrimitive(title))
                put("description", description?.let { JsonPrimitive(it) } ?: JsonNull)
                put("additional_symptoms", buildJsonArray { additionalSymptoms.forEach { add(JsonPrimitive(it)) } })
                put("photo_url", photoUrl?.let { JsonPrimitive(it) } ?: JsonNull)
                put("temperature", temperature?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        ) { filter { eq("id", id) } }
    }

    /** Uploads a health-record photo to the PRIVATE avatars bucket (per-user folder) and returns
     *  the stored bucket PATH (served later via a short-lived signed URL, like child avatars). */
    suspend fun uploadHealthPhoto(childId: String, recordId: String, bytes: ByteArray): Result<String> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        val path = "$userId/health_${childId}_$recordId.jpg"
        supabase.storage.from(StorageHelper.AVATAR_BUCKET).upload(path, bytes) { upsert = true }
        path
    }

    /**
     * Seeds the Thai national immunization (EPI/NIP) 0–3y schedule, grouped by age, with status
     * derived from the child's birthdate.
     * Source: ตารางการให้วัคซีนในเด็กไทย 2567 — สมาคมโรคติดเชื้อในเด็กแห่งประเทศไทย (PIDST) /
     * กรมควบคุมโรค กระทรวงสาธารณสุข. Notable current-schedule points reflected here:
     *   • MMR เข็มที่ 1 ที่ 9 เดือน (เลื่อนจาก 12 เดือน), MMR เข็มที่ 2 ที่ 18 เดือน
     *   • Rota 3 ครั้ง (2/4/6 เดือน) — 2–3 ครั้งขึ้นกับชนิดวัคซีน
     *   • LAJE (ไข้สมองอักเสบชนิดเชื้อเป็น) เข็มที่ 1 ที่ 12 เดือน, เข็มที่ 2 ที่ 2 ปี 6 เดือน
     */
    suspend fun insertDefaultVaccines(childId: String, birthdate: String) {
        // name | description | ageGroup | ageMonths
        val schedule = listOf(
            Triple("BCG", "วัคซีนวัณโรค ป้องกันวัณโรครุนแรงในเด็ก" to "แรกเกิด", 0),
            Triple("HBV เข็มที่ 1", "วัคซีนตับอักเสบบี เข็มแรก" to "แรกเกิด", 0),
            Triple("DTP-HB-Hib เข็มที่ 1", "คอตีบ-บาดทะยัก-ไอกรน-ตับอักเสบบี-ฮิบ" to "2 เดือน", 2),
            Triple("OPV เข็มที่ 1", "วัคซีนโปลิโอชนิดหยอด" to "2 เดือน", 2),
            Triple("Rota เข็มที่ 1", "ป้องกันท้องเสียรุนแรงจากไวรัสโรต้า" to "2 เดือน", 2),
            Triple("DTP-HB-Hib เข็มที่ 2", "คอตีบ-บาดทะยัก-ไอกรน เข็มที่ 2" to "4 เดือน", 4),
            Triple("OPV เข็มที่ 2", "โปลิโอชนิดหยอด เข็มที่ 2" to "4 เดือน", 4),
            Triple("IPV", "โปลิโอชนิดฉีด" to "4 เดือน", 4),
            Triple("Rota เข็มที่ 2", "ป้องกันท้องเสียรุนแรงจากไวรัสโรต้า เข็มที่ 2" to "4 เดือน", 4),
            Triple("DTP-HB-Hib เข็มที่ 3", "คอตีบ-บาดทะยัก-ไอกรน เข็มที่ 3" to "6 เดือน", 6),
            Triple("OPV เข็มที่ 3", "โปลิโอชนิดหยอด เข็มที่ 3" to "6 เดือน", 6),
            Triple("Rota เข็มที่ 3", "ป้องกันท้องเสียรุนแรงจากไวรัสโรต้า เข็มที่ 3 (ถ้าใช้ชนิด 3 ครั้ง)" to "6 เดือน", 6),
            Triple("ไข้หวัดใหญ่", "วัคซีนไข้หวัดใหญ่ ตามฤดูกาล (อายุ 6 เดือนขึ้นไป)" to "6 เดือน", 6),
            Triple("MMR เข็มที่ 1", "หัด-คางทูม-หัดเยอรมัน เข็มแรก (อายุ 9 เดือน)" to "9 เดือน", 9),
            Triple("JE เข็มที่ 1", "ไข้สมองอักเสบเจอี ชนิดเชื้อเป็น เข็มแรก" to "1 ปี", 12),
            Triple("DTP กระตุ้น", "คอตีบ-บาดทะยัก-ไอกรน เข็มกระตุ้น ครั้งที่ 1" to "1 ปี 6 เดือน", 18),
            Triple("OPV กระตุ้น", "โปลิโอชนิดหยอด เข็มกระตุ้น ครั้งที่ 1" to "1 ปี 6 เดือน", 18),
            Triple("MMR เข็มที่ 2", "หัด-คางทูม-หัดเยอรมัน เข็มที่ 2" to "1 ปี 6 เดือน", 18),
            Triple("JE เข็มที่ 2", "ไข้สมองอักเสบเจอี ชนิดเชื้อเป็น เข็มที่ 2" to "2 ปี 6 เดือน", 30)
        )
        val today = java.time.LocalDate.now()
        val birth = runCatching { java.time.LocalDate.parse(birthdate) }.getOrNull()

        val rows = schedule.map { (name, descGroup, ageMonths) ->
            val (description, group) = descGroup
            val due = birth?.plusMonths(ageMonths.toLong())
            val status = when {
                due == null -> "scheduled"
                due.isBefore(today) -> "completed"
                due.isBefore(today.plusMonths(2)) -> "appointment"
                else -> "upcoming"
            }
            VaccineRecord(
                childId = childId,
                vaccineName = name,
                description = description,
                ageGroup = group,
                ageOrder = ageMonths,
                scheduledDate = due?.toString(),
                dateGiven = if (status == "completed") due?.toString() else null,
                status = status
            )
        }
        runCatching { supabase.from("vaccine_records").insert(rows) }
    }
}
