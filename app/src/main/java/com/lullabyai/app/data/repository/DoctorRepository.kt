package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.Appointment
import com.lullabyai.app.data.model.DoctorAppointmentSlot
import com.lullabyai.app.data.model.DoctorAvailability
import com.lullabyai.app.data.model.DoctorProfile
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.model.hasDoctorRole
import com.lullabyai.app.data.model.VaccineVerificationRequest
import com.lullabyai.app.data.remote.DoctorPatientDriveHelper
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Resolved hours for one calendar day (special date overrides weekly). */
data class ClinicDaySchedule(
    val isOpen: Boolean,
    val startTime: String? = null,
    val endTime: String? = null
)

/** Backs the Doctor role flow: clinic profile, weekly availability, bookable slots,
 *  appointments (shared between parent + doctor sides), and vaccine verification requests.
 *  Same conventions as DoctorVisitRepository — explicit JSON payloads for insert/update. */
class DoctorRepository {

    companion object {
        val BANGKOK: ZoneId = ZoneId.of("Asia/Bangkok")
        private const val SLOT_INSERT_CHUNK = 80
        private val CAPACITY_APPOINTMENT_STATUSES = setOf("pending", "confirmed", "completed")

        fun slotStartInstant(slot: DoctorAppointmentSlot): Instant? = runCatching {
            LocalDateTime.of(
                LocalDate.parse(slot.slotDate),
                LocalTime.parse(slot.startTime.take(5))
            ).atZone(BANGKOK).toInstant()
        }.getOrNull()

        /** Parent booking UI — slot_date + start_time must be now or later in Asia/Bangkok. */
        fun isFutureSlot(slot: DoctorAppointmentSlot): Boolean {
            val start = slotStartInstant(slot) ?: return false
            return !start.isBefore(Instant.now())
        }

        fun effectiveBookedCount(slot: DoctorAppointmentSlot, appointmentsOnSlot: Int): Int =
            maxOf(slot.bookedCount, appointmentsOnSlot)

        fun hasRemainingCapacity(slot: DoctorAppointmentSlot, appointmentsOnSlot: Int): Boolean =
            slot.status == "open" && effectiveBookedCount(slot, appointmentsOnSlot) < slot.capacity
    }

    // --- Clinic profile ---

    suspend fun getDoctorProfile(doctorId: String): Result<DoctorProfile> = runCatching {
        supabase.from("doctor_profiles").select {
            filter { eq("id", doctorId) }
        }.decodeSingle<DoctorProfile>()
    }

