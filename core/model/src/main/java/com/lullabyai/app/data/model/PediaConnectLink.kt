package com.lullabyai.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Optional `pediaconnect_doctor_links` row — legacy / clinic relationship metadata.
 *
 * **Product rule (2026-07-28):** Pedia **system register** (`pediaconnect_patients`) needs no doctor approval.
 * **Each booking** is a `pending` row on `appointments` until the doctor confirms that appointment.
 *
 * Bookings stay on `appointments` (no separate queue table). Link rows are not used to gate booking.
 *
 * Status is enforced in RLS, never only here: a parent may insert `PENDING` and move it to `REVOKED`;
 * only the addressed doctor may reach `APPROVED`/`REJECTED`.
 */
@Serializable
data class PediaConnectDoctorLink(
    val id: String = "",
    @SerialName("doctor_id") val doctorId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("owner_user_id") val ownerUserId: String? = null,
    val status: String = PediaConnectLinkStatus.PENDING,
    val note: String? = null,
    /**
     * Clinic-assigned Hospital Number for this child at THIS doctor's practice.
     *
     * Lives on the link rather than on the child because HN is issued by the clinic: the same child
     * registered at two practices legitimately holds two different numbers. Free text — HN formats
     * vary by hospital. Unique per (doctor_id, hn) in the DB so a mistyped duplicate cannot end up
     * pointing at two patients.
     */
    val hn: String? = null,
    @SerialName("created_at") val createdAt: String? = null
) {
    val isApproved: Boolean get() = status == PediaConnectLinkStatus.APPROVED
    val isPending: Boolean get() = status == PediaConnectLinkStatus.PENDING
}

/** The `status` check-constraint values, kept in one place so UI and repository can't drift apart. */
object PediaConnectLinkStatus {
    const val PENDING = "pending"
    const val APPROVED = "approved"
    const val REJECTED = "rejected"
    const val REVOKED = "revoked"
}

/** Parent-side gate before submitting a booking request (Pedia Connect child registration only). */
enum class ClinicBookingGate {
    /** Child has a `pediaconnect_patients` row — may submit a booking (appointment stays pending until doctor approves). */
    CAN_BOOK,
    /** Child not registered on Pedia Connect yet. */
    NEED_REGISTRATION
}
