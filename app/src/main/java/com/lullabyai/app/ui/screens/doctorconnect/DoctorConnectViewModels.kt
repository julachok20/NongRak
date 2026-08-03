package com.lullabyai.app.ui.screens.doctorconnect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Appointment
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.model.DoctorAppointmentSlot
import com.lullabyai.app.data.model.DoctorProfile
import com.lullabyai.app.data.model.DoctorVisit
import com.lullabyai.app.data.model.Profile
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.DoctorRepository
import com.lullabyai.app.data.repository.PediaConnectRepository
import com.lullabyai.app.data.repository.PediaConnectLinkRepository
import com.lullabyai.app.data.repository.DoctorVisitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Instant

/* ------------------------------------------------------------------------------------------------
 * View models for the restructured parent-side Connect flow. They live next to the screens (rather
 * than in viewmodel/) because they're only ever used by this package, and the existing
 * existing files in the viewmodel package are owned by other concurrent work.
 * ---------------------------------------------------------------------------------------------- */

/** How long an appointment is considered "currently happening" after its start. The doctor side
 *  books hour slots and AppointmentDetailsScreen already assumes 1h for the calendar intent. */
private const val APPOINTMENT_WINDOW_MS = 60L * 60L * 1000L

private const val LAB_RESULTS_HUB_DAYS_MS = 5L * 24L * 60L * 60L * 1000L

private const val VISIT_DOCTOR_LINK_MS = 7L * 24L * 60L * 60L * 1000L

private fun visitBelongsToDoctor(visit: DoctorVisit, profile: Profile, doctorAppointments: List<Appointment>): Boolean {
    val doctorName = profile.fullName?.takeIf { it.isNotBlank() }
    if (!visit.doctorName.isNullOrBlank()) {
        return doctorName != null && visit.doctorName.equals(doctorName, ignoreCase = true)
    }
    val visitAt = parseScheduleInstant(visit.visitDatetime) ?: return false
    return doctorAppointments.any { appt ->
        appt.childId == visit.childId && appt.status == "completed" &&
            parseScheduleInstant(appt.scheduledAt)?.let { apptAt ->
                kotlin.math.abs(visitAt.toEpochMilli() - apptAt.toEpochMilli()) <= VISIT_DOCTOR_LINK_MS
            } == true
    }
}

/** True when two schedule strings fall on the same local calendar day (used to suppress a visit
 *  follow-up date after the parent cancelled the matching booking). */
private fun scheduleDatesSameDay(a: String?, b: String?): Boolean {
    val ia = parseScheduleInstant(a) ?: return false
    val ib = parseScheduleInstant(b) ?: return false
    val zone = java.time.ZoneId.systemDefault()
    return ia.atZone(zone).toLocalDate() == ib.atZone(zone).toLocalDate()
}

/** Which CTA set My Doctors Hub shows for a doctor, per the product rule:
 *  - upcoming pending or confirmed, visit not ended     -> [DETAILS] ("ดูรายละเอียด")
 *  - visit ended AND doctor set a follow-up date        -> [FOLLOW_UP] */
enum class ApptCta { DETAILS, FOLLOW_UP }

