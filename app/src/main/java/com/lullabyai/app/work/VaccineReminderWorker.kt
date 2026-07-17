package com.lullabyai.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.data.repository.NotifCategory
import com.lullabyai.app.data.repository.NotificationRepository
import com.lullabyai.app.data.settings.SettingsManager
import io.github.jan.supabase.auth.auth
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Runs in the background (WorkManager) to remind parents about upcoming vaccine appointments.
 * Fires a notification 3, 2, 1 days before, and on the due day for each scheduled (not
 * completed/skipped) dose. Dedupe markers ("vaccineId:offset") guarantee one notification per offset.
 */
class VaccineReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val offsets = listOf(3, 2, 1, 0)   // days-before to notify

    // ponytail: static text, not per-vaccine-specific. Same wording already vetted in the
    // research_chunks RAG corpus ("การดูแลหลังฉีดวัคซีน"). Upgrade to RAG-generated per-vaccine
    // text later if the generic warning turns out not to be enough.
    private val postVaccineSymptomWarning =
        " หลังฉีดวัคซีนอาจมีไข้ต่ำและปวดบริเวณที่ฉีด ซึ่งเป็นเรื่องปกติและหายได้เองใน 1-2 วัน " +
            "เตรียมเช็ดตัวลดไข้และให้ดื่มน้ำมากๆ ไว้ล่วงหน้า หากไข้สูงควรพบแพทย์"

    override suspend fun doWork(): Result {
        // Restore the persisted session (the process may have been cold-started by WorkManager).
        runCatching { supabase.auth.awaitInitialization() }
        supabase.auth.currentUserOrNull() ?: return Result.success()   // not logged in → nothing to do

        val children = ChildRepository().getChildren().getOrDefault(emptyList())
        if (children.isEmpty()) return Result.success()

        val today = LocalDate.now()
        val settings = SettingsManager(applicationContext)
        val alreadyReminded = settings.getRemindedKeys()
        val notifRepo = NotificationRepository()
        val healthRepo = HealthRepository()

        val newlyReminded = mutableSetOf<String>()
        val upcomingIds = mutableSetOf<String>()   // to prune stale dedupe keys at the end

        for (child in children) {
            val vaccines = healthRepo.getVaccineRecords(child.id).getOrDefault(emptyList())
            for (v in vaccines) {
                if (v.status == "completed" || v.status == "skipped") continue
                val due = v.scheduledDate?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: continue
                val days = ChronoUnit.DAYS.between(today, due).toInt()
                if (days < 0) continue
                upcomingIds += v.id
                if (days !in offsets) continue
                val key = "${v.id}:$days"
                if (key in alreadyReminded || key in newlyReminded) continue

                val whenText = when (days) {
                    0 -> "วันนี้"
                    1 -> "พรุ่งนี้"
                    else -> "อีก $days วัน"
                }
                val warning = if (days == 2 || days == 1) postVaccineSymptomWarning else ""
                notifRepo.notify(
                    NotifCategory.SCHEDULE,
                    "เตือนนัดวัคซีน",
                    "$whenText (${due}) ${child.name} มีนัดวัคซีน ${v.vaccineName}$warning"
                )
                newlyReminded += key
            }
        }

        // Keep only dedupe keys for still-upcoming vaccines so the set doesn't grow unbounded.
        val merged = (alreadyReminded + newlyReminded).filter { it.substringBefore(":") in upcomingIds }.toSet()
        settings.setRemindedKeys(merged)
        return Result.success()
    }
}
