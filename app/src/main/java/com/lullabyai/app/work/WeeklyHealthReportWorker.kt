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
            val report = chatRepo.generateWeeklyHealthReport(child, forceRefresh = true, isThai = true)
                .getOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: continue

            // A child with no name yet read as "ของ.พร้อมแล้ว" in production, and a named one as
            // "ของGigaพร้อมแล้ว" with no spaces. Both fixed here.
            val name = child.name.trim().takeIf { it.isNotEmpty() && it != "." }
            val title = if (name != null) {
                "รายงานสุขภาพประจำสัปดาห์ของ $name พร้อมแล้ว"
            } else {
                "รายงานสุขภาพประจำสัปดาห์พร้อมแล้ว"
            }

            notifRepo.notify(NotifCategory.NORMAL, title, snippet(report))
        }
        return Result.success()
    }

    /**
     * The preview line under the title. Cuts at a sentence end where there is one nearby, so the
     * notification does not break mid-word - Thai has no spaces between words, which makes a blind
     * substring especially unreadable.
     */
    private fun snippet(report: String, limit: Int = 120): String {
        val oneLine = report.replace(Regex("\\s+"), " ").trim()
        if (oneLine.length <= limit) return oneLine

        val head = oneLine.take(limit)
        val cut = head.lastIndexOfAny(charArrayOf('.', '!', '?', 'ๆ'))
        return if (cut >= limit / 2) head.take(cut + 1) else "$head…"
    }
}
