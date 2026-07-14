package com.lullabyai.app.widget

import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.data.repository.MedicationRepository
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.data.repository.SleepRepository
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.util.TimeUtils
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class DailyOverviewSnapshot(
    val childName: String?,
    val lastFeeding: String?,
    val isSleeping: Boolean,
    val lastSleep: String?,
    val sleepMinutesToday: Int,
    val kcalToday: Int,
    /** Of 3 tracked categories (fed today, slept today, food logged today) — used for the
     *  "today's logging" ring. Not a medical "health score" — this app has no such metric,
     *  so the ring shows something real and transparent instead of an invented clinical number. */
    val loggedCategories: Int
) {
    val percentComplete: Int get() = loggedCategories * 100 / 3
}

data class GrowthSnapshot(
    val childName: String?,
    val weightKg: Double?,
    val weightDeltaKg: Double?,
    val heightCm: Double?,
    val heightDeltaCm: Double?
)

data class VaccineSnapshot(
    val childName: String?,
    val vaccineName: String?,
    val daysUntil: Long?
)

data class MilestoneSnapshot(
    val childName: String?,
    val percentAchieved: Int,
    val totalCount: Int,
    val nextMilestoneName: String?
)

data class SleepAnalyticsSnapshot(
    val childName: String?,
    val lastSleepHours: Long?,
    val lastSleepMinutes: Long?
)

data class DoseInfo(
    val id: String,
    val medicineName: String,
    val scheduledTime: String?,
    val taken: Boolean
)

data class MedicineDoseSnapshot(
    val childName: String?,
    val doses: List<DoseInfo>
)

object WidgetDataFetcher {

    suspend fun fetchDailyOverview(): DailyOverviewSnapshot {
        val childId = ChildSelection.current() ?: return DailyOverviewSnapshot(null, null, false, null, 0, 0, 0)
        val child = ChildRepository().getChildren().getOrDefault(emptyList()).find { it.id == childId }
        val milkLogs = NutritionRepository().getMilkLogs(childId)
        val lastMilk = milkLogs.firstOrNull()
        val sleepLogs = SleepRepository().getLogs(childId).getOrDefault(emptyList())
        val lastSleep = sleepLogs.firstOrNull()
        val today = LocalDate.now().toString()

        val sleepMinutesToday = sleepLogs
            .filter { it.endTime != null && TimeUtils.localDate(it.startTime) == today }
            .sumOf { log ->
                val start = TimeUtils.parseInstant(log.startTime)
                val end = TimeUtils.parseInstant(log.endTime)
                if (start != null && end != null) Duration.between(start, end).toMinutes() else 0L
            }.toInt()
        val fedToday = milkLogs.any { TimeUtils.localDate(it.loggedAt) == today }
        val foodLogsToday = NutritionRepository().getFoodLogs(childId, today)
        val kcalToday = foodLogsToday.sumOf { it.calories ?: 0 }

        val loggedCategories = listOf(fedToday, sleepMinutesToday > 0, foodLogsToday.isNotEmpty()).count { it }

        return DailyOverviewSnapshot(
            childName = child?.name,
            lastFeeding = TimeUtils.localTime(lastMilk?.loggedAt),
            isSleeping = lastSleep != null && lastSleep.endTime == null,
            lastSleep = TimeUtils.localTime(lastSleep?.endTime ?: lastSleep?.startTime),
            sleepMinutesToday = sleepMinutesToday,
            kcalToday = kcalToday,
            loggedCategories = loggedCategories
        )
    }

    suspend fun fetchGrowth(): GrowthSnapshot {
        val childId = ChildSelection.current() ?: return GrowthSnapshot(null, null, null, null, null)
        val child = ChildRepository().getChildren().getOrDefault(emptyList()).find { it.id == childId }
        val records = HealthRepository().getGrowthRecords(childId).getOrDefault(emptyList())
        val latest = records.getOrNull(0)
        val previous = records.getOrNull(1)
        return GrowthSnapshot(
            childName = child?.name,
            weightKg = latest?.weightKg,
            weightDeltaKg = if (latest?.weightKg != null && previous?.weightKg != null) latest.weightKg - previous.weightKg else null,
            heightCm = latest?.heightCm,
            heightDeltaCm = if (latest?.heightCm != null && previous?.heightCm != null) latest.heightCm - previous.heightCm else null
        )
    }

