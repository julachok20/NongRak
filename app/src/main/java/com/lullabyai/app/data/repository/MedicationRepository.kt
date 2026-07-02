package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.MedicationDose
import com.lullabyai.app.data.model.MedicationSchedule
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.util.TimeUtils
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class MedicationRepository {

    // Doses-per-day clock times, keyed by frequency label.
    private fun dailyTimes(frequency: String?): List<LocalTime> = when (frequency) {
        "every_4h" -> listOf(6, 10, 14, 18, 22).map { LocalTime.of(it, 0) }
        "every_6h" -> listOf(8, 14, 20).map { LocalTime.of(it, 0) }
        "every_8h" -> listOf(8, 16).map { LocalTime.of(it, 0) }
        "morning_evening" -> listOf(LocalTime.of(8, 0), LocalTime.of(20, 0))
        "once_daily" -> listOf(LocalTime.of(8, 0))
        else -> listOf(LocalTime.of(8, 0))
    }

    /** Builds the concrete dose timestamps for a schedule based on its type + frequency. */
    private fun generateDoseTimes(schedule: MedicationSchedule): List<LocalDateTime> {
        val times = dailyTimes(schedule.frequency)
        return when (schedule.scheduleType) {
            "times" -> {
                // Fixed number of doses, taken back-to-back starting today at the frequency's clock times.
                val total = schedule.numDoses ?: times.size
                (0 until total).map { i ->
                    val day = i / times.size
                    val time = times[i % times.size]
                    LocalDate.now().plusDays(day.toLong()).atTime(time)
                }
            }
            "date_range" -> {
                val start = schedule.startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
                val end = schedule.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: start
                generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }
                    .flatMap { day -> times.map { day.atTime(it) } }
                    .toList()
            }
            else -> { // "until_gone" — open-ended; seed a rolling 14-day window
                (0 until 14L).flatMap { day -> times.map { LocalDate.now().plusDays(day).atTime(it) } }
            }
        }
    }

    suspend fun createSchedule(
        childId: String, medicineName: String, dosage: Double?, dosageUnit: String, frequency: String,
        scheduleType: String, startDate: String?, endDate: String?, numDoses: Int?, notes: String?
    ): Result<MedicationSchedule> = runCatching {
        val schedule = supabase.from("medication_schedules").insert(
            buildJsonObject {
                put("child_id", JsonPrimitive(childId))
                put("medicine_name", JsonPrimitive(medicineName))
                dosage?.let { put("dosage", JsonPrimitive(it)) }
                put("dosage_unit", JsonPrimitive(dosageUnit))
                put("frequency", JsonPrimitive(frequency))
                put("schedule_type", JsonPrimitive(scheduleType))
                startDate?.let { put("start_date", JsonPrimitive(it)) }
                endDate?.let { put("end_date", JsonPrimitive(it)) }
                numDoses?.let { put("num_doses", JsonPrimitive(it)) }
                notes?.let { put("notes", JsonPrimitive(it)) }
            }
        ) { select() }.decodeSingle<MedicationSchedule>()

        val doseRows = generateDoseTimes(schedule).map { time ->
            buildJsonObject {
                put("schedule_id", JsonPrimitive(schedule.id))
                put("child_id", JsonPrimitive(childId))
                put("scheduled_at", JsonPrimitive(TimeUtils.toUtcIso(time)))
            }
        }
        if (doseRows.isNotEmpty()) supabase.from("medication_doses").insert(doseRows)
        schedule
    }

    suspend fun getSchedules(childId: String): Result<List<MedicationSchedule>> = runCatching {
        supabase.from("medication_schedules").select {
            filter { eq("child_id", childId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<MedicationSchedule>()
    }

    suspend fun deleteSchedule(id: String): Result<Unit> = runCatching {
        supabase.from("medication_schedules").delete { filter { eq("id", id) } }
    }

    /** All doses for a child, most recent first — used for the today/history timeline. */
    suspend fun getDoses(childId: String): Result<List<MedicationDose>> = runCatching {
        supabase.from("medication_doses").select {
            filter { eq("child_id", childId) }
            order("scheduled_at", Order.DESCENDING)
        }.decodeList<MedicationDose>()
    }

    suspend fun markDoseTaken(id: String): Result<Unit> = runCatching {
        supabase.from("medication_doses").update(
            buildJsonObject {
                put("status", JsonPrimitive("taken"))
                put("taken_at", JsonPrimitive(TimeUtils.toUtcIso(LocalDateTime.now())))
            }
        ) { filter { eq("id", id) } }
        // Best-effort: bump the parent schedule's doses_taken counter.
        runCatching {
            val dose = supabase.from("medication_doses").select { filter { eq("id", id) } }.decodeSingle<MedicationDose>()
            val schedule = supabase.from("medication_schedules").select { filter { eq("id", dose.scheduleId) } }.decodeSingle<MedicationSchedule>()
            supabase.from("medication_schedules").update(
                buildJsonObject { put("doses_taken", JsonPrimitive(schedule.dosesTaken + 1)) }
            ) { filter { eq("id", schedule.id) } }
        }
    }

    suspend fun skipDose(id: String): Result<Unit> = runCatching {
        supabase.from("medication_doses").update(
            buildJsonObject { put("status", JsonPrimitive("skipped")) }
        ) { filter { eq("id", id) } }
    }
}
