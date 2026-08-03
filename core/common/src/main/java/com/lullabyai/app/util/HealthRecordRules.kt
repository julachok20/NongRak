package com.lullabyai.app.util

import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.data.model.SymptomLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Batch Q standing rules #7 / #8 / #9 (+ F3 refinement) for health / sickness records.
 * Pure helpers — no I/O. Prefer deriving UI state here over writing to the DB.
 */
object HealthRecordRules {

    const val EDIT_LOCK_MINUTES = 10L
    const val AUTO_RESOLVE_DAYS = 7L

    /** Display status used by history badges (includes derived auto-resolve). */
    enum class DisplayStatus { MONITORING, RECOVERED, UNUSED, AUTO_RESOLVED }

    /** Rule #7 — older than 1 calendar month (from today). */
    fun isOlderThanOneMonth(record: HealthRecord, today: LocalDate = LocalDate.now()): Boolean {
        val date = recordDate(record) ?: return false
        return date.isBefore(today.minusMonths(1))
    }

    /**
     * F3 — content edits lock [EDIT_LOCK_MINUTES] after the **last symptom record/update**,
     * not merely whole-record [HealthRecord.createdAt] when that differs.
     *
     * Origin (newest wins):
     * 1. [lastSymptomUpdateIso] from day-logs (created_at / updated_at)
     * 2. [HealthRecord.symptomsUpdatedAt]
     * 3. [HealthRecord.updatedAt] (detail-screen symptom edits stamp this)
     * 4. [HealthRecord.createdAt] (symptoms recorded at create)
     */
    fun isEditLocked(
        record: HealthRecord,
        lastSymptomUpdateIso: String? = null,
        now: Instant = Instant.now()
    ): Boolean {
        val origin = editLockOrigin(record, lastSymptomUpdateIso) ?: return false
        return ChronoUnit.MINUTES.between(origin, now) >= EDIT_LOCK_MINUTES
    }

    fun editLockOrigin(record: HealthRecord, lastSymptomUpdateIso: String? = null): Instant? {
        val candidates = listOfNotNull(
            lastSymptomUpdateIso,
            record.symptomsUpdatedAt,
            record.updatedAt,
            record.createdAt
        )
        return candidates.mapNotNull { TimeUtils.parseInstant(it) }.maxOrNull()
    }

    /** True when a specific day-log is past the 10-min edit window (from its own last write). */
    fun isSymptomLogEditLocked(log: SymptomLog, now: Instant = Instant.now()): Boolean {
        val origin = listOfNotNull(log.updatedAt, log.createdAt)
            .mapNotNull { TimeUtils.parseInstant(it) }
            .maxOrNull() ?: return false
        return ChronoUnit.MINUTES.between(origin, now) >= EDIT_LOCK_MINUTES
    }

    /** Rule #8 — edited during the window (persisted via [HealthRecord.updatedAt]). */
    fun isEdited(record: HealthRecord): Boolean = !record.updatedAt.isNullOrBlank()

    /**
     * Rule #9 — monitoring episode with no activity for [AUTO_RESOLVE_DAYS] consecutive days
     * is shown as resolved. Does NOT delete or rewrite the row.
     *
     * [lastActivityIso] = latest symptom-log date/timestamp for this episode when known;
     * falls back to created_at / recorded_at on the parent record.
     */
    fun displayStatus(record: HealthRecord, lastActivityIso: String? = null, today: LocalDate = LocalDate.now()): DisplayStatus {
        when (record.status) {
            "unused" -> return DisplayStatus.UNUSED
            "recovered" -> return DisplayStatus.RECOVERED
        }
        if (record.status == "monitoring" && isAutoResolved(record, lastActivityIso, today)) {
            return DisplayStatus.AUTO_RESOLVED
        }
        return DisplayStatus.MONITORING
    }

    fun isAutoResolved(record: HealthRecord, lastActivityIso: String? = null, today: LocalDate = LocalDate.now()): Boolean {
        if (record.status != "monitoring") return false
        val last = activityDate(record, lastActivityIso) ?: return false
        return ChronoUnit.DAYS.between(last, today) >= AUTO_RESOLVE_DAYS
    }

    /**
     * Rule #9 (per-symptom) — a named symptom with no active sighting for
     * [AUTO_RESOLVE_DAYS] days is assumed resolved (UI-only).
     */
    fun isSymptomAutoResolved(lastActiveDate: LocalDate?, today: LocalDate = LocalDate.now()): Boolean {
        if (lastActiveDate == null) return false
        return ChronoUnit.DAYS.between(lastActiveDate, today) >= AUTO_RESOLVE_DAYS
    }

    fun recordDate(record: HealthRecord): LocalDate? {
        val iso = record.recordedAt ?: record.createdAt ?: return null
        return TimeUtils.localDate(iso)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    private fun activityDate(record: HealthRecord, lastActivityIso: String?): LocalDate? {
        val candidates = listOfNotNull(
            lastActivityIso,
            record.symptomsUpdatedAt,
            record.createdAt,
            record.recordedAt
        )
        return candidates.mapNotNull { iso ->
            TimeUtils.localDate(iso)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: TimeUtils.parseInstant(iso)?.atZone(ZoneId.systemDefault())?.toLocalDate()
        }.maxOrNull()
    }
}
