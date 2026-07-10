package com.lullabyai.app.util

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Hands a vaccine due-date off to whatever calendar app is installed (Google Calendar or
 * otherwise) via Android's built-in calendar-insert intent — no Google Sign-In, no Calendar API,
 * no OAuth. The user still taps "Save" in their calendar app; nothing is added silently.
 *
 * Returns false (instead of failing silently) when the date is unparseable or no app on the
 * device can handle the insert — the caller shows a Toast either way, since a tapped button that
 * visibly does nothing is indistinguishable from a broken one.
 */
object CalendarHelper {
    fun addVaccineEvent(context: Context, vaccineName: String, childName: String, dateIso: String, place: String?): Boolean {
        val date = runCatching { LocalDate.parse(dateIso.take(10)) }.getOrNull() ?: return false
        // ALL_DAY events must be anchored to UTC midnight, per CalendarContract's own contract —
        // using the device's local zone (e.g. Thailand, UTC+7) shifts this 7 hours off UTC
        // midnight, which some calendar apps quietly reject by discarding the pre-fill entirely
        // and showing a blank "new event" form instead of erroring.
        val startMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val endMillis = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(Intent.EXTRA_TITLE, "วัคซีน: $vaccineName ($childName)")
            putExtra(CalendarContract.Events.TITLE, "วัคซีน: $vaccineName ($childName)")
            putExtra(CalendarContract.Events.DESCRIPTION, "นัดฉีดวัคซีน $vaccineName สำหรับ $childName" + (place?.let { " ที่ $it" } ?: ""))
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            place?.let { putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
        }
        if (intent.resolveActivity(context.packageManager) == null) return false
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