    suspend fun upsertDoctorProfile(
        doctorId: String,
        clinicName: String?,
        specialty: String?,
        licenseNumber: String?,
        clinicAddress: String?,
        clinicPhotoUrl: String?,
        bio: String?
    ): Result<DoctorProfile> = runCatching {
        val payload = buildJsonObject {
            put("id", JsonPrimitive(doctorId))
            put("clinic_name", clinicName?.let { JsonPrimitive(it) } ?: JsonNull)
            put("specialty", specialty?.let { JsonPrimitive(it) } ?: JsonNull)
            put("license_number", licenseNumber?.let { JsonPrimitive(it) } ?: JsonNull)
            put("clinic_address", clinicAddress?.let { JsonPrimitive(it) } ?: JsonNull)
            put("clinic_photo_url", clinicPhotoUrl?.let { JsonPrimitive(it) } ?: JsonNull)
            put("bio", bio?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        supabase.from("doctor_profiles").upsert(payload) { select() }.decodeSingle<DoctorProfile>()
    }

    /** Parent-side doctor directory search — matches on clinic name / specialty via the joined profile's full_name. */
    suspend fun searchDoctors(query: String? = null): Result<List<Pair<Profile, DoctorProfile>>> = runCatching {
        val profiles = supabase.from("profiles").select().decodeList<Profile>()
            .filter { it.hasDoctorRole() }
        val clinicProfiles = supabase.from("doctor_profiles").select().decodeList<DoctorProfile>()
        val byId = clinicProfiles.associateBy { it.id }
        profiles.mapNotNull { p -> byId[p.id]?.let { p to it } }
            .filter { (p, dp) ->
                query.isNullOrBlank() ||
                    p.fullName?.contains(query, ignoreCase = true) == true ||
                    p.phone?.contains(query.filter { it.isDigit() }.ifBlank { query }, ignoreCase = true) == true ||
                    dp.specialty?.contains(query, ignoreCase = true) == true ||
                    dp.clinicName?.contains(query, ignoreCase = true) == true
            }
    }

    /**
     * #7 — the parent-facing doctor directory: only doctors a parent can actually BOOK, meaning
     * they have published BOTH working hours and appointment slots. Listing a doctor with neither
     * walks the parent into a dead end.
     *
     * Deliberately a SEPARATE function rather than a filter inside [searchDoctors]: five callers
     * (AppointmentDetailsViewModel, DoctorReservationViewModel, PatientRegistrationViewModel and
     * two in DoctorConnectViewModels) call `searchDoctors(null)` purely to resolve a NAME, and
     * filtering there would silently break every one of them.
     *
     * Both source tables are readable by any authenticated user (RLS "Anyone can view ..."), so
     * this needs no extra permission.
     */
    suspend fun searchBookableDoctors(query: String? = null): Result<List<Pair<Profile, DoctorProfile>>> =
        runCatching {
            val bookableIds = fetchBookableDoctorIdsViaRpc()
            val candidates = searchDoctors(query).getOrThrow()
            when {
                bookableIds != null -> candidates.filter { (profile, _) -> profile.id in bookableIds }
                else -> candidates.filter { (profile, _) -> isDoctorListedForParentBooking(profile.id) }
            }
        }

    /** Server-side bookable set (bypasses RLS gaps). Null when RPC not deployed yet. */
    private suspend fun fetchBookableDoctorIdsViaRpc(): Set<String>? = runCatching {
        supabase.postgrest.rpc("list_bookable_doctor_ids", buildJsonObject { })
            .decodeList<BookableDoctorIdRow>()
            .map { it.doctorId }
            .toSet()
    }.getOrNull()

    /**
     * Parent directory + booking: weekly hours configured and at least one open slot a parent can book.
     * Uses per-doctor reads (same as reservation) — a global table scan breaks when RLS only returns
     * the signed-in user's own availability/slot rows.
     */
    suspend fun isDoctorListedForParentBooking(doctorId: String): Boolean {
        val weekly = getAvailability(doctorId).getOrDefault(emptyList())
        val hasHours = weekly.any { !it.isDayOff && !it.startTime.isNullOrBlank() && !it.endTime.isNullOrBlank() }
        if (!hasHours) return false
        return getBookableSlotsForParent(doctorId).getOrDefault(emptyList()).isNotEmpty()
    }

    /** Display name for any user id — a plain lookup on profiles.full_name, mirroring the join
     *  searchDoctors() already does. Used for the parent on a registration/appointment request card,
     *  and for the doctor when stamping their own name onto a clinical record. */
    suspend fun getProfileFullName(userId: String): Result<String?> = runCatching {
        supabase.from("profiles").select { filter { eq("id", userId) } }.decodeSingle<Profile>().fullName
    }

    // --- Weekly availability ---

    suspend fun resolveClinicScheduleForDate(doctorId: String, date: LocalDate): Result<ClinicDaySchedule> = runCatching {
        val dateStr = date.toString()
        val special = supabase.from("doctor_special_dates").select {
            filter {
                eq("doctor_id", doctorId)
                eq("special_date", dateStr)
            }
        }.decodeList<DoctorSpecialDateRow>().firstOrNull()
        if (special != null) {
            return@runCatching ClinicDaySchedule(
                isOpen = !special.isClosed,
                startTime = special.startTime?.take(5),
                endTime = special.endTime?.take(5)
            )
        }
        val dow = date.dayOfWeek.value % 7
        val weekly = getAvailability(doctorId).getOrThrow().firstOrNull { it.dayOfWeek == dow }
        when {
            weekly == null || weekly.isDayOff -> ClinicDaySchedule(isOpen = false)
            else -> ClinicDaySchedule(
                isOpen = true,
                startTime = weekly.startTime?.take(5),
                endTime = weekly.endTime?.take(5)
            )
        }
    }

    /** Next [limit] calendar days when the clinic accepts appointments (weekly + special dates). */
    suspend fun upcomingOpenClinicDates(doctorId: String, from: LocalDate, limit: Int): Result<List<LocalDate>> =
        runCatching {
            val weeklyByDow = getAvailability(doctorId).getOrThrow().associateBy { it.dayOfWeek }
            val specials = supabase.from("doctor_special_dates").select {
                filter {
                    eq("doctor_id", doctorId)
                    gte("special_date", from.toString())
                }
            }.decodeList<DoctorSpecialDateRow>().associateBy { it.specialDate }
            val out = mutableListOf<LocalDate>()
            var cursor = from
            while (out.size < limit && !cursor.isAfter(from.plusDays(120))) {
                val special = specials[cursor.toString()]
                val open = when {
                    special != null -> !special.isClosed
                    else -> weeklyByDow[cursor.dayOfWeek.value % 7]?.let { !it.isDayOff } == true
                }
                if (open) out += cursor
                cursor = cursor.plusDays(1)
            }
            out
        }

    /** Drop future unbooked slots on weekdays now marked closed (after hours edit). */
    suspend fun purgeUnbookedSlotsOnClosedWeekdays(doctorId: String): Result<Unit> = runCatching {
        val closedDows = getAvailability(doctorId).getOrThrow()
            .filter { it.isDayOff }
            .map { it.dayOfWeek }
            .toSet()
        if (closedDows.isEmpty()) return@runCatching
        val today = LocalDate.now(BANGKOK).toString()
        getSlots(doctorId, today).getOrThrow()
            .filter { slot ->
                slot.bookedCount == 0 &&
                    runCatching { LocalDate.parse(slot.slotDate).dayOfWeek.value % 7 }.getOrNull() in closedDows
            }
            .forEach { deleteSlot(it.id).getOrThrow() }
    }

    suspend fun getAvailability(doctorId: String): Result<List<DoctorAvailability>> = runCatching {
        supabase.from("doctor_availability").select {
            filter { eq("doctor_id", doctorId) }
            order("day_of_week", Order.ASCENDING)
        }.decodeList<DoctorAvailability>()
    }

    suspend fun setAvailability(
        doctorId: String,
        dayOfWeek: Int,
        startTime: String?,
        endTime: String?,
        isDayOff: Boolean
    ): Result<DoctorAvailability> = runCatching {
        val existing = supabase.from("doctor_availability").select {
            filter { eq("doctor_id", doctorId); eq("day_of_week", dayOfWeek) }
        }.decodeList<DoctorAvailability>().firstOrNull()
        val payload = buildJsonObject {
            put("doctor_id", JsonPrimitive(doctorId))
            put("day_of_week", JsonPrimitive(dayOfWeek))
            put("start_time", startTime?.let { JsonPrimitive(it) } ?: JsonNull)
            put("end_time", endTime?.let { JsonPrimitive(it) } ?: JsonNull)
            put("is_day_off", JsonPrimitive(isDayOff))
        }
        if (existing == null) {
            supabase.from("doctor_availability").insert(payload) { select() }.decodeSingle<DoctorAvailability>()
        } else {
            supabase.from("doctor_availability").update(payload) { filter { eq("id", existing.id) }; select() }.decodeSingle<DoctorAvailability>()
        }
    }

    // --- Bookable appointment slots ---

    suspend fun getSlots(doctorId: String, fromDate: String? = null): Result<List<DoctorAppointmentSlot>> = runCatching {
        supabase.from("doctor_appointment_slots").select {
            filter {
                eq("doctor_id", doctorId)
                fromDate?.let { gte("slot_date", it) }
            }
            order("slot_date", Order.ASCENDING)
        }.decodeList<DoctorAppointmentSlot>()
    }

    /** Open slots with remaining capacity, excluding past times (Asia/Bangkok). */
    suspend fun getBookableSlotsForParent(doctorId: String): Result<List<DoctorAppointmentSlot>> = runCatching {
        val todayBangkok = LocalDate.now(BANGKOK).toString()
        val candidates = getSlots(doctorId, todayBangkok).getOrThrow()
            .filter { it.status == "open" && isFutureSlot(it) }
        val counts = appointmentCountsBySlotId(doctorId, candidates.map { it.id }.toSet())
        candidates.filter { hasRemainingCapacity(it, counts[it.id] ?: 0) }
    }

    private suspend fun appointmentCountsBySlotId(doctorId: String, slotIds: Set<String>): Map<String, Int> {
        if (slotIds.isEmpty()) return emptyMap()
        return supabase.from("appointments").select {
            filter { eq("doctor_id", doctorId) }
        }.decodeList<Appointment>()
            .filter { appt ->
                appt.slotId != null &&
                    appt.slotId in slotIds &&
                    appt.status in CAPACITY_APPOINTMENT_STATUSES
            }
            .groupingBy { it.slotId!! }
            .eachCount()
    }

    private suspend fun incrementSlotBookedCount(slotId: String) {
        val slot = getSlot(slotId).getOrNull() ?: return
        supabase.from("doctor_appointment_slots").update(
            buildJsonObject { put("booked_count", JsonPrimitive(slot.bookedCount + 1)) }
        ) { filter { eq("id", slotId) } }
    }

    suspend fun createSlot(doctorId: String, slotDate: String, startTime: String, endTime: String, capacity: Int): Result<DoctorAppointmentSlot> = runCatching {
        val payload = slotPayload(doctorId, slotDate, startTime, endTime, capacity)
        supabase.from("doctor_appointment_slots").insert(payload) { select() }.decodeSingle<DoctorAppointmentSlot>()
    }

    /** Batch insert — one round-trip per chunk instead of one HTTP call per slot. */
    suspend fun createSlotsBatch(
        doctorId: String,
        slots: List<Quadruple<String, String, String, Int>>
    ): Result<Int> = runCatching {
        if (slots.isEmpty()) return@runCatching 0
        var inserted = 0
        slots.chunked(SLOT_INSERT_CHUNK).forEach { chunk ->
            val payloads = chunk.map { (date, start, end, cap) ->
                slotPayload(doctorId, date, start, end, cap)
            }
            supabase.from("doctor_appointment_slots").insert(payloads)
            inserted += chunk.size
        }
        inserted
    }

    private fun slotPayload(
        doctorId: String,
        slotDate: String,
        startTime: String,
        endTime: String,
        capacity: Int
    ) = buildJsonObject {
        put("doctor_id", JsonPrimitive(doctorId))
        put("slot_date", JsonPrimitive(slotDate))
        put("start_time", JsonPrimitive(startTime))
        put("end_time", JsonPrimitive(endTime))
        put("capacity", JsonPrimitive(capacity))
        put("status", JsonPrimitive("open"))
    }

    /** Single delete for all unbooked slots on a day. */
    suspend fun deleteUnbookedSlotsForDay(doctorId: String, slotDate: String): Result<Unit> = runCatching {
        supabase.from("doctor_appointment_slots").delete {
            filter {
                eq("doctor_id", doctorId)
                eq("slot_date", slotDate)
                eq("booked_count", 0)
            }
        }
    }

    /** Remove a slot outright. The Stitch design puts an X on each generated slot chip — closing a
     *  slot ([setSlotStatus]) is a different intent: it keeps the row and any bookings on it. */
    suspend fun deleteSlot(slotId: String): Result<Unit> = runCatching {
        supabase.from("doctor_appointment_slots").delete { filter { eq("id", slotId) } }
    }

    suspend fun setSlotStatus(slotId: String, status: String): Result<Unit> = runCatching {
        supabase.from("doctor_appointment_slots").update(
            buildJsonObject { put("status", JsonPrimitive(status)) }
        ) { filter { eq("id", slotId) } }
    }

    // --- Appointments (shared parent + doctor side) ---

    suspend fun getAppointmentsForChild(childId: String): Result<List<Appointment>> = runCatching {
        supabase.from("appointments").select {
            filter { eq("child_id", childId) }
            order("scheduled_at", Order.DESCENDING)
        }.decodeList<Appointment>()
    }

    suspend fun getAppointmentsForDoctor(doctorId: String): Result<List<Appointment>> = runCatching {
        supabase.from("appointments").select {
            filter { eq("doctor_id", doctorId) }
            order("scheduled_at", Order.DESCENDING)
        }.decodeList<Appointment>()
    }

    suspend fun getAppointment(id: String): Result<Appointment> = runCatching {
        supabase.from("appointments").select { filter { eq("id", id) } }.decodeSingle<Appointment>()
    }

    /** True when an earlier same-day appointment for this doctor is still in progress at/after this one's time. */
    suspend fun isAppointmentDelayedInQueue(appointment: Appointment): Boolean = runCatching {
        val mine = getAppointmentsForDoctor(appointment.doctorId).getOrDefault(emptyList())
        val myStart = appointment.scheduledAt?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() } ?: return@runCatching false
        if (myStart.isAfter(java.time.Instant.now().plusSeconds(15 * 60))) return@runCatching false
        val sameDay = mine.filter { other ->
            other.id != appointment.id &&
                other.scheduledAt?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }?.let { start ->
                    start.atZone(java.time.ZoneId.systemDefault()).toLocalDate() ==
                        myStart.atZone(java.time.ZoneId.systemDefault()).toLocalDate() &&
                        start.isBefore(myStart)
                } == true
        }
        sameDay.any { earlier ->
            earlier.status in listOf("pending", "confirmed") &&
                earlier.scheduledAt?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
                    ?.isBefore(java.time.Instant.now()) == true
        }
    }.getOrDefault(false)