/** One row of the "รายชื่อคุณหมอ" board — a doctor plus the appointment state that decides its CTAs. */
data class DoctorBoardEntry(
    val profile: Profile,
    val doctorProfile: DoctorProfile,
    /** The appointment the CTAs act on: the soonest upcoming one, else the most recent past one. */
    val appointment: Appointment?,
    /** `doctor_visits.next_appointment_date` for this doctor's patients — the "a next appointment is needed" signal. */
    val nextAppointmentDate: String?,
    /** Start of the most recent past appointment — drives the "Recently Visited" ordering. */
    val lastSeenAt: Instant?,
    /** Latest completed appointment/visit for this doctor+child — 5-day window for "ดูผลตรวจ". */
    val lastCompletedAt: Instant?
) {
    val isHappeningNow: Boolean
        get() {
            val start = parseScheduleInstant(appointment?.scheduledAt) ?: return false
            val now = Instant.now().toEpochMilli()
            return now >= start.toEpochMilli() && now < start.toEpochMilli() + APPOINTMENT_WINDOW_MS
        }

    val hasEnded: Boolean
        get() {
            val a = appointment ?: return false
            if (a.status == "completed") return true
            val start = parseScheduleInstant(a.scheduledAt) ?: return false
            return Instant.now().toEpochMilli() >= start.toEpochMilli() + APPOINTMENT_WINDOW_MS
        }

    val isUpcomingPending: Boolean
        get() {
            val a = appointment ?: return false
            if (a.status != "pending") return false
            val start = parseScheduleInstant(a.scheduledAt) ?: return false
            return start.isAfter(Instant.now())
        }

    val cta: ApptCta
        get() {
            if (isUpcomingPending) return ApptCta.DETAILS
            if (hasEnded && nextAppointmentDate != null) return ApptCta.FOLLOW_UP
            return ApptCta.DETAILS
        }

    val showLabResultsOnHub: Boolean
        get() {
            val a = appointment ?: return false
            val completedAt = lastCompletedAt ?: parseScheduleInstant(a.scheduledAt).takeIf { a.status == "completed" }
                ?: return false
            if (Instant.now().toEpochMilli() - completedAt.toEpochMilli() > LAB_RESULTS_HUB_DAYS_MS) return false
            return a.status == "completed" || cta == ApptCta.FOLLOW_UP
        }

    val isVisitCompleted: Boolean get() = appointment?.status == "completed"

    /** Label above the date row: Stitch shows "นัดหมายครั้งถัดไป" on the follow-up card, "วันที่นัดหมาย" otherwise. */
    val showsNextDate: Boolean get() = cta == ApptCta.FOLLOW_UP
}

data class MyDoctorsBoardUiState(
    val loading: Boolean = false,
    val entries: List<DoctorBoardEntry> = emptyList(),
    /** Doctors ordered by most-recent past appointment — the "คุณหมอที่เคยปรึกษา" rail. */
    val recentlyVisited: List<DoctorBoardEntry> = emptyList(),
    /** pending/confirmed future appointments — the "ติดตามการจองคิว" list. */
    val bookings: List<Appointment> = emptyList(),
    val bookingDoctorNames: Map<String, String> = emptyMap()
)

/** Backs [MyDoctorsHubScreen]. Derives everything from the existing `appointments` + `doctor_visits`
 *  tables — there is no favourites table, so "my doctors" stays "doctors my children have an
 *  appointment row with", the same derivation MyDoctorsHubViewModel used before the restructure.
 *
 *  Callers pass the dashboard-selected child id in [load] (typically a one-element list from
 *  [com.lullabyai.app.data.settings.ChildSelection]); use [force] when the hub regains focus or
 *  the user pull-to-refreshes after booking. */
class MyDoctorsBoardViewModel : ViewModel() {
    private val repo = DoctorRepository()
    private val visitRepo = DoctorVisitRepository()

    private val _ui = MutableStateFlow(MyDoctorsBoardUiState())
    val ui: StateFlow<MyDoctorsBoardUiState> = _ui

    private var loadedFor: List<String>? = null

    fun load(childIds: List<String>, force: Boolean = false) {
        if (childIds.isEmpty()) return
        if (!force && childIds == loadedFor) return
        loadedFor = childIds
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)

            val appointments = childIds.flatMap { repo.getAppointmentsForChild(it).getOrDefault(emptyList()) }
            val visitsByChild = childIds.associateWith { visitRepo.getVisits(it).getOrDefault(emptyList()) }
            val doctors = repo.searchDoctors(null).getOrDefault(emptyList())
                .filter { (p, _) -> p.id in appointments.map { it.doctorId }.toSet() }

