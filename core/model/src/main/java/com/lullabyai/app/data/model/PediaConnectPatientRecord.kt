package com.lullabyai.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Row from `pediaconnect_patients` — hospital intake copy (child SSOT remains `children`). */
@Serializable
data class PediaConnectPatientRecord(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    val name: String = "",
    val birthdate: String? = null,
    val gender: String? = null,
    @SerialName("blood_group") val bloodGroup: String? = null,
    val allergies: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class PediaConnectPatientParentRecord(
    @SerialName("full_name") val fullName: String = "",
    val relationship: String? = null,
    val phone: String? = null,
    val email: String? = null,
    @SerialName("is_main_parent") val isMainParent: Boolean = false
)
