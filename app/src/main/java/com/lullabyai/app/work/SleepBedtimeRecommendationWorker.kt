package com.lullabyai.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.NotifCategory
import com.lullabyai.app.data.repository.NotificationRepository
import com.lullabyai.app.data.repository.SleepRepository
import com.lullabyai.app.data.settings.SettingsManager
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.Instant

/**
 * Runs every few days (same cooldown as the in-app prompt on SleepScheduleScreen) to reach
 * parents who haven't opened the Sleep tab recently — reaching them proactively is the point of
 * "frequent" here, not just showing the recommendation when they happen to visit.
 * Reuses [SettingsManager.sleepRecRespondedAt] so the in-app card and this notification share one
 * cooldown clock instead of nagging independently.
 */
class SleepBedtimeRecommendationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        runCatching { supabase.auth.awaitInitialization() }
        supabase.auth.currentUserOrNull() ?: return Result.success()

        val settings = SettingsManager(applicationContext)
        val respondedAt = settings.sleepRecRespondedAt.first()
        val cooldownOver = respondedAt == null ||
            runCatching { Duration.between(Instant.parse(respondedAt), Instant.now()) > Duration.ofDays(3) }.getOrDefault(true)
        if (!cooldownOver) return Result.success()

        val children = ChildRepository().getChildren().getOrDefault(emptyList())
        val sleepRepo = SleepRepository()
        val stillMissingMainSchedule = children.any { child ->
            sleepRepo.getSchedules(child.id).getOrDefault(emptyList()).none { it.sleepType == "main" }
        }
        if (stillMissingMainSchedule) {
            NotificationRepository().notify(
                NotifCategory.NORMAL,
                "คำแนะนำเวลานอนสำหรับลูก",
                "NongRak AI มีคำแนะนำเวลาเข้านอนตามวัยของลูก แตะเพื่อดูรายละเอียดในหน้าตารางการนอน"
            )
        }
        return Result.success()
    }
}
