package com.lullabyai.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.NotifCategory
import com.lullabyai.app.data.repository.NotificationRepository
import com.lullabyai.app.data.repository.WearablePlatform
import com.lullabyai.app.data.repository.WearableRepository
import com.lullabyai.app.data.settings.AppLanguage
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.data.wearables.HealthConnectSync
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.first

/**
 * Periodic Health Connect pull → `device_readings`, then fires parent WARNING notifications for
 * any new warning-flagged metrics (SystemNotifier via NotificationRepository).
 */
class DeviceSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        runCatching { supabase.auth.awaitInitialization() }
        supabase.auth.currentUserOrNull() ?: return Result.success()

        val settings = SettingsManager(applicationContext)
        if (!settings.settings.first().healthAlert) return Result.success()

        val children = ChildRepository().getChildren().getOrDefault(emptyList())
        if (children.isEmpty()) return Result.success()

        val repo = WearableRepository()
        val connections = repo.getConnections().filter { it.status == "connected" }
        if (connections.isEmpty()) return Result.success()

        val hcPlatforms = setOf(
            WearablePlatform.HEALTH_CONNECT,
            WearablePlatform.GOOGLE_FIT,
            WearablePlatform.SAMSUNG,
            WearablePlatform.FITBIT,
            WearablePlatform.GARMIN
        )
        val wantsHc = connections.any { it.platform in hcPlatforms }
        if (!wantsHc || !HealthConnectSync.isAvailable(applicationContext)) return Result.success()

        val notif = NotificationRepository()
        val already = settings.getDeviceWarningKeys()
        val newly = mutableSetOf<String>()
        val isThai = settings.settings.first().language == AppLanguage.TH

        for (child in children) {
            val source = connections.firstOrNull { it.platform in hcPlatforms }?.platform
                ?: WearablePlatform.HEALTH_CONNECT
            val rows = HealthConnectSync.readRecent(
                applicationContext, child.id, hours = 12, source = source
            )
            if (rows.isEmpty()) continue

            val batch = rows.sortedByDescending { it.recordedAt }.take(40)
            repo.insertReadingsBatch(batch)
            repo.markSynced(source, child.id)

            for (r in batch.filter { it.isWarning }) {
                val key = "${child.id}:${r.metric}:${r.recordedAt}"
                if (key in already || key in newly) continue
                notif.notify(
                    NotifCategory.WARNING,
                    if (isThai) "แจ้งเตือนจากอุปกรณ์" else "Device warning",
                    "${child.name}: ${r.warningReason ?: r.metric}"
                )
                newly += key
            }
        }

        if (newly.isNotEmpty()) {
            settings.setDeviceWarningKeys((already + newly).toList().takeLast(200).toSet())
        }
        return Result.success()
    }
}
