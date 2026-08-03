package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.PediaConnectDoctorLink
import com.lullabyai.app.data.model.PediaConnectLinkStatus
import com.lullabyai.app.data.model.ClinicBookingGate
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * `pediaconnect_doctor_links` — the child<->doctor registration link that must exist and be APPROVED
 * before a queue can be booked. Separate from [PediaConnectRepository] (which owns the intake record)
 * because the link is the doctor-facing half and has its own approval lifecycle.
 *
 * `owner_user_id` is intentionally not sent: the column defaults to `auth.uid()` and RLS checks it, so
 * the client cannot claim ownership of someone else's child.
 */
class PediaConnectLinkRepository {

    /** Every link for the given children — the parent-side view (pending + approved + rejected). */
    suspend fun linksForChildren(childIds: List<String>): Result<List<PediaConnectDoctorLink>> = runCatching {
        if (childIds.isEmpty()) return@runCatching emptyList()
        supabase.from("pediaconnect_doctor_links").select {
            filter { isIn("child_id", childIds) }
        }.decodeList<PediaConnectDoctorLink>()
    }

    /** The link for one child/doctor pair, or null when the parent never registered. */
    suspend fun linkFor(doctorId: String, childId: String): Result<PediaConnectDoctorLink?> = runCatching {
        supabase.from("pediaconnect_doctor_links").select {
            filter { eq("doctor_id", doctorId); eq("child_id", childId) }
            limit(1)
        }.decodeList<PediaConnectDoctorLink>().firstOrNull()
    }

    /**
     * File a registration request. Always lands as `pending` — RLS rejects any other status on insert,
     * so a parent cannot register-and-approve in one step.
     *
     * Re-registering after a rejection/withdrawal reuses the row: `(doctor_id, child_id)` is unique.
     */
    suspend fun register(doctorId: String, childId: String, note: String?): Result<PediaConnectDoctorLink> = runCatching {
        val existing = linkFor(doctorId, childId).getOrNull()
        val payload = buildJsonObject {
            put("doctor_id", JsonPrimitive(doctorId))
            put("child_id", JsonPrimitive(childId))
            put("note", note?.let { JsonPrimitive(it) } ?: JsonNull)
        }
        if (existing == null) {
            supabase.from("pediaconnect_doctor_links").insert(payload) { select() }
                .decodeSingle<PediaConnectDoctorLink>()
        } else {
            // Already approved? Leave it alone — re-submitting must not drop an existing approval.
            if (existing.isApproved) existing
            else supabase.from("pediaconnect_doctor_links").update(
                buildJsonObject {
                    put("status", JsonPrimitive(PediaConnectLinkStatus.PENDING))
                    put("note", note?.let { JsonPrimitive(it) } ?: JsonNull)
                }
            ) { filter { eq("id", existing.id) }; select() }.decodeSingle<PediaConnectDoctorLink>()
        }
    }

    /**
     * Children this doctor has *approved* through Pedia Connect. The link row carries `child_id`
     * directly, so this needs no join through `pediaconnect_patients`.
     *
     * Used by [DoctorRepository.getPatientChildIds]: an approved registration makes someone a patient
     * of this doctor whether or not they have ever booked an appointment.
     */
    suspend fun approvedChildIdsForDoctor(doctorId: String): Result<List<String>> = runCatching {
        supabase.from("pediaconnect_doctor_links").select {
            filter { eq("doctor_id", doctorId); eq("status", PediaConnectLinkStatus.APPROVED) }
        }.decodeList<PediaConnectDoctorLink>().map { it.childId }.distinct()
    }

    suspend fun pendingForDoctor(doctorId: String): Result<List<PediaConnectDoctorLink>> = runCatching {
        supabase.from("pediaconnect_doctor_links").select {
            filter { eq("doctor_id", doctorId); eq("status", PediaConnectLinkStatus.PENDING) }
        }.decodeList<PediaConnectDoctorLink>().sortedByDescending { it.createdAt ?: "" }
    }

    suspend fun getLink(linkId: String): Result<PediaConnectDoctorLink?> = runCatching {
        supabase.from("pediaconnect_doctor_links").select {
            filter { eq("id", linkId) }
            limit(1)
        }.decodeList<PediaConnectDoctorLink>().firstOrNull()
    }

    /** HN for each of this doctor's linked children, keyed by child_id — for list/schedule rows. */
    suspend fun hnByChildForDoctor(doctorId: String): Result<Map<String, String>> = runCatching {
        supabase.from("pediaconnect_doctor_links").select {
            filter { eq("doctor_id", doctorId) }
        }.decodeList<PediaConnectDoctorLink>()
            .mapNotNull { link -> link.hn?.takeIf { it.isNotBlank() }?.let { link.childId to it } }
            .toMap()
    }

    /**
     * Assign or clear this child's HN at this doctor's practice. Blank clears rather than storing an
     * empty string, so the partial unique index keeps ignoring unassigned links.
     */
    suspend fun setHn(doctorId: String, childId: String, hn: String?): Result<Unit> = runCatching {
        supabase.from("pediaconnect_doctor_links").update(
            buildJsonObject {
                put("hn", hn?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it.trim()) } ?: JsonNull)
            }
        ) { filter { eq("doctor_id", doctorId); eq("child_id", childId) } }
    }

    /** Doctor-owned clinic note on the link row — with HN, the only patient fields the doctor may
     *  edit, since `children` stays under the parent's ownership (see the children RLS policies). */
    suspend fun setClinicNote(doctorId: String, childId: String, note: String?): Result<Unit> = runCatching {
        supabase.from("pediaconnect_doctor_links").update(
            buildJsonObject {
                put("note", note?.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it.trim()) } ?: JsonNull)
            }
        ) { filter { eq("doctor_id", doctorId); eq("child_id", childId) } }
    }

    suspend fun approve(linkId: String, doctorId: String): Result<Unit> = runCatching {
        supabase.from("pediaconnect_doctor_links").update(
            buildJsonObject { put("status", JsonPrimitive(PediaConnectLinkStatus.APPROVED)) }
        ) { filter { eq("id", linkId); eq("doctor_id", doctorId) } }
    }

    suspend fun reject(linkId: String, doctorId: String): Result<Unit> = runCatching {
        supabase.from("pediaconnect_doctor_links").update(
            buildJsonObject { put("status", JsonPrimitive(PediaConnectLinkStatus.REJECTED)) }
        ) { filter { eq("id", linkId); eq("doctor_id", doctorId) } }
    }

    /** Whether the active child may submit a booking request (Pedia Connect registration required). */
    suspend fun bookingGate(doctorId: String, childId: String?): ClinicBookingGate {
        if (childId.isNullOrBlank()) return ClinicBookingGate.NEED_REGISTRATION
        val onPedia = PediaConnectRepository().hasRegistrationForChild(childId).getOrDefault(false)
        return if (onPedia) ClinicBookingGate.CAN_BOOK else ClinicBookingGate.NEED_REGISTRATION
    }
}