    suspend fun getSlot(slotId: String): Result<DoctorAppointmentSlot?> = runCatching {
        supabase.from("doctor_appointment_slots").select {
            filter { eq("id", slotId) }
            limit(1)
        }.decodeList<DoctorAppointmentSlot>().firstOrNull()
    }

    /** Parent booking requests awaiting this doctor's approval. */
    suspend fun pendingBookingsForDoctor(doctorId: String): Result<List<Appointment>> = runCatching {
        supabase.from("appointments").select {
            filter { eq("doctor_id", doctorId); eq("status", "pending") }
            order("created_at", Order.DESCENDING)
        }.decodeList<Appointment>()
    }

    suspend fun requestAppointment(
        doctorId: String,
        childId: String,
        slotId: String?,
        reason: String?,
        isEmergency: Boolean = false
    ): Result<Appointment> = runCatching {
        val slot = slotId?.let { id ->
            getSlot(id).getOrThrow() ?: error("Appointment slot not found.")
        }
        if (!isEmergency) {
            if (slot != null) {
                check(isFutureSlot(slot)) { "Cannot book a past appointment slot." }
                val onSlot = appointmentCountsBySlotId(doctorId, setOf(slot.id))[slot.id] ?: 0
                check(hasRemainingCapacity(slot, onSlot)) { "This slot is no longer available." }
            }
        }
        val scheduledAt = when {
            isEmergency -> java.time.Instant.now().toString()
            slot != null -> slotStartInstant(slot)?.toString()
            else -> null
        }
        val notes = if (isEmergency) com.lullabyai.app.util.AppointmentBookingMeta.emergencyNotes(null) else null
        val payload = buildJsonObject {
            put("doctor_id", JsonPrimitive(doctorId))
            put("child_id", JsonPrimitive(childId))
            put("slot_id", slotId?.let { JsonPrimitive(it) } ?: JsonNull)
            put("reason", reason?.let { JsonPrimitive(it) } ?: JsonNull)
            put("status", JsonPrimitive("pending"))
            notes?.let { put("notes", JsonPrimitive(it)) }
            scheduledAt?.let { put("scheduled_at", JsonPrimitive(it)) }
        }
        val created = supabase.from("appointments").insert(payload) { select() }.decodeSingle<Appointment>()
        if (slotId != null) {
            runCatching { incrementSlotBookedCount(slotId) }
        }
        runCatching {
            PediaConnectLinkRepository().register(doctorId, childId, reason)
        }
        created
    }

