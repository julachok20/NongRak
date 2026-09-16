package com.lullabyai.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.work.VaccineReminderWorker
import com.lullabyai.app.work.MedicineReminderWorker
import com.lullabyai.app.work.WeeklyHealthReportWorker
import com.lullabyai.app.work.SleepBedtimeRecommendationWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class NongRakApp : Application() {

    companion object {
        /** Application-level context — safe for non-UI operations (AccountManager, cacheDir, etc.) */
        lateinit var instance: NongRakApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        // Supabase client is initialized as a top-level val.
        // Create the system-notification channel up front so notifications can post.
        com.lullabyai.app.util.SystemNotifier.ensureChannel(this)
        scheduleVaccineReminders()
        scheduleMedicineReminders()
        rescheduleSleepReminders()
        scheduleWeeklyHealthReport()
        scheduleSleepBedtimeRecommendation()
        // Retry any growth-log writes that failed while offline last session.
        CoroutineScope(Dispatchers.IO).launch { com.lullabyai.app.data.offline.PendingWriteQueue.flush() }
        // WHO growth tables (~330 KB): read off the main thread so the first growth card doesn't wait.
        CoroutineScope(Dispatchers.IO).launch { runCatching { com.lullabyai.app.util.GrowthReference.load(this@NongRakApp) } }
    }

    // scheduleDeviceSync() pulled Health Connect metrics into device_readings every six hours.
    // Health Connect was removed with the feature it served; readings from a smart crib or baby
    // monitor still arrive through WearableRepository, which never needed a background pull.

    /** Proactive bedtime-recommendation nudge, gated by the same 3-day cooldown the in-app
     *  SleepScheduleScreen prompt uses (checked inside the worker via SettingsManager). */
    private fun scheduleSleepBedtimeRecommendation() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "sleep_bedtime_recommendation",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SleepBedtimeRecommendationWorker>(3, TimeUnit.DAYS).setConstraints(constraints).build()
        )
    }

    /** Proactive weekly re-engagement: turns the on-demand Weekly Health Report into a push
     *  notification, gated by the "dailyReport" settings toggle (checked inside the worker). */
    private fun scheduleWeeklyHealthReport() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weekly_health_report",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WeeklyHealthReportWorker>(7, TimeUnit.DAYS).setConstraints(constraints).build()
        )
    }

    /** Re-arms sleep-schedule reminders for the last-selected child on cold start (fresh install,
     *  reboot before any schedule was re-saved) — add/delete already keep these current at runtime. */
    private fun rescheduleSleepReminders() {
        CoroutineScope(Dispatchers.IO).launch {
            com.lullabyai.app.data.settings.ChildSelection.awaitLoaded()
            com.lullabyai.app.data.settings.ChildSelection.current()?.let {
                com.lullabyai.app.util.SleepReminderScheduler.rescheduleAll(it)
            }
        }
    }

    /** Background reminders for due medicine doses (checks every 30 min + once now). */
    private fun scheduleMedicineReminders() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val wm = WorkManager.getInstance(this)
        wm.enqueueUniquePeriodicWork(
            "medicine_reminders",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MedicineReminderWorker>(30, TimeUnit.MINUTES).setConstraints(constraints).build()
        )
        wm.enqueueUniqueWork(
            "medicine_reminders_now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<MedicineReminderWorker>().setConstraints(constraints).build()
        )
    }

    /** Background reminders for upcoming vaccine appointments (checks ~twice a day + once now). */
    private fun scheduleVaccineReminders() {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val wm = WorkManager.getInstance(this)
        wm.enqueueUniquePeriodicWork(
            "vaccine_reminders",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<VaccineReminderWorker>(12, TimeUnit.HOURS).setConstraints(constraints).build()
        )
        // Also run shortly after each launch so reminders aren't delayed up to a full period.
        wm.enqueueUniqueWork(
            "vaccine_reminders_now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<VaccineReminderWorker>().setConstraints(constraints).build()
        )
    }
}
