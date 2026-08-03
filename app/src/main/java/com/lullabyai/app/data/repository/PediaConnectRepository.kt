package com.lullabyai.app.data.repository

import com.lullabyai.app.data.model.PediaConnectPatientRecord
import com.lullabyai.app.data.model.PediaConnectPatientParentRecord
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@kotlinx.serialization.Serializable
private data class PediaPatientRow(val id: String? = null)

class PediaConnectRepository {

    /** Hospital / Pedia Connect intake — does not modify `children`; links via `child_id` when user picked an app profile. */
    suspend fun registerIntake(
        childId: String,
        name: String,
        birthdate: String,
        gender: String,
        bloodGroup: String?,
        allergies: String?,
        notes: String?,
        mainGuardianUserId: String?,
        mainGuardianName: String,
        mainGuardianRelationship: String,
        mainGuardianPhone: String,
        mainGuardianEmail: String
    ): Result<String> = runCatching {
        val bd = birthdate.takeIf { it.isNotBlank() }
        supabase.postgrest.rpc(
            "register_pediaconnect_intake",
            buildJsonObject {
                put("p_child_id", JsonPrimitive(childId))
                put("p_name", JsonPrimitive(name))
                put("p_birthdate", bd?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                put("p_gender", JsonPrimitive(gender))
                put("p_blood_group", bloodGroup?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                put("p_allergies", allergies?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                put("p_notes", notes?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                put("p_main_user_id", mainGuardianUserId?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                put("p_main_full_name", JsonPrimitive(mainGuardianName))
                put("p_main_relationship", mainGuardianRelationship.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                put("p_main_phone", mainGuardianPhone.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
                put("p_main_email", mainGuardianEmail.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull)
            }
        ).data.trim().trim('"')
    }

    suspend fun hasRegistrationForChild(childId: String): Result<Boolean> = runCatching {
        supabase.from("pediaconnect_patients").select {
            filter { eq("child_id", childId) }
            limit(1)
        }.decodeList<PediaPatientRow>().isNotEmpty()
    }

    /** Which of the given app child ids have a Pedia Connect registration row. */
    suspend fun registeredChildIdsAmong(childIds: List<String>): Result<Set<String>> = runCatching {
        if (childIds.isEmpty()) return@runCatching emptySet()
        childIds.filter { id ->
            hasRegistrationForChild(id).getOrDefault(false)
        }.toSet()
    }

    /** Pedia intake row for a child — visible to parents and doctors with a pending/approved link (RLS). */
    suspend fun patientRecordForChild(childId: String): Result<PediaConnectPatientRecord?> = runCatching {
        supabase.from("pediaconnect_patients").select {
            filter { eq("child_id", childId) }
            limit(1)
        }.decodeList<PediaConnectPatientRecord>().firstOrNull()
    }

    suspend fun mainParentForPatient(patientId: String): Result<PediaConnectPatientParentRecord?> = runCatching {
        supabase.from("pediaconnect_patient_parents").select {
            filter { eq("patient_id", patientId); eq("is_main_parent", true) }
            limit(1)
        }.decodeList<PediaConnectPatientParentRecord>().firstOrNull()
    }
}
