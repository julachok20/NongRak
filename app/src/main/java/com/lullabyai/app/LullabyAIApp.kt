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
