package com.lullabyai.app.data.brief

import com.lullabyai.app.data.repository.DoctorVisitRepository
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.data.repository.MedicationRepository
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.util.TimeUtils
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/** What the companion is warning about. The UI maps this to a mascot pose. */
enum class BriefKind { MEDICINE, VACCINE, APPOINTMENT, NONE }

/**
 * The single thing worth saying when the parent opens the app.
 *
 * [headline] is the one-line chip on หน้าหลัก; [detail] is the fuller sentence the companion
 * speaks on its own screen. Both are pre-rendered Thai — no AI call, so the greeting is on
 * screen instantly and costs nothing.
 */
data class ProactiveBrief(
    val kind: BriefKind,
    val headline: String,
    val detail: String
) {
    companion object {
        /** Nothing pending. */
        val GREETING = ProactiveBrief(
            BriefKind.NONE,
            "สวัสดีค่ะ",
            "สวัสดีค่ะ"
        )
    }
}

/**
 * Picks the one most urgent thing across medicine doses, vaccines, and doctor appointments.
 *
 * Each source has its own window, deliberately: an overdue dose from this morning matters, a
 * vaccine overdue by two months does not belong in a greeting. Inside those windows the
 * earliest item wins, so "เลยเวลากินยา" beats "อีก 3 วันฉีดวัคซีน".
 *
 * ponytail: no missed-log detection yet — that needs a nutrition + sleep round trip for a
 * softer nudge. Add when the three hard reminders prove not to be enough.
 */
class ProactiveBriefUseCase(
    private val medicationRepo: MedicationRepository = MedicationRepository(),
    private val healthRepo: HealthRepository = HealthRepository(),
    private val visitRepo: DoctorVisitRepository = DoctorVisitRepository()
) {

    private data class Candidate(val at: Instant, val brief: ProactiveBrief)

    suspend fun load(childName: String? = null, now: Instant = Instant.now()): ProactiveBrief {
        val childId = ChildSelection.current() ?: return ProactiveBrief.GREETING
        val who = childName?.takeIf { it.isNotBlank() } ?: "ลูก"

        val candidates = buildList {
            addAll(medicineCandidates(childId, who, now))
            addAll(vaccineCandidates(childId, who, now))
            addAll(appointmentCandidates(childId, who, now))
        }
        return candidates.minByOrNull { it.at }?.brief ?: ProactiveBrief.GREETING
    }

    private suspend fun medicineCandidates(childId: String, who: String, now: Instant): List<Candidate> {
        val doses = medicationRepo.getDoses(childId).getOrDefault(emptyList())
        if (doses.isEmpty()) return emptyList()
        val names = medicationRepo.getSchedules(childId).getOrDefault(emptyList())
            .associate { it.id to it.medicineName }

        val from = now.minus(MEDICINE_OVERDUE_WINDOW)
        val to = now.plus(MEDICINE_AHEAD_WINDOW)

        return doses.mapNotNull { dose ->
            if (dose.status != "pending") return@mapNotNull null
            val due = TimeUtils.parseInstant(dose.scheduledAt) ?: return@mapNotNull null
            if (due.isBefore(from) || due.isAfter(to)) return@mapNotNull null

            val medicine = names[dose.scheduleId] ?: "ยา"
            val rel = relativeThai(now, due)
            val brief = if (due.isAfter(now)) {
                ProactiveBrief(
                    BriefKind.MEDICINE,
                    "อีก $rel ถึงเวลากินยา",
                    "อีก $rel ถึงเวลาที่${who}ต้องกิน$medicine แล้วนะคะ เตรียมไว้ก่อนได้เลยค่ะ"
                )
            } else {
                ProactiveBrief(
                    BriefKind.MEDICINE,
                    "เลยเวลากินยาแล้ว",
                    "${who}เลยเวลากิน$medicine มา $rel แล้วค่ะ กินแล้วหรือยังคะ"
                )
            }
            Candidate(due, brief)
        }
    }

    private suspend fun vaccineCandidates(childId: String, who: String, now: Instant): List<Candidate> {
        val today = LocalDate.ofInstant(now, ZoneId.systemDefault())
        return healthRepo.getVaccineRecords(childId).getOrDefault(emptyList()).mapNotNull { v ->
            if (v.status == "completed" || v.status == "skipped") return@mapNotNull null
            val due = v.scheduledDate?.take(10)
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(today, due)
            if (days < 0 || days > VACCINE_AHEAD_DAYS) return@mapNotNull null

            val name = v.vaccineName.ifBlank { "วัคซีน" }
            val brief = ProactiveBrief(
                BriefKind.VACCINE,
                "${dayPhrase(days)}ต้องฉีดวัคซีน",
                "${dayPhrase(days)}${who}มีนัดฉีด$name ค่ะ อย่าลืมเตรียมสมุดวัคซีนไปด้วยนะคะ"
            )
            // Midday, so a same-day vaccine sorts after a dose that was due this morning
            // but before one due tonight.
            Candidate(due.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant(), brief)
        }
    }

    private suspend fun appointmentCandidates(childId: String, who: String, now: Instant): List<Candidate> {
        val to = now.plus(APPOINTMENT_AHEAD_WINDOW)
        val today = LocalDate.ofInstant(now, ZoneId.systemDefault())

        return visitRepo.getVisits(childId).getOrDefault(emptyList()).mapNotNull { visit ->
            val at = TimeUtils.parseInstant(visit.visitDatetime) ?: return@mapNotNull null
            if (at.isBefore(now) || at.isAfter(to)) return@mapNotNull null

            val days = ChronoUnit.DAYS.between(today, LocalDate.ofInstant(at, ZoneId.systemDefault()))
            val place = visit.facilityName?.takeIf { it.isNotBlank() }?.let { " ที่$it" } ?: ""
            val brief = ProactiveBrief(
                BriefKind.APPOINTMENT,
                "${dayPhrase(days)}มีนัดหมอ",
                "${dayPhrase(days)}${who}มีนัดพบแพทย์$place ค่ะ"
            )
            Candidate(at, brief)
        }
    }

    companion object {
        private val MEDICINE_OVERDUE_WINDOW: Duration = Duration.ofHours(12)
        private val MEDICINE_AHEAD_WINDOW: Duration = Duration.ofHours(8)
        private const val VACCINE_AHEAD_DAYS = 7L
        private val APPOINTMENT_AHEAD_WINDOW: Duration = Duration.ofDays(7)
    }
}

/**
 * Distance between two instants as a bare Thai quantity — "30 นาที", "3 ชั่วโมง", "2 วัน".
 *
 * Direction-free on purpose: the caller wraps it in "อีก ..." or "มา ... แล้ว", so past and
 * future share one formatter.
 */
internal fun relativeThai(from: Instant, to: Instant): String {
    val minutes = abs(Duration.between(from, to).toMinutes())
    return when {
        minutes < 1L -> "ไม่ถึงนาที"
        minutes < 60L -> "$minutes นาที"
        minutes < 24 * 60L -> "${minutes / 60} ชั่วโมง"
        else -> "${minutes / (24 * 60)} วัน"
    }
}

/** Day-level phrasing for things scheduled by date rather than by clock time. */
internal fun dayPhrase(days: Long): String = when (days) {
    0L -> "วันนี้"
    1L -> "พรุ่งนี้"
    else -> "อีก $days วัน"
}