            val now = Instant.now()
            val entries = doctors.map { (profile, doctorProfile) ->
                val allForDoctor = appointments.filter { it.doctorId == profile.id }
                val mine = allForDoctor.filter { it.status != "cancelled" }
                val cancelledFuture = allForDoctor.filter {
                    it.status == "cancelled" &&
                        parseScheduleInstant(it.scheduledAt)?.isAfter(now) == true
                }
                val upcoming = mine
                    .filter { parseScheduleInstant(it.scheduledAt)?.isAfter(now) == true && it.status != "completed" }
                    .minByOrNull { parseScheduleInstant(it.scheduledAt)?.toEpochMilli() ?: Long.MAX_VALUE }
                val past = mine
                    .filter { parseScheduleInstant(it.scheduledAt)?.isBefore(now) == true || it.status == "completed" }
                    .maxByOrNull { parseScheduleInstant(it.scheduledAt)?.toEpochMilli() ?: 0L }
                val childIdsForDoctor = allForDoctor.map { it.childId }.distinct()
                val doctorVisits = childIdsForDoctor.flatMap { visitsByChild[it].orEmpty() }
                    .filter { visitBelongsToDoctor(it, profile, mine) }
                val nextFromVisit = doctorVisits
                    .mapNotNull { it.nextAppointmentDate?.takeIf { d -> d.isNotBlank() } }
                    .filter { parseScheduleInstant(it)?.isAfter(now) == true }
                    .filter { visitDate ->
                        cancelledFuture.none { cancelled ->
                            scheduleDatesSameDay(cancelled.scheduledAt, visitDate)
                        }
                    }
                    .minByOrNull { parseScheduleInstant(it)?.toEpochMilli() ?: Long.MAX_VALUE }
                // Prefer an active booking; only fall back to visit follow-up when not cancelled.
                val nextDate = upcoming?.scheduledAt ?: nextFromVisit

                val completedApptInstants = mine.filter { it.status == "completed" }
                    .mapNotNull { parseScheduleInstant(it.scheduledAt) }
                val completedVisitInstants = doctorVisits
                    .filter { it.status == "completed" || it.status == "follow_up" }
                    .mapNotNull { parseScheduleInstant(it.visitDatetime) }
                val lastCompletedAt = (completedApptInstants + completedVisitInstants).maxOrNull()

                DoctorBoardEntry(
                    profile = profile,
                    doctorProfile = doctorProfile,
                    appointment = upcoming ?: past,
                    nextAppointmentDate = nextDate,
                    lastSeenAt = parseScheduleInstant(past?.scheduledAt),
                    lastCompletedAt = lastCompletedAt
                )
            }

            // Cancelled rows stay in the tracker (until their slot time passes) instead of silently
            // vanishing, so the parent sees the status flip to ยกเลิกแล้ว after cancelling.
            val bookings = appointments
                .filter {
                    (it.status == "pending" || it.status == "confirmed" || it.status == "cancelled") &&
                        parseScheduleInstant(it.scheduledAt)?.isAfter(now) == true
                }
                .sortedBy { parseScheduleInstant(it.scheduledAt)?.toEpochMilli() ?: Long.MAX_VALUE }
            val names = doctors.associate { (p, _) -> p.id to (p.fullName ?: "") }

            _ui.value = MyDoctorsBoardUiState(
                loading = false,
                entries = entries.sortedBy { entry ->
                    parseScheduleInstant(entry.appointment?.scheduledAt)?.toEpochMilli()
                        ?: parseScheduleInstant(entry.nextAppointmentDate)?.toEpochMilli()
                        ?: Long.MAX_VALUE
                },
                recentlyVisited = entries.filter { it.lastSeenAt != null }
                    .sortedByDescending { it.lastSeenAt?.toEpochMilli() ?: 0L },
                bookings = bookings,
                bookingDoctorNames = names
            )
        }
    }
}

/* ---------------------------------------------- Lab results ------------------------------------ */

/** One numeric metric plotted over time. Built only from values the doctor actually recorded, so an
 *  empty list here is exactly the "not numeric/structured -> fall back to the doctor's text" case. */
data class LabMetricSeries(
    val key: String,
    val labelTh: String,
    val labelEn: String,
    val unit: String,
    val points: List<Pair<String, Double>>,
    /** Inclusive reference range, when one exists for the metric (temperature, BP). */
    val referenceLow: Double? = null,
    val referenceHigh: Double? = null
) {
    val latest: Double? get() = points.lastOrNull()?.second

    /** null = no reference range to judge against (growth metrics). */
    val inRange: Boolean?
        get() {
            val v = latest ?: return null
            val lo = referenceLow
            val hi = referenceHigh
            if (lo == null && hi == null) return null
            return (lo == null || v >= lo) && (hi == null || v <= hi)
        }
}

data class LabResultsUiState(
    val loading: Boolean = false,
    val visits: List<DoctorVisit> = emptyList(),
    val series: List<LabMetricSeries> = emptyList(),
    val error: String? = null
) {
    /** Any structured numbers to draw? Decides chart path vs doctor-text-only path. */
    val hasChartableData: Boolean get() = series.any { it.points.isNotEmpty() }

    /** Summary table rows in a stable metric order (weight → height → temp → BP). */
    val summarySeries: List<LabMetricSeries>
        get() {
            val order = listOf("weight", "height", "temperature", "systolic")
            return order.mapNotNull { key -> series.find { it.key == key } }
        }

    /** Doctor-authored prose, newest first — the text-only fallback and the AI-insight source. */
    val narratives: List<DoctorVisit>
        get() = visits.filter {
            !it.diagnosis.isNullOrBlank() || !it.treatmentPlan.isNullOrBlank() || !it.doctorAdvice.isNullOrBlank()
        }
}