    /** Confirm/cancel/reschedule/skip/complete — one function since every status transition
     *  updates the same row from either the parent or doctor side (RLS covers who's allowed). */
    suspend fun updateAppointmentStatus(id: String, status: String, scheduledAt: String? = null, notes: String? = null): Result<Appointment> = runCatching {
        val payload = buildJsonObject {
            put("status", JsonPrimitive(status))
            scheduledAt?.let { put("scheduled_at", JsonPrimitive(it)) }
            notes?.let { put("notes", JsonPrimitive(it)) }
        }
        supabase.from("appointments").update(payload) { filter { eq("id", id) }; select() }.decodeSingle<Appointment>()
    }

    /** Doctor's patient directory — distinct children the doctor has ever had an appointment with. */
    /**
     * Every child that counts as this doctor's patient, from BOTH routes into the relationship:
     *  - an appointment with this doctor (the original, booking-driven route), and
     *  - an *approved* Pedia Connect registration, which makes someone a patient before they have
     *    ever booked, and keeps pre-existing patients visible after their appointments age out.
     *
     * Appointments alone was the bug: a doctor who approved a registration still saw an empty list.
     */
    suspend fun getPatientChildIds(doctorId: String): Result<List<String>> = runCatching {
        val fromAppointments = supabase.from("appointments").select {
            filter { eq("doctor_id", doctorId) }
        }.decodeList<Appointment>().map { it.childId }
        val fromPediaConnect = PediaConnectLinkRepository()
            .approvedChildIdsForDoctor(doctorId)
            .getOrDefault(emptyList())
        val fromPendingLinks = PediaConnectLinkRepository()
            .pendingForDoctor(doctorId)
            .getOrDefault(emptyList())
            .map { it.childId }
        (fromAppointments + fromPediaConnect + fromPendingLinks).distinct()
    }

