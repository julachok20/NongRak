package com.lullabyai.app.data

import com.lullabyai.app.data.model.DoctorVisit
import com.lullabyai.app.data.model.GrowthRecord
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.data.model.SleepLog
import com.lullabyai.app.data.model.VaccineRecord
import com.lullabyai.app.data.model.WasteRecord

/**
 * One timeline of everything logged for a child, newest first.
 *
 * The app writes a child's history into half a dozen tables that share no shape and no timestamp
 * column — `recorded_at` here, `start_time` there, `visit_datetime`, `date_given`. This flattens
 * them into one sortable list so a parent can see what was written last without having to know
 * which screen wrote it.
 *
 * Pure: callers pass rows they already hold, so building the feed costs no round trip.
 */
object ChildRecordFeed {

    enum class Kind { GROWTH, HEALTH, SLEEP, WASTE, VISIT, VACCINE }

    data class Entry(
        val kind: Kind,
        val title: String,
        val detail: String,
        /** Normalised to yyyy-MM-ddTHH:mm:ss so mixed sources sort against each other. */
        val at: String
    ) {
        val date: String get() = at.take(10)
    }

    /**
     * A date-only stamp has to become a real instant before it can be compared with a datetime,
     * or "2026-09-20" sorts before everything that happened on 2026-09-20. Midnight is the honest
     * reading: the row says which day, not which minute.
     */
    internal fun normalise(raw: String?): String? {
        val v = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val t = v.replace(' ', 'T')
        return when {
            t.length == 10 -> "${t}T00:00:00"
            t.length >= 19 -> t.take(19)
            else -> t.padEnd(19, '0')
        }
    }

    fun build(
        growth: List<GrowthRecord> = emptyList(),
        health: List<HealthRecord> = emptyList(),
        sleep: List<SleepLog> = emptyList(),
        waste: List<WasteRecord> = emptyList(),
        visits: List<DoctorVisit> = emptyList(),
        vaccines: List<VaccineRecord> = emptyList(),
        isThai: Boolean = true
    ): List<Entry> {
        val out = mutableListOf<Entry>()

        growth.forEach { r ->
            val at = normalise(r.recordedAt) ?: return@forEach
            out += Entry(
                Kind.GROWTH,
                if (isThai) "การเติบโต" else "Growth",
                listOfNotNull(
                    r.weightKg?.let { "%.1f".format(it) + if (isThai) " กก." else " kg" },
                    r.heightCm?.let { "%.1f".format(it) + if (isThai) " ซม." else " cm" }
                ).joinToString(" • "),
                at
            )
        }

        health.forEach { r ->
            val at = normalise(r.recordedAt ?: r.createdAt) ?: return@forEach
            out += Entry(
                Kind.HEALTH,
                r.title.ifBlank { if (isThai) "บันทึกสุขภาพ" else "Health record" },
                listOfNotNull(
                    r.temperature?.let { "%.1f°C".format(it) },
                    r.description?.takeIf { it.isNotBlank() }
                ).joinToString(" • "),
                at
            )
        }

        sleep.forEach { r ->
            val at = normalise(r.startTime) ?: return@forEach
            out += Entry(
                Kind.SLEEP,
                if (r.sleepType == "nap") {
                    if (isThai) "งีบกลางวัน" else "Nap"
                } else {
                    if (isThai) "นอนกลางคืน" else "Night sleep"
                },
                listOfNotNull(r.mood, r.notes?.takeIf { it.isNotBlank() }).joinToString(" • "),
                at
            )
        }

        waste.forEach { r ->
            val at = normalise(r.recordedAt) ?: return@forEach
            out += Entry(
                Kind.WASTE,
                if (r.wasteType == "urine") {
                    if (isThai) "ปัสสาวะ" else "Wee"
                } else {
                    if (isThai) "อุจจาระ" else "Poo"
                },
                listOfNotNull(r.consistency, r.color).joinToString(" • "),
                at
            )
        }

        visits.forEach { r ->
            val at = normalise(r.visitDatetime) ?: return@forEach
            out += Entry(
                Kind.VISIT,
                if (isThai) "พบแพทย์" else "Doctor visit",
                r.doctorName.orEmpty(),
                at
            )
        }

        vaccines.forEach { r ->
            // Only doses actually given are history; a scheduled date is a plan, not a record.
            val at = normalise(r.dateGiven) ?: return@forEach
            out += Entry(
                Kind.VACCINE,
                if (isThai) "วัคซีน" else "Vaccine",
                r.vaccineName,
                at
            )
        }

        return out.sortedByDescending { it.at }
    }

    /** The handful shown inline, with the rest behind "see all". */
    fun recent(entries: List<Entry>, count: Int = 5): List<Entry> = entries.take(count)
}