    suspend fun fetchVaccine(): VaccineSnapshot {
        val childId = ChildSelection.current() ?: return VaccineSnapshot(null, null, null)
        val child = ChildRepository().getChildren().getOrDefault(emptyList()).find { it.id == childId }
        val today = LocalDate.now()
        val next = HealthRepository().getVaccineRecords(childId).getOrDefault(emptyList())
            .filter { (it.status == "scheduled" || it.status == "upcoming") && it.scheduledDate != null }
            .mapNotNull { record -> runCatching { LocalDate.parse(TimeUtils.localDate(record.scheduledDate)) }.getOrNull()?.let { it to record } }
            .filter { !it.first.isBefore(today) }
            .minByOrNull { it.first }
        return VaccineSnapshot(
            childName = child?.name,
            vaccineName = next?.second?.vaccineName,
            daysUntil = next?.let { ChronoUnit.DAYS.between(today, it.first) }
        )
    }

    suspend fun fetchMilestones(): MilestoneSnapshot {
        val childId = ChildSelection.current() ?: return MilestoneSnapshot(null, 0, 0, null)
        val child = ChildRepository().getChildren().getOrDefault(emptyList()).find { it.id == childId }
        val milestones = ChildRepository().getMilestones(childId).getOrDefault(emptyList())
        val achieved = milestones.count { it.isAchieved }
        return MilestoneSnapshot(
            childName = child?.name,
            percentAchieved = if (milestones.isNotEmpty()) achieved * 100 / milestones.size else 0,
            totalCount = milestones.size,
            nextMilestoneName = milestones.firstOrNull { !it.isAchieved }?.milestoneName
        )
    }

    suspend fun fetchSleepAnalytics(): SleepAnalyticsSnapshot {
        val childId = ChildSelection.current() ?: return SleepAnalyticsSnapshot(null, null, null)
        val child = ChildRepository().getChildren().getOrDefault(emptyList()).find { it.id == childId }
        val lastMainSleep = SleepRepository().getLogs(childId).getOrDefault(emptyList())
            .firstOrNull { it.sleepType == "main" && it.endTime != null }
        val start = TimeUtils.parseInstant(lastMainSleep?.startTime)
        val end = TimeUtils.parseInstant(lastMainSleep?.endTime)
        val duration = if (start != null && end != null) Duration.between(start, end) else null
        return SleepAnalyticsSnapshot(
            childName = child?.name,
            lastSleepHours = duration?.toHours(),
            lastSleepMinutes = duration?.let { it.toMinutes() % 60 }
        )
    }

    suspend fun fetchMedicineDoses(): MedicineDoseSnapshot {
        val childId = ChildSelection.current() ?: return MedicineDoseSnapshot(null, emptyList())
        val child = ChildRepository().getChildren().getOrDefault(emptyList()).find { it.id == childId }
        val medicationRepo = MedicationRepository()
        val schedules = medicationRepo.getSchedules(childId).getOrDefault(emptyList()).associateBy { it.id }
        val today = LocalDate.now()
        val doses = medicationRepo.getDoses(childId).getOrDefault(emptyList())
            .filter { TimeUtils.localDate(it.scheduledAt) == today.toString() }
            .sortedBy { it.scheduledAt }
            .take(2)
            .map { dose ->
                DoseInfo(
                    id = dose.id,
                    medicineName = schedules[dose.scheduleId]?.medicineName ?: "ยา",
                    scheduledTime = TimeUtils.localTime(dose.scheduledAt),
                    taken = dose.status == "taken"
                )
            }
        return MedicineDoseSnapshot(childName = child?.name, doses = doses)
    }
}