    // --- Vaccine verification ---

    suspend fun getVaccineVerifications(childId: String? = null, doctorId: String? = null): Result<List<VaccineVerificationRequest>> = runCatching {
        supabase.from("vaccine_verification_requests").select {
            filter {
                childId?.let { eq("child_id", it) }
                doctorId?.let { eq("doctor_id", it) }
            }
            order("requested_at", Order.DESCENDING)
        }.decodeList<VaccineVerificationRequest>()
    }

    suspend fun requestVaccineVerification(vaccineRecordId: String, childId: String, doctorId: String?): Result<VaccineVerificationRequest> = runCatching {
        val payload = buildJsonObject {
            put("vaccine_record_id", JsonPrimitive(vaccineRecordId))
            put("child_id", JsonPrimitive(childId))
            put("doctor_id", doctorId?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        supabase.from("vaccine_verification_requests").insert(payload) { select() }.decodeSingle<VaccineVerificationRequest>()
    }

    suspend fun resolveVaccineVerification(id: String, status: String, notes: String?): Result<VaccineVerificationRequest> = runCatching {
        val payload = buildJsonObject {
            put("status", JsonPrimitive(status))
            put("notes", notes?.let { JsonPrimitive(it) } ?: JsonNull)
            put("resolved_at", JsonPrimitive(java.time.Instant.now().toString()))
        }
        supabase.from("vaccine_verification_requests").update(payload) { filter { eq("id", id) }; select() }.decodeSingle<VaccineVerificationRequest>()
    }

    /** Doctor initiates verification — parent gets inbox popup; row is already verified. */
    suspend fun sendVaccineVerificationToParent(
        doctorId: String,
        childId: String,
        vaccineRecordId: String,
        message: String?
    ): Result<VaccineVerificationRequest> = runCatching {
        val now = java.time.Instant.now().toString()
        val noteBody = message?.trim()?.takeIf { it.isNotBlank() }
            ?: "Verified by your doctor"
        val payload = buildJsonObject {
            put("vaccine_record_id", JsonPrimitive(vaccineRecordId))
            put("child_id", JsonPrimitive(childId))
            put("doctor_id", JsonPrimitive(doctorId))
            put("status", JsonPrimitive("verified"))
            put("notes", JsonPrimitive("doctor_sent:$noteBody"))
            put("requested_at", JsonPrimitive(now))
            put("resolved_at", JsonPrimitive(now))
        }
        supabase.from("vaccine_verification_requests").insert(payload) { select() }.decodeSingle<VaccineVerificationRequest>()
    }

    // --- Patient detailed records (Google Drive, not Supabase) ---

    suspend fun isPatientDriveAvailable(): Boolean = DoctorPatientDriveHelper.isDriveAvailable()

    suspend fun uploadPatientDetailRecord(
        doctorId: String,
        childId: String,
        appointmentId: String?,
        childName: String?,
        symptoms: String,
        diagnosis: String,
        treatmentPlan: String,
        weightKg: Double?,
        heightCm: Double?,
        temperatureC: Double?,
        nextAppointmentDate: String?
    ): Result<String> {
        val record = DoctorPatientDriveHelper.PatientDetailRecord(
            id = UUID.randomUUID().toString(),
            doctorId = doctorId,
            childId = childId,
            appointmentId = appointmentId,
            childName = childName,
            symptoms = symptoms,
            diagnosis = diagnosis,
            treatmentPlan = treatmentPlan,
            weightKg = weightKg,
            heightCm = heightCm,
            temperatureC = temperatureC,
            nextAppointmentDate = nextAppointmentDate
        )
        return DoctorPatientDriveHelper.uploadPatientRecord(record)
    }

    suspend fun listPatientDetailFiles(childId: String): Result<List<DoctorPatientDriveHelper.DriveFileMeta>> =
        DoctorPatientDriveHelper.listPatientRecords(childId)

    suspend fun downloadPatientDetail(fileId: String): Result<DoctorPatientDriveHelper.PatientDetailRecord> =
        DoctorPatientDriveHelper.downloadPatientRecord(fileId)
}

@Serializable
private data class DoctorSpecialDateRow(
    @SerialName("special_date") val specialDate: String = "",
    @SerialName("is_closed") val isClosed: Boolean = true,
    @SerialName("start_time") val startTime: String? = null,
    @SerialName("end_time") val endTime: String? = null
)

@Serializable
private data class BookableDoctorIdRow(
    @SerialName("doctor_id") val doctorId: String
)

/** Lightweight slot insert tuple for [DoctorRepository.createSlotsBatch]. */
data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
