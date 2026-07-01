package com.lullabyai.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
data class Profile(
    val id: String = "",
    val username: String? = null,
    val relationship: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
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
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
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
    @SerialName("recorded_at") val recordedAt: String? = null
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
    val timestamp: Long = System.currentTimeMillis()
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
