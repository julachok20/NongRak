package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.Allergy
import com.lullabyai.app.data.model.GrowthRecord
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.data.model.VaccineRecord
import com.lullabyai.app.data.remote.StorageHelper
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlin.time.Duration.Companion.hours
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    /** Bucket holding medical attachments. Certificates live under {ownerUserId}/{childId}/certs/. */
    private val certBucket get() = supabase.storage.from("medical-docs")

    @Serializable
    private data class ChildOwnerRow(@SerialName("user_id") val userId: String)

    /**
     * Upload an ใบรับรองวัคซีน (#10) and return its bucket PATH, not a URL — signed URLs expire,
     * paths don't, so the path is what belongs on the row.
     * Path matches parent medical-docs RLS: first folder = child owner's auth uid.
     */
    suspend fun uploadVaccineCertificate(
        childId: String,
        recordId: String,
        bytes: ByteArray,
        ext: String
    ): Result<String> = runCatching {
        val ownerId = supabase.from("children").select(Columns.list("user_id")) {
            filter { eq("id", childId) }
        }.decodeSingle<ChildOwnerRow>().userId
        val path = "$ownerId/$childId/certs/vaccine_$recordId.$ext"
        certBucket.upload(path, bytes) { upsert = true }
        path
    }

    /** Short-lived signed link so the parent can view a stored certificate. */
    suspend fun signedVaccineCertificateUrl(path: String): Result<String> = runCatching {
        certBucket.createSignedUrl(path, 1.hours)
    }

    /** Index an uploaded vaccine PDF in the parent's คลังเอกสาร (category: certs). Idempotent by [filePath]. */
    suspend fun registerVaccineCertificateInVault(
        childId: String,
        filePath: String,
        vaccineName: String,
        fileSizeBytes: Int
    ): Result<Unit> = runCatching {
        val safeName = vaccineName.trim().ifBlank { "vaccine" }
        val fileName = "ใบรับรองวัคซีน $safeName.pdf"
        runCatching {
            supabase.postgrest.rpc(
                "register_vaccine_certificate_document",
                buildJsonObject {
                    put("p_child_id", JsonPrimitive(childId))
                    put("p_file_path", JsonPrimitive(filePath))
                    put("p_file_name", JsonPrimitive(fileName))
                    put("p_file_size_bytes", JsonPrimitive(fileSizeBytes.toLong()))
                }
            )
        }.onFailure {
            // Fallback when RPC not deployed yet — direct insert (needs RLS policy).
            runCatching {
                val existing = supabase.from("medical_documents").select(Columns.list("id")) {
                    filter { eq("file_path", filePath) }
                    limit(1)
                }.decodeList<MedicalDocIdRow>()
                if (existing.isEmpty()) {
                    supabase.from("medical_documents").insert(buildJsonObject {
                        put("child_id", JsonPrimitive(childId))
                        put("file_name", JsonPrimitive(fileName))
                        put("file_path", JsonPrimitive(filePath))
                        put("mime_type", JsonPrimitive("application/pdf"))
                        put("category", JsonPrimitive("certs"))
                        put("file_size_bytes", JsonPrimitive(fileSizeBytes.toLong()))
                        put("verified", JsonPrimitive(true))
                    })
                }
            }
        }
    }

    @Serializable
    private data class MedicalDocIdRow(val id: String)

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

    // ---------------- WASTE / ELIMINATION RECORDS ----------------

    suspend fun getWasteRecords(childId: String): Result<List<com.lullabyai.app.data.model.WasteRecord>> = runCatching {
        supabase.from("waste_records").select {
            filter { eq("child_id", childId) }
            order("recorded_at", Order.DESCENDING)
        }.decodeList<com.lullabyai.app.data.model.WasteRecord>()
    }

    suspend fun addWasteRecord(childId: String, wasteType: String, consistency: String?, color: String?, amount: String, notes: String?): Result<Unit> = runCatching {
        supabase.from("waste_records").insert(
            buildJsonObject {
                put("child_id", JsonPrimitive(childId))
                put("waste_type", JsonPrimitive(wasteType))
                put("consistency", consistency?.let { JsonPrimitive(it) } ?: JsonNull)
                put("color", color?.let { JsonPrimitive(it) } ?: JsonNull)
                put("amount", JsonPrimitive(amount))
                put("notes", notes?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        )
    }

    suspend fun deleteWasteRecord(id: String): Result<Unit> = runCatching {
        supabase.from("waste_records").delete { filter { eq("id", id) } }
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
        // Structured row is the source of truth — keep the child-row summary string in sync for
        // Nutrition / chat / doctor directory surfaces that still read `children.allergies`.
        syncAllergySummaryForChild(childId)
    }

    suspend fun updateAllergy(id: String, name: String, type: String, severity: String, symptoms: String?): Result<Unit> = runCatching {
        // Need child_id to refresh the denormalized summary after the row update.
        val childId = supabase.from("allergies").select {
            filter { eq("id", id) }
            limit(1)
        }.decodeList<Allergy>().firstOrNull()?.childId
        supabase.from("allergies").update(
            buildJsonObject {
                put("name", JsonPrimitive(name))
                put("type", JsonPrimitive(type))
                put("severity", JsonPrimitive(severity))
                put("symptoms", symptoms?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        ) { filter { eq("id", id) } }
        if (childId != null) syncAllergySummaryForChild(childId)
    }

    suspend fun deleteAllergy(id: String): Result<Unit> = runCatching {
        val childId = supabase.from("allergies").select {
            filter { eq("id", id) }
            limit(1)
        }.decodeList<Allergy>().firstOrNull()?.childId
        supabase.from("allergies").delete { filter { eq("id", id) } }
        if (childId != null) syncAllergySummaryForChild(childId)
    }

    /** Rebuild `children.allergies` from the structured allergy rows for this child. */
    private suspend fun syncAllergySummaryForChild(childId: String) {
        val names = getAllergies(childId).getOrDefault(emptyList()).map { it.name }
        ChildRepository().syncAllergySummary(childId, names)
    }

    // ---------------- SYMPTOM LOGS (sickness day-tracking, parent = a health_records row) ----------------

    suspend fun getSymptomLogs(healthRecordId: String): Result<List<com.lullabyai.app.data.model.SymptomLog>> = runCatching {
        supabase.from("health_symptom_logs").select {
            filter { eq("health_record_id", healthRecordId) }
            order("log_date", Order.DESCENDING)
        }.decodeList<com.lullabyai.app.data.model.SymptomLog>()
    }

    /**
     * Save one timeline entry for an episode. Multiple entries per day are allowed — no unique
     * constraint on (health_record_id, log_date) — so a null [existingId] always INSERTs a new
     * row (a new timeline entry); a non-null [existingId] UPDATEs that specific row in place.
     * Stamps parent [symptoms_updated_at] so F3's 10-min edit lock clocks from this write.
     */
    suspend fun upsertSymptomLog(
        healthRecordId: String,
        childId: String,
        logDate: String,
        symptoms: Map<String, String>,
        temperature: Double?,
        feeling: String?,
        dayStatus: String,
        notes: String?,
        painLevel: Int?,
        photoUrl: String?,
        existingId: String? = null,
        temperatureUnit: String = "C"
    ): Result<com.lullabyai.app.data.model.SymptomLog> = runCatching {
        val now = java.time.Instant.now().toString()
        val payload = buildJsonObject {
            put("health_record_id", JsonPrimitive(healthRecordId))
            put("child_id", JsonPrimitive(childId))
            put("log_date", JsonPrimitive(logDate))
            put("symptoms", buildJsonObject { symptoms.forEach { (k, v) -> put(k, JsonPrimitive(v)) } })
            put("temperature", temperature?.let { JsonPrimitive(it) } ?: JsonNull)
            put("temperature_unit", JsonPrimitive(temperatureUnit))
            put("feeling", feeling?.let { JsonPrimitive(it) } ?: JsonNull)
            put("day_status", JsonPrimitive(dayStatus))
            put("notes", notes?.let { JsonPrimitive(it) } ?: JsonNull)
            put("pain_level", painLevel?.let { JsonPrimitive(it) } ?: JsonNull)
            put("photo_url", photoUrl?.let { JsonPrimitive(it) } ?: JsonNull)
            if (existingId != null) put("updated_at", JsonPrimitive(now))
        }
        val saved = if (existingId != null) {
            supabase.from("health_symptom_logs").update(payload) {
                filter { eq("id", existingId) }
                select()
            }.decodeSingle<com.lullabyai.app.data.model.SymptomLog>()
        } else {
            supabase.from("health_symptom_logs").insert(payload) { select() }
                .decodeSingle<com.lullabyai.app.data.model.SymptomLog>()
        }
        stampSymptomsUpdatedAt(healthRecordId, now)
        saved
    }

    @Deprecated("Use upsertSymptomLog", ReplaceWith("upsertSymptomLog(healthRecordId, childId, java.time.LocalDate.now().toString(), symptoms, temperature, feeling, dayStatus, notes, null, null, null)"))
    suspend fun addSymptomLog(
        healthRecordId: String, childId: String, symptoms: Map<String, String>,
        temperature: Double?, feeling: String?, dayStatus: String, notes: String?
    ): Result<Unit> = upsertSymptomLog(
        healthRecordId, childId, java.time.LocalDate.now().toString(),
        symptoms, temperature, feeling, dayStatus, notes, null, null, null
    ).map { }

    private suspend fun stampSymptomsUpdatedAt(healthRecordId: String, iso: String) {
        supabase.from("health_records").update(
            buildJsonObject {
                put("symptoms_updated_at", JsonPrimitive(iso))
                put("updated_at", JsonPrimitive(iso))
            }
        ) { filter { eq("id", healthRecordId) } }
    }

    // ---------------- CHRONIC CONDITIONS ----------------

    suspend fun getChronicConditions(childId: String): Result<List<com.lullabyai.app.data.model.ChronicCondition>> = runCatching {
        supabase.from("chronic_conditions").select {
            filter { eq("child_id", childId) }
            order("recorded_at", Order.DESCENDING)
        }.decodeList<com.lullabyai.app.data.model.ChronicCondition>()
    }

    suspend fun addChronicCondition(
        childId: String,
        name: String,
        notes: String?,
        prevention: String? = null
    ): Result<Unit> = runCatching {
        supabase.from("chronic_conditions").insert(
            buildJsonObject {
                put("child_id", JsonPrimitive(childId))
                put("name", JsonPrimitive(name))
                put("notes", notes?.let { JsonPrimitive(it) } ?: JsonNull)
                put("prevention", prevention?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        )
    }

    suspend fun updateChronicCondition(
        id: String,
        name: String,
        notes: String?,
        prevention: String? = null
    ): Result<Unit> = runCatching {
        supabase.from("chronic_conditions").update(
            buildJsonObject {
                put("name", JsonPrimitive(name))
                put("notes", notes?.let { JsonPrimitive(it) } ?: JsonNull)
                put("prevention", prevention?.let { JsonPrimitive(it) } ?: JsonNull)
            }
        ) { filter { eq("id", id) } }
    }

    suspend fun deleteChronicCondition(id: String): Result<Unit> = runCatching {
        supabase.from("chronic_conditions").delete { filter { eq("id", id) } }
    }

    suspend fun getGrowthRecords(childId: String): Result<List<GrowthRecord>> = runCatching {
        supabase.from("growth_records").select {
            filter { eq("child_id", childId) }
            order("recorded_at", Order.DESCENDING)
        }.decodeList<GrowthRecord>()
    }

    // Builds an explicit JSON payload (no "id") rather than inserting the data class directly —
    // GrowthRecord.id defaults to "" (not null), and growth_records.id is a NOT NULL uuid column,
    // so serializing the whole object would send an invalid empty-string uuid and fail every insert.
    //
    // Also mirrors weight/height onto `children` so every hub that reads the child row (Summary,
    // Health, Edit Child, chat profile block, medicine dosing) sees the same latest vitals as
    // Growth History / WHO charts. Partial records (weight-only / height-only) leave the other
    // child field untouched.
    suspend fun addGrowthRecord(record: GrowthRecord): Result<GrowthRecord> = runCatching {
        val saved = supabase.from("growth_records").insert(
            buildJsonObject {
                put("child_id", JsonPrimitive(record.childId))
                put("weight_kg", record.weightKg?.let { JsonPrimitive(it) } ?: JsonNull)
                put("height_cm", record.heightCm?.let { JsonPrimitive(it) } ?: JsonNull)
                record.recordedAt?.let { put("recorded_at", JsonPrimitive(it)) }
                put("record_type", JsonPrimitive(record.recordType))
                record.notes?.let { put("notes", JsonPrimitive(it)) }
            }
        ) { select() }.decodeSingle<GrowthRecord>()
        if (record.weightKg != null || record.heightCm != null) {
            ChildRepository().updateVitals(record.childId, record.weightKg, record.heightCm)
        }
        saved
    }

    suspend fun deleteGrowthRecord(id: String): Result<Unit> = runCatching {
        supabase.from("growth_records").delete { filter { eq("id", id) } }
    }

    // recorded_at alone isn't a reliable sort key — the add-record screen only lets a parent pick a
    // date, not a time, so entries logged the same day (e.g. "ไข้" then "อาการดีขึ้น" that afternoon)
    // all get midnight as their recorded_at and become indistinguishable. created_at (the real insert
    // timestamp, added via migration) breaks the tie so same-day entries list in the order they were
    // actually logged, not an arbitrary DB-decided one.
    suspend fun getHealthRecords(childId: String): Result<List<HealthRecord>> = runCatching {
        supabase.from("health_records").select {
            filter { eq("child_id", childId) }
            order("recorded_at", Order.DESCENDING)
            order("created_at", Order.DESCENDING)
        }.decodeList<HealthRecord>()
    }

    // Same reason as addGrowthRecord above — HealthRecord.id also defaults to "" against a NOT
    // NULL uuid column, so an explicit JSON payload (no "id") is required, not the raw data class.
    suspend fun addHealthRecord(record: HealthRecord): Result<HealthRecord> = runCatching {
        val now = java.time.Instant.now().toString()
        supabase.from("health_records").insert(
            buildJsonObject {
                put("child_id", JsonPrimitive(record.childId))
                put("event_type", JsonPrimitive(record.eventType))
                put("title", JsonPrimitive(record.title))
                record.description?.let { put("description", JsonPrimitive(it)) }
                put("severity", JsonPrimitive(record.severity))
                put("temperature", record.temperature?.let { JsonPrimitive(it) } ?: JsonNull)
                record.additionalSymptoms?.let { syms -> put("additional_symptoms", buildJsonArray { syms.forEach { add(JsonPrimitive(it)) } }) }
                record.photoUrl?.let { put("photo_url", JsonPrimitive(it)) }
                record.recordedAt?.let { put("recorded_at", JsonPrimitive(it)) }
                // F3 — creating with symptoms starts the edit-lock clock now.
                put("symptoms_updated_at", JsonPrimitive(now))
            }
        ) { select() }.decodeSingle<HealthRecord>()
    }

    /** Edit an existing health record from the detail screen. Explicit JSON so every field is
     *  always sent (data-class updates drop fields equal to their default).
     *  Stamps [updated_at] + [symptoms_updated_at] (F3 edit-lock origin + "edited" badge). */
    suspend fun updateHealthRecord(
        id: String,
        title: String,
        description: String?,
        additionalSymptoms: List<String>,
        photoUrl: String?,
        temperature: Double?
    ): Result<Unit> = runCatching {
        val now = java.time.Instant.now().toString()
        supabase.from("health_records").update(
            buildJsonObject {
                put("title", JsonPrimitive(title))
                put("description", description?.let { JsonPrimitive(it) } ?: JsonNull)
                put("additional_symptoms", buildJsonArray { additionalSymptoms.forEach { add(JsonPrimitive(it)) } })
                put("photo_url", photoUrl?.let { JsonPrimitive(it) } ?: JsonNull)
                put("temperature", temperature?.let { JsonPrimitive(it) } ?: JsonNull)
                put("updated_at", JsonPrimitive(now))
                put("symptoms_updated_at", JsonPrimitive(now))
            }
        ) { filter { eq("id", id) } }
    }

    /** Marks a health record monitoring / recovered / unused — drives HealthHistoryScreen badges.
     *  Status toggles do NOT stamp updated_at (edit lock still allows unused ticks). */
    suspend fun updateHealthStatus(id: String, status: String): Result<Unit> = runCatching {
        supabase.from("health_records").update(
            buildJsonObject { put("status", JsonPrimitive(status)) }
        ) { filter { eq("id", id) } }
    }

    /** Latest symptom-log calendar date per health_record_id for a child — used by Batch Q #9
     *  (7-day auto-resolve) without a schema change. */
    suspend fun getLatestSymptomActivityByRecord(childId: String): Result<Map<String, String>> = runCatching {
        val logs = supabase.from("health_symptom_logs").select {
            filter { eq("child_id", childId) }
            order("log_date", Order.DESCENDING)
        }.decodeList<com.lullabyai.app.data.model.SymptomLog>()
        buildMap {
            for (log in logs) {
                if (log.healthRecordId !in this) put(log.healthRecordId, log.logDate)
            }
        }
    }

    /**
     * Latest symptom **write timestamp** per episode — feeds F3's 10-min edit lock.
     * Uses max(updated_at, created_at) of the newest day-log write.
     */
    suspend fun getLatestSymptomUpdateByRecord(childId: String): Result<Map<String, String>> = runCatching {
        val logs = supabase.from("health_symptom_logs").select {
            filter { eq("child_id", childId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<com.lullabyai.app.data.model.SymptomLog>()
        buildMap {
            for (log in logs) {
                val stamp = listOfNotNull(log.updatedAt, log.createdAt).firstOrNull() ?: continue
                val prev = this[log.healthRecordId]
                if (prev == null) {
                    put(log.healthRecordId, stamp)
                } else {
                    val prevI = TimeUtilsInstant(prev)
                    val curI = TimeUtilsInstant(stamp)
                    if (curI != null && (prevI == null || curI.isAfter(prevI))) put(log.healthRecordId, stamp)
                }
            }
        }
    }

    private fun TimeUtilsInstant(iso: String): java.time.Instant? =
        com.lullabyai.app.util.TimeUtils.parseInstant(iso)

    /** Uploads a health-record photo to the PRIVATE avatars bucket (per-user folder) and returns
     *  the stored bucket PATH (served later via a short-lived signed URL, like child avatars). */
    suspend fun uploadHealthPhoto(childId: String, recordId: String, bytes: ByteArray): Result<String> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        val path = "$userId/health_${childId}_$recordId.jpg"
        supabase.storage.from(StorageHelper.AVATAR_BUCKET).upload(path, bytes) { upsert = true }
        path
    }

    /** Day-log photo under private avatars bucket. */
    suspend fun uploadSymptomLogPhoto(childId: String, logKey: String, bytes: ByteArray): Result<String> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not logged in")
        val path = "$userId/symptom_${childId}_$logKey.jpg"
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
        val today = java.time.LocalDate.now()
        val birth = runCatching { java.time.LocalDate.parse(birthdate) }.getOrNull()

        val rows = com.lullabyai.app.data.VaccineSetupCatalog.SCHEDULE.map { dose ->
            val due = birth?.plusMonths(dose.ageMonths.toLong())
            // Provisional — onboarding selections override via applyOnboardingVaccineSelections.
            val status = when {
                due == null -> "scheduled"
                due.isBefore(today) -> "overdue"
                due.isBefore(today.plusMonths(2)) -> "appointment"
                else -> "upcoming"
            }
            VaccineRecord(
                childId = childId,
                vaccineName = dose.dbName,
                description = dose.description,
                ageGroup = dose.ageGroup,
                ageOrder = dose.ageMonths,
                scheduledDate = due?.toString(),
                dateGiven = null,
                status = status
            )
        }
        runCatching { supabase.from("vaccine_records").insert(rows) }
    }

    /** Marks parent-selected doses completed in the vaccine book; resets the rest from due dates. */
    suspend fun applyOnboardingVaccineSelections(
        childId: String,
        selectedNames: List<String>,
        customOnly: List<String> = emptyList()
    ) {
        val canonical = selectedNames.mapNotNull { com.lullabyai.app.data.VaccineSetupCatalog.normalizeSelection(it) }.toSet()
        val records = getVaccineRecords(childId).getOrDefault(emptyList())
        val today = java.time.LocalDate.now()
        val known = records.map { it.vaccineName }.toSet()

        for (r in records) {
            if (r.vaccineName in canonical) {
                val given = r.scheduledDate ?: today.toString()
                updateVaccineStatus(r.id, "completed", given)
            } else {
                val due = r.scheduledDate?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
                val newStatus = when {
                    due == null -> "scheduled"
                    due.isBefore(today) -> "overdue"
                    due.isBefore(today.plusMonths(2)) -> "appointment"
                    else -> "upcoming"
                }
                updateVaccineStatus(r.id, newStatus, null)
            }
        }

        for (name in customOnly) {
            if (name !in known && com.lullabyai.app.data.VaccineSetupCatalog.normalizeSelection(name) == null) {
                addVaccineRecord(
                    VaccineRecord(
                        childId = childId,
                        vaccineName = name,
                        status = "completed",
                        dateGiven = today.toString()
                    )
                )
            }
        }
    }
}
