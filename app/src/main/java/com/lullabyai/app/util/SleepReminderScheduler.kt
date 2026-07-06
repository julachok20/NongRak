package com.lullabyai.app.util

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lullabyai.app.NongRakApp
import com.lullabyai.app.data.model.SleepSchedule
import com.lullabyai.app.data.repository.NotifCategory
import com.lullabyai.app.data.repository.NotificationRepository
import com.lullabyai.app.data.repository.SleepRepository
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * Reminder-only, per the product decision: this posts a notification at the scheduled sleep time
 * and does NOT create a sleep_logs row automatically — the parent still taps "start" in the app
 * themselves, so a running-late parent never ends up with a wrong auto-created log entry.
 *
 * Recurring schedules are represented as one delayed WorkManager job per next occurrence, which
 * re-schedules itself for the following applicable day right after it fires — a periodic work
 * request would either drift or not survive a daily-time-of-day requirement cleanly.
 */
object SleepReminderScheduler {
    private const val DAY_KEYS_ORDERED = "MON,TUE,WED,THU,FRI,SAT,SUN"
    private fun workName(scheduleId: String) = "sleep_reminder_$scheduleId"

    fun scheduleNext(schedule: SleepSchedule) {
        val ctx = NongRakApp.instance
        val delayMs = millisUntilNextOccurrence(schedule) ?: return
        val request = OneTimeWorkRequestBuilder<SleepReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(
                workDataOf(
                    "scheduleId" to schedule.id,
                    "label" to schedule.label,
                    "sleepType" to schedule.sleepType
                )
            )
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(workName(schedule.id), ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(scheduleId: String) {
        WorkManager.getInstance(NongRakApp.instance).cancelUniqueWork(workName(scheduleId))
    }

    /** Re-arms every active schedule — called on app start, since a fresh install/reboot has
     *  nothing enqueued yet (WorkManager persists existing jobs across reboots on its own, but
     *  there's nothing to persist the very first time a schedule is created before this ran). */
    suspend fun rescheduleAll(childId: String) {
        SleepRepository().getSchedules(childId).onSuccess { schedules ->
            schedules.forEach { scheduleNext(it) }
        }
    }

    /** Null when [schedule] has no valid day/time to compute from, or matches no day at all. */
    private fun millisUntilNextOccurrence(schedule: SleepSchedule): Long? {
        val time = runCatching {
            val parts = schedule.startTime.split(":")
            LocalTime.of(parts[0].toInt(), parts[1].toInt())
        }.getOrNull() ?: return null

        val allowedDays: Set<DayOfWeek> = if (schedule.daysOfWeek.isBlank() || schedule.daysOfWeek == "ALL") {
            DayOfWeek.entries.toSet()
        } else {
            val keys = DAY_KEYS_ORDERED.split(",")
            schedule.daysOfWeek.split(",").mapNotNull { key ->
                val idx = keys.indexOf(key.trim().uppercase())
                if (idx >= 0) DayOfWeek.of(idx + 1) else null
            }.toSet()
        }
        if (allowedDays.isEmpty()) return null

        val now = LocalDateTime.now(ZoneId.systemDefault())
        for (offset in 0..7) {
            val candidateDate = now.toLocalDate().plusDays(offset.toLong())
            if (candidateDate.dayOfWeek !in allowedDays) continue
            val candidate = LocalDateTime.of(candidateDate, time)
            if (candidate.isAfter(now)) {
                return java.time.Duration.between(now, candidate).toMillis()
            }
        }
        return null
    }
}

class SleepReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val scheduleId = inputData.getString("scheduleId") ?: return Result.failure()
        val label = inputData.getString("label").orEmpty()
        val sleepType = inputData.getString("sleepType") ?: "main"

        // Defensive existence check — cancel() should already remove this job on delete/edit, but
        // WorkManager cancellation isn't instantaneous, so re-verify before notifying about
        // something that may no longer exist.
        val childId = com.lullabyai.app.data.settings.ChildSelection.current()
        val stillExists = childId != null &&
            SleepRepository().getSchedules(childId).getOrDefault(emptyList()).any { it.id == scheduleId }
        if (!stillExists) return Result.success()

        val isNap = sleepType == "nap"
        val title = if (label.isNotBlank()) label else if (isNap) "ถึงเวลางีบ" else "ถึงเวลานอน"
        val body = if (isNap) "ถึงเวลางีบตามตารางแล้ว แตะเพื่อเริ่มติดตามการนอน" else "ถึงเวลานอนตามตารางแล้ว แตะเพื่อเริ่มติดตามการนอน"
        NotificationRepository().notify(NotifCategory.SCHEDULE, title, body)

        // Reschedule the next occurrence (next week, or the next applicable day).
        childId?.let { cid ->
            SleepRepository().getSchedules(cid).getOrDefault(emptyList())
                .firstOrNull { it.id == scheduleId }
                ?.let { SleepReminderScheduler.scheduleNext(it) }
        }
        return Result.success()
    }
}
