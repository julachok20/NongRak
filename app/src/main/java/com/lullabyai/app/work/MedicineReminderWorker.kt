package com.lullabyai.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.MedicationRepository
import com.lullabyai.app.data.repository.NotifCategory
import com.lullabyai.app.data.repository.NotificationRepository
import com.lullabyai.app.data.settings.SettingsManager
import io.github.jan.supabase.auth.auth
import java.time.LocalDateTime

/**
 * Runs in the background (WorkManager) to remind parents about due medicine doses.
 * Fires a notification for each "pending" dose whose scheduled time has already arrived.
 * Dedupe markers (dose id) guarantee one notification per dose.
 */
class MedicineReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        runCatching { supabase.auth.awaitInitialization() }
        supabase.auth.currentUserOrNull() ?: return Result.success()

        val children = ChildRepository().getChildren().getOrDefault(emptyList())
        if (children.isEmpty()) return Result.success()

        val now = LocalDateTime.now()
        val settings = SettingsManager(applicationContext)
        val alreadyReminded = settings.getMedicineRemindedKeys()
        val notifRepo = NotificationRepository()
        val medicationRepo = MedicationRepository()

        val newlyReminded = mutableSetOf<String>()

        for (child in children) {
            val schedules = medicationRepo.getSchedules(child.id).getOrDefault(emptyList())
            val schedulesByName = schedules.associateBy { it.id }
            val doses = medicationRepo.getDoses(child.id).getOrDefault(emptyList())
            for (dose in doses) {
                if (dose.status != "pending") continue
                val due = runCatching { LocalDateTime.parse(dose.scheduledAt) }.getOrNull() ?: continue
                if (due.isAfter(now)) continue // not due yet
                val key = dose.id
                if (key in alreadyReminded || key in newlyReminded) continue

                val medicineName = schedulesByName[dose.scheduleId]?.medicineName ?: "ยา"
                notifRepo.notify(
                    NotifCategory.SCHEDULE,
                    "ถึงเวลาทานยาแล้ว",
                    "${child.name} ถึงเวลาทาน $medicineName"
                )
                newlyReminded += key
            }
        }

        settings.setMedicineRemindedKeys(alreadyReminded + newlyReminded)
        return Result.success()
    }
}
