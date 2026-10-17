package com.lullabyai.app.util

import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.data.model.MedicationDose
import com.lullabyai.app.data.model.VaccineRecord
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * What the ครอบครัวของเรา card on the home screen says.
 *
 * A PURE function over rows the app already holds - no network, no AI, nothing invented. The card
 * has to show the fact itself rather than "มีข้อมูลสำคัญที่ต้องดูแลวันนี้", which is the anxious
 * way of saying nothing: it tells a parent something is wrong, then makes her tap to find out what.
 *
 * Two rules this file exists to enforce:
 *
 *  * **Nothing appears unless a row says so.** An empty list is a real answer and the card says so
 *    plainly. A cheerful invented day sitting in a health app is a fabricated record.
 *  * **No judgement.** Every item is a fact with a time on it. "ยามื้อ 14:00 ยังไม่ได้ให้" is
 *    something the rows prove; "ลูกอาจจะไม่สบาย" is a diagnosis and belongs nowhere near this file.
 */
enum class TodayKind {
    /** A scheduled dose whose time has passed and which is still pending. Always sorts first. */
    MEDICINE_OVERDUE,

    /** A dose still to come today. */
    MEDICINE_DUE,

    /** An illness is being tracked and today has no symptom entry yet. */
    SYMPTOM_LOG,

    /** The nearest scheduled vaccine not yet given. */
    VACCINE,

    /** Already done today. Last, because seeing what she HAS done is half the reassurance. */
    DONE_MEDICINE
}

/**
 * One line on the card.
 *
 * Carries data, never a sentence: the screen formats Thai or English from these fields, which keeps
 * this file testable and free of copy.
 */
data class TodayItem(
    val kind: TodayKind,
    val childId: String,
    val childName: String,
    /** "HH:mm" for a dose. Null for anything with no clock time. */
    val time: String? = null,
    /** ISO date (yyyy-MM-dd) for a vaccine. */
    val date: String? = null,
    /** Which day of a tracked illness this is, 1-based. */
    val dayNumber: Int? = null,
    /** The vaccine name, or the illness title. */
    val label: String? = null
)

object TodayPlan {

    /**
     * Today's lines for one child.
     *
     * [now] is injected so this is testable at a fixed instant rather than at whatever time the
     * suite happens to run: the overdue/due split is entirely a function of the clock, and a test
     * that only passes in the afternoon is worse than no test at all.
     */
    fun forChild(
        child: Child,
        doses: List<MedicationDose>,
        vaccines: List<VaccineRecord>,
        healthRecords: List<HealthRecord>,
        /** record id to the date of its latest symptom entry, as ChildUiState already holds it. */
        symptomActivityByRecord: Map<String, String> = emptyMap(),
        now: LocalDateTime = LocalDateTime.now()
    ): List<TodayItem> {
        val today = now.toLocalDate()
        val items = mutableListOf<TodayItem>()

        doses.asSequence()
            .filter { it.childId == child.id }
            .mapNotNull { dose -> parseLocal(dose.scheduledAt)?.let { dose to it } }
            .filter { it.second.toLocalDate() == today }
            .sortedBy { it.second }
            .forEach { (dose, at) ->
                val time = "%02d:%02d".format(at.hour, at.minute)
                when {
                    dose.status == "taken" ->
                        items += TodayItem(TodayKind.DONE_MEDICINE, child.id, child.name, time = time)
                    // "skipped" is a decision the parent already made. Listing it as outstanding
                    // would nag about something they deliberately chose not to give.
                    dose.status == "skipped" -> Unit
                    at.isBefore(now) ->
                        items += TodayItem(TodayKind.MEDICINE_OVERDUE, child.id, child.name, time = time)
                    else ->
                        items += TodayItem(TodayKind.MEDICINE_DUE, child.id, child.name, time = time)
                }
            }

        // An illness being tracked, with nothing written for today yet.
        healthRecords
            .filter { it.childId == child.id && it.status == "monitoring" }
            .forEach { record ->
                if (symptomActivityByRecord[record.id] == today.toString()) return@forEach
                val started = (record.recordedAt ?: record.createdAt)
                    ?.let { TimeUtils.localDate(it) }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                items += TodayItem(
                    kind = TodayKind.SYMPTOM_LOG,
                    childId = child.id,
                    childName = child.name,
                    dayNumber = started
                        ?.let { (ChronoUnit.DAYS.between(it, today) + 1).toInt() }
                        ?.takeIf { it >= 1 },
                    label = record.title.takeIf { it.isNotBlank() }
                )
            }

        // The nearest vaccine still owed - not every future one. Six dates a year out is a wall,
        // not a reminder.
        vaccines.asSequence()
            .filter { it.childId == child.id && it.status != "completed" && !it.scheduledDate.isNullOrBlank() }
            .mapNotNull { v -> runCatching { LocalDate.parse(v.scheduledDate) }.getOrNull()?.let { v to it } }
            .filter { !it.second.isBefore(today) }
            .minByOrNull { it.second }
            ?.let { (vaccine, date) ->
                items += TodayItem(
                    kind = TodayKind.VACCINE,
                    childId = child.id,
                    childName = child.name,
                    date = date.toString(),
                    label = vaccine.vaccineName.takeIf { it.isNotBlank() }
                )
            }

        return items.sortedBy { it.kind.ordinal }
    }

    /**
     * Every child's lines, worst first.
     *
     * The card is called ครอบครัวของเรา, so with more than one child it shows the family rather
     * than whichever child happens to be selected. An overdue dose for the second child must never
     * be hidden behind a profile switch.
     */
    fun forFamily(
        children: List<Child>,
        doses: List<MedicationDose>,
        vaccines: List<VaccineRecord>,
        healthRecords: List<HealthRecord>,
        symptomActivityByRecord: Map<String, String> = emptyMap(),
        now: LocalDateTime = LocalDateTime.now()
    ): List<TodayItem> = children
        .flatMap { child ->
            forChild(child, doses, vaccines, healthRecords, symptomActivityByRecord, now)
        }
        .sortedBy { it.kind.ordinal }

    /** True when nothing is outstanding - only finished things, or nothing at all. */
    fun isClear(items: List<TodayItem>): Boolean =
        items.none { it.kind != TodayKind.DONE_MEDICINE }

    /**
     * The stored instant as this device's wall clock.
     *
     * Goes through TimeUtils and nothing else. Every dose row is written with TimeUtils.toUtcIso
     * (MedicationRepository), and TimeUtils reads an offset-less string as UTC - so a second
     * interpretation here would make the same string mean two different times depending on which
     * file read it. An earlier draft of this function fell back to parsing a naive string as LOCAL,
     * which would have put a 14:00 dose at 21:00 in Bangkok.
     */
    private fun parseLocal(iso: String?): LocalDateTime? =
        TimeUtils.parseInstant(iso)?.let { LocalDateTime.ofInstant(it, ZoneId.systemDefault()) }
}