/** Backs [LabResultsScreen]. The schema has no `lab_results` table; the doctor's Clinical Record
 *  Entry writes vitals + narrative to `doctor_visits` (and a copy of the long form to Drive), so
 *  that table IS the parent-visible result set. Numeric columns become charts, prose becomes text. */
class LabResultsViewModel : ViewModel() {
    private val visitRepo = DoctorVisitRepository()

    private val _ui = MutableStateFlow(LabResultsUiState())
    val ui: StateFlow<LabResultsUiState> = _ui

    private var loadedChildId: String? = null

    fun load(childId: String, force: Boolean = false) {
        if (!force && childId == loadedChildId) return
        loadedChildId = childId
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            visitRepo.getVisits(childId)
                .onSuccess { visits ->
                    val ordered = visits.sortedBy { parseScheduleInstant(it.visitDatetime)?.toEpochMilli() ?: 0L }
                    _ui.value = LabResultsUiState(loading = false, visits = ordered.reversed(), series = buildSeries(ordered))
                }
                .onFailure { _ui.value = LabResultsUiState(loading = false, error = it.message) }
        }
    }

    private fun buildSeries(ordered: List<DoctorVisit>): List<LabMetricSeries> {
        fun stamp(v: DoctorVisit) = v.visitDatetime
        val out = mutableListOf<LabMetricSeries>()

        out += LabMetricSeries(
            "weight", "น้ำหนัก", "Weight", "กก.",
            ordered.mapNotNull { v -> v.weightKg?.let { stamp(v) to it } }
        )
        out += LabMetricSeries(
            "height", "ส่วนสูง", "Height", "ซม.",
            ordered.mapNotNull { v -> v.heightCm?.let { stamp(v) to it } }
        )
        // Paediatric axillary/oral normal band — the one metric with a fixed reference range.
        out += LabMetricSeries(
            "temperature", "อุณหภูมิร่างกาย", "Body temperature", "°C",
            ordered.mapNotNull { v -> v.temperatureC?.let { stamp(v) to it } },
            referenceLow = 36.5, referenceHigh = 37.5
        )
        // blood_pressure is stored as free text ("100/65"); only the systolic half is chartable.
        out += LabMetricSeries(
            "systolic", "ความดัน (ตัวบน)", "Blood pressure (systolic)", "mmHg",
            ordered.mapNotNull { v ->
                v.bloodPressure?.substringBefore('/')?.trim()?.toDoubleOrNull()?.let { stamp(v) to it }
            },
            referenceLow = 80.0, referenceHigh = 115.0
        )

        return out.filter { it.points.isNotEmpty() }
    }
}

/* ------------------------------------------- Patient intake ------------------------------------- */

/** The two ways the owner can attach a guardian to the registration, per the product requirement:
 *  pick an account that already has access to this child, or type in someone who isn't an app user. */
enum class GuardianSource { FROM_APP, MANUAL }

data class PatientIntakeUiState(
    val loading: Boolean = false,
    val doctorProfile: DoctorProfile? = null,
    val doctorName: String? = null,
    /** Existing app accounts with access to the selected child — the FROM_APP picker's options. */
    val appGuardians: List<ChildAccessMember> = emptyList(),
    val submitting: Boolean = false,
    /** Set when an appointment was created (doctor-scoped registration) — navigates to its details. */
    val submittedAppointmentId: String? = null,
    /** Set when a doctor-less registration only saved the patient record. */
    val savedWithoutDoctor: Boolean = false,
    /** After clinic intake, navigate to book with this doctor (no auto appointment). */
    val redirectToReservationDoctorId: String? = null,
    val error: String? = null
)

/** Backs [PatientRegistrationScreen]. Two jobs:
 *  1. **Pedia Connect mode** (no clinic): persist a separate hospital registration in `pediaconnect_*`
 *     — user may pick an app child only to **sync/prefill** the form; `children` is not updated.
 *  2. **Clinic mode** (doctor chosen): same Pedia intake row, then create an `appointments` request;
 *     guardian notes still go on the appointment for the doctor to read. */
