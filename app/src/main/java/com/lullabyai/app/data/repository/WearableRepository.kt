package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.DeviceReading
import com.lullabyai.app.data.model.WearableConnection
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object WearablePlatform {
    const val APPLE_HEALTH = "apple_health"
    const val HEALTH_CONNECT = "health_connect"
    const val GOOGLE_FIT = "google_fit"
    const val SAMSUNG = "samsung"
    const val FITBIT = "fitbit"
    const val GARMIN = "garmin"
    const val SMART_CRIB = "smart_crib"
    const val BABY_MONITOR = "baby_monitor"
}

object WearableMetric {
    const val HEART_RATE = "heart_rate"
    const val SLEEP_MINUTES = "sleep_minutes"
    const val BODY_TEMP_C = "body_temp_c"
    const val SPO2 = "spo2"
    const val STEPS = "steps"
    const val ROOM_TEMP_C = "room_temp_c"
    const val MOVEMENT = "movement"
}

class WearableRepository {

    suspend fun getConnections(): List<WearableConnection> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()
        supabase.from("wearable_connections").select {
            filter { eq("user_id", userId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<WearableConnection>()
    }.getOrDefault(emptyList())

    suspend fun upsertConnection(
        platform: String,
        childId: String?,
        displayName: String?,
        status: String = "connected"
    ): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not signed in")
        // Replace prior row for this user+platform (+child when set).
        runCatching {
            supabase.from("wearable_connections").delete {
                filter {
                    eq("user_id", userId)
                    eq("platform", platform)
                    if (childId != null) eq("child_id", childId)
                }
            }
        }
        supabase.from("wearable_connections").insert(
            buildJsonObject {
                put("user_id", userId)
                if (childId != null) put("child_id", childId)
                put("platform", platform)
                if (displayName != null) put("display_name", displayName)
                put("status", status)
                put("last_synced_at", java.time.Instant.now().toString())
            }
        )
    }

    suspend fun disconnect(platform: String, childId: String? = null): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return@runCatching
        supabase.from("wearable_connections").delete {
            filter {
                eq("user_id", userId)
                eq("platform", platform)
                if (childId != null) eq("child_id", childId)
            }
        }
    }

