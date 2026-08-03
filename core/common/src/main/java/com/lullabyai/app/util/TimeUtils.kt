package com.lullabyai.app.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Supabase stores all timestamps in UTC (e.g. "2026-06-24T14:30:00+00:00"). The UI must show them in
 * the device's LOCAL timezone (Thailand = UTC+7), otherwise times appear 7 hours off. Centralised here
 * so every screen converts the same way.
 *
 * Plain date-only values ("2026-06-24") are returned as-is, and timestamps that are exactly midnight
 * UTC (how AddHealthRecord persists a date-only pick) are treated as having no real clock time.
 */
object TimeUtils {

    /** Parse an ISO timestamp (offset / Z / instant / naive) into the device-local zone. */
    private fun parseLocal(iso: String): ZonedDateTime? = runCatching {
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault())
    }.recoverCatching {
        Instant.parse(iso).atZone(ZoneId.systemDefault())
    }.recoverCatching {
        // No offset in the string → Supabase emits UTC, so treat as UTC then convert to local.
        LocalDateTime.parse(iso).atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault())
    }.getOrNull()

    /** True for a value with no time component (plain "yyyy-MM-dd" or a midnight-UTC date-only entry). */
    private fun isDateOnly(iso: String): Boolean =
        iso.length < 11 || iso[10] != 'T' || iso.substring(11, 16) == "00:00"

    /** Local "HH:mm", or null for date-only / midnight entries (nothing meaningful to show). */
    fun localTime(iso: String?): String? {
        if (iso.isNullOrBlank() || isDateOnly(iso)) return null
        val z = parseLocal(iso) ?: return null
        return "%02d:%02d".format(z.hour, z.minute)
    }

    /** Local date as "yyyy-MM-dd". Plain dates pass through; timestamps convert to the local day. */
    fun localDate(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        if (iso.length == 10) return iso                 // already a plain date
        val z = parseLocal(iso) ?: return iso.take(10)
        return z.toLocalDate().toString()
    }

    /** Local "dd/MM" (month/day) from a date or timestamp — for compact list rows. */
    fun localDayMonth(iso: String?): String? {
        val d = localDate(iso) ?: return null
        if (d.length < 10) return null
        return d.substring(8, 10) + "/" + d.substring(5, 7)
    }

    /** Parse any Supabase ISO timestamp (offset / Z / naive-as-UTC) into an [Instant], for
     *  programmatic use — Duration math, isBefore/isAfter comparisons. Returns null if unparseable. */
    fun parseInstant(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return parseLocal(iso)?.toInstant()
    }

    /** Convert a device-local wall-clock date+time into the UTC ISO string Supabase expects for a
     *  timestamptz column. Use this for every write — never send LocalDateTime.now().toString()
     *  directly, since Postgres treats an offset-less string as UTC, silently shifting local times. */
    fun toUtcIso(local: LocalDateTime): String =
        local.atZone(ZoneId.systemDefault()).toInstant().toString()
}