class PatientIntakeViewModel : ViewModel() {
    private val doctorRepo = DoctorRepository()
    private val childRepo = ChildRepository()
    private val pediaConnectRepo = PediaConnectRepository()
    private val linkRepo = PediaConnectLinkRepository()

    private val _ui = MutableStateFlow(PatientIntakeUiState())
    val ui: StateFlow<PatientIntakeUiState> = _ui

    private var loadedDoctorId: String? = null
    private var loadedGuardiansFor: String? = null

    fun loadDoctor(doctorId: String?) {
        if (doctorId == null || doctorId == loadedDoctorId) return
        loadedDoctorId = doctorId
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true)
            val dp = doctorRepo.getDoctorProfile(doctorId).getOrNull()
            val name = doctorRepo.searchDoctors(null).getOrDefault(emptyList())
                .firstOrNull { it.first.id == doctorId }?.first?.fullName
            _ui.value = _ui.value.copy(loading = false, doctorProfile = dp, doctorName = name)
        }
    }

    fun loadAppGuardians(childId: String?) {
        if (childId.isNullOrBlank() || childId == loadedGuardiansFor) return
        loadedGuardiansFor = childId
        viewModelScope.launch {
            val members = childRepo.listAccessMembers(childId).getOrDefault(emptyList())
            _ui.value = _ui.value.copy(appGuardians = members)
        }
    }

    fun clearError() { _ui.value = _ui.value.copy(error = null) }

    /**
     * @param doctorId null for a registration started from Pedia Connect (no clinic picked yet) —
     *   then only the patient record is saved and no appointment is requested.
     */
    fun submit(
        doctorId: String?,
        linkedChildId: String,
        name: String,
        birthdate: String,
        gender: String,
        bloodGroup: String?,
        allergies: String?,
        notes: String?,
        guardianBlock: String,
        mainGuardianUserId: String?,
        mainGuardianName: String,
        mainGuardianRelationship: String,
        mainGuardianPhone: String,
        mainGuardianEmail: String
    ) {
        if (_ui.value.submitting) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(submitting = true, error = null)

            pediaConnectRepo.registerIntake(
                childId = linkedChildId,
                name = name,
                birthdate = birthdate,
                gender = gender,
                bloodGroup = bloodGroup,
                allergies = allergies,
                notes = notes,
                mainGuardianUserId = mainGuardianUserId,
                mainGuardianName = mainGuardianName,
                mainGuardianRelationship = mainGuardianRelationship,
                mainGuardianPhone = mainGuardianPhone,
                mainGuardianEmail = mainGuardianEmail
            ).onFailure {
                _ui.value = _ui.value.copy(submitting = false, error = it.message)
                return@launch
            }

            if (doctorId == null) {
                _ui.value = _ui.value.copy(submitting = false, savedWithoutDoctor = true)
                return@launch
            }

            val intakeNotes = buildIntakeNotes(
                guardianBlock, name, birthdate, gender, bloodGroup, allergies, notes
            )
            linkRepo.register(doctorId, linkedChildId, intakeNotes).getOrNull()
            doctorRepo.requestAppointment(doctorId, linkedChildId, slotId = null, reason = "Patient registration")
                .onSuccess { appt ->
                    doctorRepo.updateAppointmentStatus(appt.id, status = "pending", notes = intakeNotes)
                        .onFailure {
                            doctorRepo.updateAppointmentStatus(appt.id, status = "pending", notes = intakeNotes)
                        }
                    _ui.value = _ui.value.copy(submitting = false, redirectToReservationDoctorId = doctorId)
                }
                .onFailure {
                    _ui.value = _ui.value.copy(submitting = false, redirectToReservationDoctorId = doctorId)
                }
        }
    }

    private fun buildIntakeNotes(
        guardianBlock: String,
        name: String,
        birthdate: String,
        gender: String,
        bloodGroup: String?,
        allergies: String?,
        conditions: String?
    ): String = buildString {
        appendLine("=== Pedia Connect registration ===")
        appendLine("Patient: $name")
        appendLine("Birthdate: $birthdate")
        appendLine("Gender: $gender")
        bloodGroup?.takeIf { it.isNotBlank() }?.let { appendLine("Blood group: $it") }
        allergies?.takeIf { it.isNotBlank() }?.let { appendLine("Allergies: $it") }
        conditions?.takeIf { it.isNotBlank() }?.let { appendLine("Conditions: $it") }
        appendLine()
        append(guardianBlock)
    }
}

