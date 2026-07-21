package com.lullabyai.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Monthly parent (not child) mental-health self-assessment — modeled on the DMH-endorsed EPDS
 *  (Edinburgh Postnatal Depression Scale) framework: 10 items, each 0-3, total 0-30. */
@Serializable
data class MentalHealthAssessment(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val answers: List<Int> = emptyList(),
    @SerialName("total_score") val totalScore: Int = 0,
    @SerialName("risk_tier") val riskTier: String = "low", // low | moderate | high
    @SerialName("flagged_self_harm") val flaggedSelfHarm: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class MedicalDocument(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("file_name") val fileName: String = "",
    @SerialName("file_path") val filePath: String = "",
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class MedicationSchedule(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("medicine_name") val medicineName: String = "",
    val dosage: Double? = null,
    @SerialName("dosage_unit") val dosageUnit: String = "mg",
    val frequency: String? = null,
    @SerialName("schedule_type") val scheduleType: String = "times", // date_range | times | until_gone
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("num_doses") val numDoses: Int? = null,
    @SerialName("doses_taken") val dosesTaken: Int = 0,
    val notes: String? = null,
    val status: String = "active", // active | completed | stopped
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class MedicationDose(
    val id: String = "",
    @SerialName("schedule_id") val scheduleId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("scheduled_at") val scheduledAt: String = "",
    @SerialName("taken_at") val takenAt: String? = null,
    val status: String = "pending", // pending | taken | skipped
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SleepLog(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("sleep_type") val sleepType: String = "main", // main | nap
    @SerialName("start_time") val startTime: String = "",
    @SerialName("end_time") val endTime: String? = null,       // null while a session is in progress
    val mood: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class SleepSchedule(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    val label: String = "",
    @SerialName("sleep_type") val sleepType: String = "main",
    @SerialName("start_time") val startTime: String = "",   // "HH:mm"
    @SerialName("end_time") val endTime: String = "",        // "HH:mm"
    @SerialName("days_of_week") val daysOfWeek: String = "ALL", // "MON,TUE,..." or "ALL"
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Profile(
    val id: String = "",
    val username: String? = null,
    val relationship: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    // Separate, opt-in consent for using (de-identified) data to improve/train AI models —
    // deliberately distinct from the mandatory signup consent, since it's a different purpose
    // under PDPA and must be freely given, not bundled. Off by default.
    @SerialName("ai_training_consent") val aiTrainingConsent: Boolean = false,
    @SerialName("ai_training_consent_at") val aiTrainingConsentAt: String? = null
)

@Serializable
data class Child(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val name: String = "",
    val birthdate: String = "",
    val gender: String = "unspecified",
    val allergies: String? = null,
    val notes: String? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("sleep_hours") val sleepHours: Double? = null,
    @SerialName("activity_level") val activityLevel: String = "moderate",
    @SerialName("blood_group") val bloodGroup: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    // Explicit "confirmed no allergies" — distinct from simply having zero rows in the allergies
    // table, so the missing-info nudge can tell "not yet answered" apart from "answered: none".
    @SerialName("no_known_allergies") val noKnownAllergies: Boolean = false,
    // Hospital-record demographic fields (สัญชาติ/เชื้อชาติ/ศาสนา) — collected on the first onboarding
    // page and printed on the doctor-export PDF, all optional.
    val nationality: String? = null,
    val ethnicity: String? = null,
    val religion: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

/** A candidate match returned by the search_child_for_sync RPC (name + birthdate, no other data). */
@Serializable
data class ChildSyncMatch(
    @SerialName("child_id") val childId: String = "",
    @SerialName("child_name") val childName: String = ""
)

/** A pending/resolved request to sync (co-parent) access to someone else's child. */
@Serializable
data class ChildSyncRequest(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("requester_user_id") val requesterUserId: String = "",
    @SerialName("owner_user_id") val ownerUserId: String = "",
    val method: String = "search",
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class MedicalHistory(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("has_taken_medication") val hasTakenMedication: Boolean = false,
    @SerialName("medication_details") val medicationDetails: String? = null,
    @SerialName("underlying_conditions") val underlyingConditions: String? = null,
    @SerialName("vaccines_given") val vaccinesGiven: List<String>? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class VaccineRecord(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("vaccine_name") val vaccineName: String = "",
    @SerialName("date_given") val dateGiven: String? = null,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    // Nullable: legacy DB rows may have NULL status; a non-nullable field would break list decoding.
    // status: scheduled | upcoming | appointment | overdue | completed | skipped
    val status: String? = "scheduled",
    val notes: String? = null,                 // also holds the skip reason when status == "skipped"
    @SerialName("place") val place: String? = null,   // hospital / clinic where the dose was/will be given
    // Nullable for the same reason: older seed generations left NULL age_group/age_order on some
    // rows. A non-nullable field makes kotlinx throw on the explicit null and blanks the WHOLE list.
    @SerialName("age_group") val ageGroup: String? = "แรกเกิด",
    @SerialName("age_order") val ageOrder: Int? = 0,
    val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class MilkLog(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("milk_type") val milkType: String = "formula", // breast | formula
    @SerialName("volume_ml") val volumeMl: Int = 0,
    val side: String? = null, // left | right | both (breast only)
    @SerialName("logged_at") val loggedAt: String? = null,
    val notes: String? = null
)

@Serializable
data class WasteRecord(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("waste_type") val wasteType: String = "stool", // urine | stool
    val consistency: String? = null, // stool only: soft_normal | hard_constipated | loose_diarrhea | mucus_blood
    val color: String? = null,       // yellow | brown | green | red_orange | grey_white
    val amount: String = "medium",   // low | medium | high
    @SerialName("recorded_at") val recordedAt: String? = null,
    val notes: String? = null
)

@Serializable
data class AppNotification(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val category: String = "normal",  // schedule | warning | normal
    val title: String = "",
    val body: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class GrowthRecord(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("height_cm") val heightCm: Double? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("record_type") val recordType: String = "manual",
    val notes: String? = null
)

@Serializable
data class ChronicCondition(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    val name: String = "",
    val notes: String? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Allergy(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    val name: String = "",
    val type: String = "food",          // food | environment | medication
    val severity: String = "mild",      // mild | moderate | severe
    val symptoms: String? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class HealthRecord(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("event_type") val eventType: String = "illness",
    val title: String = "",
    val description: String? = null,
    val severity: String = "mild",
    val temperature: Double? = null,
    @SerialName("additional_symptoms") val additionalSymptoms: List<String>? = null,
    @SerialName("photo_url") val photoUrl: String? = null,
    @SerialName("recorded_at") val recordedAt: String? = null,
    // recorded_at is date-only in practice (AddHealthRecordScreen only lets the user pick a date,
    // not a time), so two records logged the same day are indistinguishable by it alone — created_at
    // (a real insert-time timestamp, DB default now()) breaks the tie so the list order matches the
    // order they were actually logged in, not an arbitrary one.
    @SerialName("created_at") val createdAt: String? = null,
    // monitoring | recovered — drives the status badge on HealthHistoryScreen's timeline.
    val status: String = "monitoring"
)

// One entry per day of tracking an illness (health_records row = the episode/parent). symptoms is
// a name->severity map (none|mild|moderate|severe) so custom symptoms need no schema change.
@Serializable
data class SymptomLog(
    val id: String = "",
    @SerialName("health_record_id") val healthRecordId: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("log_date") val logDate: String = "",
    val symptoms: Map<String, String> = emptyMap(),
    val temperature: Double? = null,
    val feeling: String? = null,           // cold | normal | hot
    @SerialName("day_status") val dayStatus: String = "active",   // started | active | recovered
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class DevelopmentMilestone(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    val category: String = "",
    @SerialName("milestone_name") val milestoneName: String = "",
    @SerialName("is_achieved") val isAchieved: Boolean = false,
    @SerialName("achieved_at") val achievedAt: String? = null,
    @SerialName("age_months_expected") val ageMonthsExpected: Int? = null,
    @SerialName("skip_reason") val skipReason: String? = null
)

@Serializable
data class ChatSession(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val title: String = "แชทใหม่",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class ChatMessage(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    @SerialName("session_id") val sessionId: String? = null,
    val role: String = "user",
    val content: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

data class UiChatMessage(
    val id: String,
    val role: String,
    val content: String,
    val imageUri: String? = null,   // local content:// URI of an attached image (display only, this session)
    val timestamp: Long = System.currentTimeMillis(),
    val agentAction: AgentAction? = null,   // non-null when the AI proposed a data-changing action
    val actionStatus: String? = null,       // null = awaiting confirmation, "confirmed", "cancelled", "failed"
    val needInfo: NeedInfoRequest? = null,  // non-null when the AI needs one piece of info to help further
    val needInfoAnswered: Boolean = false   // true once the popup was submitted or skipped (hides it)
)

/**
 * The AI asked a clarifying question via chat (e.g. it can't judge growth without the child's
 * current weight). No skip cooldown by design — skipping just dismisses this instance; if the AI
 * still lacks the info on a later turn, it asks again on its own.
 */
@Serializable
data class NeedInfoRequest(
    val question: String,
    val fieldLabel: String = ""
)

/**
 * A data-changing action the AI proposed via chat (log/edit/delete). Always requires an explicit
 * user tap to execute — see ChatViewModel.confirmAgentAction — nothing in [type]/[params] runs on
 * its own just because the model emitted it.
 */
@Serializable
data class AgentAction(
    val type: String,
    val confirmText: String,
    val params: Map<String, String> = emptyMap()
)

@Serializable
data class AiTask(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("task_text") val taskText: String = "",
    val category: String = "general",
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("completed_at") val completedAt: String? = null,
    val status: String = "active",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AiAnalysis(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("analysis_type") val analysisType: String = "",
    val content: String = "",
    @SerialName("updated_at") val updatedAt: String? = null
)

// ---------------- Nutrition / Calorie / Exercise feature ----------------

@Serializable
data class FoodLog(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("log_date") val logDate: String = "",
    @SerialName("meal_type") val mealType: String = "breakfast", // breakfast|lunch|dinner|snack|other
    @SerialName("item_name") val itemName: String = "",
    val calories: Int? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ExerciseLog(
    val id: String = "",
    @SerialName("child_id") val childId: String = "",
    @SerialName("log_date") val logDate: String = "",
    @SerialName("activity_name") val activityName: String = "",
    @SerialName("duration_min") val durationMin: Int? = null,
    @SerialName("calories_burned") val caloriesBurned: Int? = null,
    val completed: Boolean = true,
    val mood: String? = null,   // tired | happy | energized — how the child felt after
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

/** AI-generated content (not persisted) — recipe & exercise instructions, suggestion lists. */
data class RecipeContent(
    val title: String,
    val timeMinutes: String,
    val ageRange: String,
    val servings: String,
    val nutritionNote: String,
    val ingredients: List<String>,
    val steps: List<String>
)

data class ExerciseContent(
    val title: String,
    val prepTime: String,
    val duration: String,
    val ageRange: String,
    val difficulty: String,
    val benefit: String,
    val equipment: List<String>,
    val steps: List<String>
)

/** A lightweight suggestion item shown in lists (recommended foods / activities). */
data class SuggestionItem(
    val title: String,
    val subtitle: String,
    val tags: List<String> = emptyList()
)