    suspend fun markSynced(platform: String, childId: String?): Result<Unit> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return@runCatching
        supabase.from("wearable_connections").update(
            buildJsonObject { put("last_synced_at", java.time.Instant.now().toString()) }
        ) {
            filter {
                eq("user_id", userId)
                eq("platform", platform)
                if (childId != null) eq("child_id", childId)
            }
        }
    }

    suspend fun getReadings(childId: String, limit: Long = 50): List<DeviceReading> = runCatching {
        supabase.from("device_readings").select {
            filter { eq("child_id", childId) }
            order("recorded_at", Order.DESCENDING)
            limit(limit)
        }.decodeList<DeviceReading>()
    }.getOrDefault(emptyList())

    /** Compact recent metrics for AI / analysis prompts. */
    suspend fun recentSummaryForAi(childId: String, days: Long = 3): String {
        val since = java.time.Instant.now().minus(java.time.Duration.ofDays(days)).toString()
        val rows = runCatching {
            supabase.from("device_readings").select {
                filter {
                    eq("child_id", childId)
                    gte("recorded_at", since)
                }
                order("recorded_at", Order.DESCENDING)
                limit(80)
            }.decodeList<DeviceReading>()
        }.getOrDefault(emptyList())
        if (rows.isEmpty()) return ""

        fun latest(metric: String) = rows.firstOrNull { it.metric == metric }
        fun avg(metric: String): Double? {
            val vals = rows.filter { it.metric == metric }.map { it.value }
            return if (vals.isEmpty()) null else vals.average()
        }

        return buildString {
            append("\nข้อมูลจากอุปกรณ์/แอปสุขภาพ (Wearables/IoT ${days} วันล่าสุด):\n")
            latest(WearableMetric.HEART_RATE)?.let {
                append("- ชีพจรล่าสุด: ${it.value.toInt()} bpm (${it.source})\n")
            }
            avg(WearableMetric.SLEEP_MINUTES)?.let {
                append("- นอนเฉลี่ยจากอุปกรณ์: ${(it / 60.0).let { h -> "%.1f".format(h) }} ชม./ครั้ง\n")
            }
            latest(WearableMetric.BODY_TEMP_C)?.let {
                append("- อุณหภูมิร่างกายล่าสุด: ${"%.1f".format(it.value)} °C\n")
            }
            latest(WearableMetric.ROOM_TEMP_C)?.let {
                append("- อุณหภูมิห้อง (IoT): ${"%.1f".format(it.value)} °C\n")
            }
            latest(WearableMetric.SPO2)?.let {
                append("- SpO2 ล่าสุด: ${it.value.toInt()}%\n")
            }
            val warnings = rows.filter { it.isWarning }.take(3)
            if (warnings.isNotEmpty()) {
                append("- การแจ้งเตือนจากอุปกรณ์ล่าสุด:\n")
                warnings.forEach { w ->
                    append("  • ${w.warningReason ?: w.metric} (${w.recordedAt ?: "-"})\n")
                }
            }
        }
    }

    suspend fun insertReading(
        childId: String,
        source: String,
        metric: String,
        value: Double,
        unit: String?,
        recordedAt: String,
        isWarning: Boolean = false,
        warningReason: String? = null
    ): Result<DeviceReading?> = runCatching {
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not signed in")
        supabase.from("device_readings").insert(
            buildJsonObject {
                put("child_id", childId)
                put("user_id", userId)
                put("source", source)
                put("metric", metric)
                put("value", value)
                if (unit != null) put("unit", unit)
                put("recorded_at", recordedAt)
                put("is_warning", isWarning)
                if (warningReason != null) put("warning_reason", warningReason)
            }
        ) { select() }.decodeList<DeviceReading>().firstOrNull()
    }

    suspend fun insertReadingsBatch(rows: List<DeviceReadingInsert>): Result<Int> = runCatching {
        if (rows.isEmpty()) return@runCatching 0
        val userId = supabase.auth.currentUserOrNull()?.id ?: error("Not signed in")
        val payload = rows.map { r ->
            buildJsonObject {
                put("child_id", r.childId)
                put("user_id", userId)
                put("source", r.source)
                put("metric", r.metric)
                put("value", r.value)
                if (r.unit != null) put("unit", r.unit)
                put("recorded_at", r.recordedAt)
                put("is_warning", r.isWarning)
                if (r.warningReason != null) put("warning_reason", r.warningReason)
            }
        }
        supabase.from("device_readings").insert(payload)
        rows.size
    }

    /** Pediatric-oriented thresholds for device warnings (conservative; not a diagnosis). */
    fun evaluateWarning(metric: String, value: Double): Pair<Boolean, String?> = when (metric) {
        WearableMetric.HEART_RATE -> when {
            value < 70 -> true to "ชีพจรต่ำผิดปกติจากอุปกรณ์ (${value.toInt()} bpm)"
            value > 180 -> true to "ชีพจรสูงผิดปกติจากอุปกรณ์ (${value.toInt()} bpm)"
            else -> false to null
        }
        WearableMetric.BODY_TEMP_C -> when {
            value >= 38.0 -> true to "อุณหภูมิร่างกายสูงจากอุปกรณ์ (${"%.1f".format(value)} °C)"
            value < 35.5 -> true to "อุณหภูมิต่ำจากอุปกรณ์ (${"%.1f".format(value)} °C)"
            else -> false to null
        }
        WearableMetric.ROOM_TEMP_C -> when {
            value >= 30.0 -> true to "อุณหภูมิห้องสูงจากอุปกรณ์ IoT (${"%.1f".format(value)} °C)"
            value < 16.0 -> true to "อุณหภูมิห้องต่ำจากอุปกรณ์ IoT (${"%.1f".format(value)} °C)"
            else -> false to null
        }
        WearableMetric.SPO2 -> when {
            value in 1.0..94.0 -> true to "ค่าออกซิเจนในเลือดต่ำจากอุปกรณ์ (${value.toInt()}%)"
            else -> false to null
        }
        else -> false to null
    }
}

data class DeviceReadingInsert(
    val childId: String,
    val source: String,
    val metric: String,
    val value: Double,
    val unit: String?,
    val recordedAt: String,
    val isWarning: Boolean = false,
    val warningReason: String? = null
)