/* --------------------------------------- Appointment management --------------------------------- */

data class AppointmentManageUiState(
    val loading: Boolean = false,
    val appointment: Appointment? = null,
    val doctorProfile: DoctorProfile? = null,
    val doctorName: String? = null,
    /** Open future slots on the doctor's calendar, grouped by `slot_date` for the date/time pickers. */
    val slots: List<DoctorAppointmentSlot> = emptyList(),
    val working: Boolean = false,
    val done: Boolean = false,
    val error: String? = null
) {
    val dates: List<String> get() = slots.map { it.slotDate }.distinct().sorted()
    fun timesFor(date: String?): List<DoctorAppointmentSlot> =
        slots.filter { it.slotDate == date }.sortedBy { it.startTime }
}

/** Backs [AppointmentManagementScreen] ("แก้ไข/ยกเลิก"). Reschedule reuses the existing
 *  `updateAppointmentStatus(id, status, scheduledAt)` transition the doctor side already uses. */
class AppointmentManageViewModel : ViewModel() {
    private val repo = DoctorRepository()

    private val _ui = MutableStateFlow(AppointmentManageUiState())
    val ui: StateFlow<AppointmentManageUiState> = _ui

    fun load(appointmentId: String) {
        if (_ui.value.appointment?.id == appointmentId) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            repo.getAppointment(appointmentId)
                .onSuccess { appt ->
                    val dp = repo.getDoctorProfile(appt.doctorId).getOrNull()
                    val name = repo.searchDoctors(null).getOrDefault(emptyList())
                        .firstOrNull { it.first.id == appt.doctorId }?.first?.fullName
                    val today = java.time.LocalDate.now().toString()
                    val slots = repo.getSlots(appt.doctorId, fromDate = today).getOrDefault(emptyList())
                    _ui.value = _ui.value.copy(
                        loading = false, appointment = appt, doctorProfile = dp,
                        doctorName = name, slots = slots
                    )
                }
                .onFailure { _ui.value = _ui.value.copy(loading = false, error = it.message) }
        }
    }

    /** slot_date is `yyyy-MM-dd` and start_time `HH:mm[:ss]`; the appointments column is an instant. */
    fun reschedule(slot: DoctorAppointmentSlot) {
        val id = _ui.value.appointment?.id ?: return
        if (_ui.value.working) return
        val scheduledAt = runCatching {
            java.time.LocalDateTime.of(
                java.time.LocalDate.parse(slot.slotDate),
                java.time.LocalTime.parse(slot.startTime.take(5))
            ).atZone(java.time.ZoneId.systemDefault()).toInstant().toString()
        }.getOrNull() ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(working = true, error = null)
            repo.updateAppointmentStatus(id, status = "rescheduled", scheduledAt = scheduledAt)
                .onSuccess { _ui.value = _ui.value.copy(working = false, appointment = it, done = true) }
                .onFailure { _ui.value = _ui.value.copy(working = false, error = it.message) }
        }
    }

    fun confirm() {
        val id = _ui.value.appointment?.id ?: return
        if (_ui.value.working) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(working = true, error = null)
            repo.updateAppointmentStatus(id, status = "confirmed")
                .onSuccess { _ui.value = _ui.value.copy(working = false, appointment = it, done = true) }
                .onFailure { _ui.value = _ui.value.copy(working = false, error = it.message) }
        }
    }

    fun cancel(reason: String) {
        val trimmed = reason.trim()
        if (trimmed.isBlank()) return
        val id = _ui.value.appointment?.id ?: return
        if (_ui.value.working) return
        val existing = _ui.value.appointment?.notes
        val notes = if (existing.isNullOrBlank()) "Cancelled by parent: $trimmed"
        else "${existing.trim()}\n\nCancelled by parent: $trimmed"
        viewModelScope.launch {
            _ui.value = _ui.value.copy(working = true, error = null)
            repo.updateAppointmentStatus(id, status = "cancelled", notes = notes)
                .onSuccess { _ui.value = _ui.value.copy(working = false, appointment = it, done = true) }
                .onFailure { _ui.value = _ui.value.copy(working = false, error = it.message) }
        }
    }
}
