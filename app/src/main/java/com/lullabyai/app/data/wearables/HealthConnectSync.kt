package com.lullabyai.app.data.wearables

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.RespiratoryRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.BodyTemperatureRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.lullabyai.app.data.repository.DeviceReadingInsert
import com.lullabyai.app.data.repository.WearableMetric
import com.lullabyai.app.data.repository.WearablePlatform
import com.lullabyai.app.data.repository.WearableRepository
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Android Health Connect bridge — the real sync path for Google Fit / Samsung Health /
 * Fitbit / Garmin data that those apps write into Health Connect.
 *
 * Apple Health is iOS-only → UI "Coming soon" (no client here).
 */
object HealthConnectSync {

    val PERMISSIONS: Set<String> = setOf(
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(BodyTemperatureRecord::class),
        HealthPermission.getReadPermission(OxygenSaturationRecord::class),
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(RespiratoryRateRecord::class),
    )

    fun sdkStatus(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    fun isAvailable(context: Context): Boolean =
        sdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    fun clientOrNull(context: Context): HealthConnectClient? =
        if (isAvailable(context)) HealthConnectClient.getOrCreate(context) else null

    /** Play Store / settings intent so the parent can install or open Health Connect. */
    fun installOrOpenIntent(context: Context): Intent? {
        val status = sdkStatus(context)
        return when {
            status == HealthConnectClient.SDK_AVAILABLE ->
                Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
            status == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("market://details?id=com.google.android.apps.healthdata")
                    setPackage("com.android.vending")
                }
            Build.VERSION.SDK_INT >= 34 ->
                Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)
            else -> Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"
            ))
        }
    }

    suspend fun hasAllPermissions(context: Context): Boolean {
        val client = clientOrNull(context) ?: return false
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(PERMISSIONS)
    }

    /**
     * Pull last [hours] of supported records and map them into [DeviceReadingInsert]s.
     * Source tag defaults to health_connect; callers may override for Fitbit/Garmin UI mapping.
     */
    suspend fun readRecent(
        context: Context,
        childId: String,
        hours: Long = 48,
        source: String = WearablePlatform.HEALTH_CONNECT
    ): List<DeviceReadingInsert> {
        val client = clientOrNull(context) ?: return emptyList()
        if (!hasAllPermissions(context)) return emptyList()

        val end = Instant.now()
        val start = end.minus(hours, ChronoUnit.HOURS)
        val range = TimeRangeFilter.between(start, end)
        val repo = WearableRepository()
        val out = mutableListOf<DeviceReadingInsert>()

        runCatching {
            val resp = client.readRecords(
                ReadRecordsRequest(recordType = HeartRateRecord::class, timeRangeFilter = range)
            )
            resp.records.forEach { rec ->
                rec.samples.forEach { sample ->
                    val bpm = sample.beatsPerMinute.toDouble()
                    val (warn, reason) = repo.evaluateWarning(WearableMetric.HEART_RATE, bpm)
                    out += DeviceReadingInsert(
                        childId = childId,
                        source = source,
                        metric = WearableMetric.HEART_RATE,
                        value = bpm,
                        unit = "bpm",
                        recordedAt = sample.time.toString(),
                        isWarning = warn,
                        warningReason = reason
                    )
                }
            }
        }

        runCatching {
            val resp = client.readRecords(
                ReadRecordsRequest(recordType = SleepSessionRecord::class, timeRangeFilter = range)
            )
            resp.records.forEach { rec ->
                val minutes = java.time.Duration.between(rec.startTime, rec.endTime).toMinutes().toDouble()
                out += DeviceReadingInsert(
                    childId = childId,
                    source = source,
                    metric = WearableMetric.SLEEP_MINUTES,
                    value = minutes,
                    unit = "min",
                    recordedAt = rec.startTime.toString()
                )
            }
        }

        runCatching {
            val resp = client.readRecords(
                ReadRecordsRequest(recordType = BodyTemperatureRecord::class, timeRangeFilter = range)
            )
            resp.records.forEach { rec ->
                val c = rec.temperature.inCelsius
                val (warn, reason) = repo.evaluateWarning(WearableMetric.BODY_TEMP_C, c)
                out += DeviceReadingInsert(
                    childId = childId,
                    source = source,
                    metric = WearableMetric.BODY_TEMP_C,
                    value = c,
                    unit = "celsius",
                    recordedAt = rec.time.toString(),
                    isWarning = warn,
                    warningReason = reason
                )
            }
        }

        runCatching {
            val resp = client.readRecords(
                ReadRecordsRequest(recordType = OxygenSaturationRecord::class, timeRangeFilter = range)
            )
            resp.records.forEach { rec ->
                val pct = rec.percentage.value
                val (warn, reason) = repo.evaluateWarning(WearableMetric.SPO2, pct)
                out += DeviceReadingInsert(
                    childId = childId,
                    source = source,
                    metric = WearableMetric.SPO2,
                    value = pct,
                    unit = "%",
                    recordedAt = rec.time.toString(),
                    isWarning = warn,
                    warningReason = reason
                )
            }
        }

        runCatching {
            val resp = client.readRecords(
                ReadRecordsRequest(recordType = StepsRecord::class, timeRangeFilter = range)
            )
            resp.records.forEach { rec ->
                out += DeviceReadingInsert(
                    childId = childId,
                    source = source,
                    metric = WearableMetric.STEPS,
                    value = rec.count.toDouble(),
                    unit = "steps",
                    recordedAt = rec.startTime.toString()
                )
            }
        }

        return out
    }
}
