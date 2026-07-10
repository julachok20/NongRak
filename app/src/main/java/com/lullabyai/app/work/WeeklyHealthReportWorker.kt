package com.lullabyai.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChatRepository
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.NotifCategory
import com.lullabyai.app.data.repository.NotificationRepository
import com.lullabyai.app.data.settings.SettingsManager
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.first

/**
 * Runs weekly (WorkManager) to turn the existing Weekly Health Report — previously only reachable
 * on-demand from ความเสี่ยงโรค > ดูรายงานเชิงลึก — into a proactive re-engagement notification.
 * Gated by the existing "dailyReport" settings toggle (repurposed; it was built but never wired
 * to anything).
 */
class WeeklyHealthReportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        runCatching { supabase.auth.awaitInitialization() }
        supabase.auth.currentUserOrNull() ?: return Result.success()

        val settings = SettingsManager(applicationContext)
        if (!settings.settings.first().dailyReport) return Result.success()

        val children = ChildRepository().getChildren().getOrDefault(emptyList())
        if (children.isEmpty()) return Result.success()

        val chatRepo = ChatRepository()
        val notifRepo = NotificationRepository()
        for (child in children) {
            val report = chatRepo.generateWeeklyHealthReport(child, forceRefresh = true, isThai = true).getOrNull() ?: continue
            val snippet = if (report.length > 120) report.take(120) + "..." else report
            notifRepo.notify(
                NotifCategory.NORMAL,
                "รายงานสุขภาพประจำสัปดาห์ของ${child.name}พร้อมแล้ว",
                snippet
            )
        }
        return Result.success()
    }
}
