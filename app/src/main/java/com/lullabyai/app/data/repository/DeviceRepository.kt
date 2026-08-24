package com.lullabyai.app.data.repository

import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** One device this account has ever registered, as returned by `list_devices`. */
@Serializable
data class TrustedDevice(
    @SerialName("id") val id: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("platform") val platform: String,
    @SerialName("status") val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("last_seen_at") val lastSeenAt: String
)

/**
 * Device trust: signing in on a phone this account has never used before requires approval from
 * one that has, before that phone gets in.
 *
 * The account holds a child's health records, so a new, unrecognised device should not be let in
 * silently — whether that is the parent's own second phone (the common case, one approval and
 * done) or a leaked password used somewhere else (the case this exists for).
 *
 * The FIRST device a fresh account ever registers is auto-approved server-side; there is no
 * "pending forever" trap for a brand-new sign-up.
 */
object DeviceRepository {

    /** Registers or checks in this install. Returns "approved", "pending", "rejected" or "revoked". */
    suspend fun register(deviceId: String, deviceName: String): Result<String> = runCatching {
        supabase.postgrest.rpc("register_device", buildJsonObject {
            put("p_device_id", deviceId)
            put("p_device_name", deviceName)
            put("p_platform", "android")
        }).data.trim().trim('"')
    }

    /** Cheap poll for a device waiting on approval. */
    suspend fun myStatus(deviceId: String): Result<String?> = runCatching {
        val raw = supabase.postgrest.rpc("my_device_status", buildJsonObject {
            put("p_device_id", deviceId)
        }).data.trim()
        if (raw == "null" || raw.isEmpty()) null else raw.trim('"')
    }

    /** Every device this account has registered, for the Trusted Devices screen. */
    suspend fun list(): Result<List<TrustedDevice>> = runCatching {
        supabase.postgrest.rpc("list_devices").decodeList<TrustedDevice>()
    }

    /** Approves [id]; must be called from a device that is itself already approved. */
    suspend fun approve(id: String, callerDeviceId: String): Result<Boolean> = runCatching {
        supabase.postgrest.rpc("approve_device", buildJsonObject {
            put("p_id", id); put("p_caller_device_id", callerDeviceId)
        }).data.trim().equals("true", ignoreCase = true)
    }

    /** Rejects [id]; allowed from an approved device, or the pending device cancelling itself. */
    suspend fun reject(id: String, callerDeviceId: String): Result<Boolean> = runCatching {
        supabase.postgrest.rpc("reject_device", buildJsonObject {
            put("p_id", id); put("p_caller_device_id", callerDeviceId)
        }).data.trim().equals("true", ignoreCase = true)
    }

    /** Revokes an already-approved [id]. A device can never revoke itself. */
    suspend fun revoke(id: String, callerDeviceId: String): Result<Boolean> = runCatching {
        supabase.postgrest.rpc("revoke_device", buildJsonObject {
            put("p_id", id); put("p_caller_device_id", callerDeviceId)
        }).data.trim().equals("true", ignoreCase = true)
    }
}
