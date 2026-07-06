package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.MedicalHistory
import com.lullabyai.app.data.repository.AuthRepository
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.HealthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isComplete: Boolean = false,
    // Step 1
    val childName: String = "",
    val birthdate: String = "",
    val gender: String = "male",
    val allergies: String = "",
    val notes: String = "",
    // Step 2
    val heightCm: String = "",
    val weightKg: String = "",
    val sleepHours: Float = 14f,
    val activityLevel: String = "moderate",
    val bloodGroup: String = "",   // A | B | AB | O (optional)
    // Step 3
    val hasTakenMedication: Boolean = false,
    val medicationDetails: String = "",
    val selectedVaccines: List<String> = emptyList(),
    val underlyingConditions: String = "",
    val allergyEntries: List<AllergyEntry> = emptyList(),   // list-based allergies (name + symptoms)
    val additionalNotes: String = "",
    val pendingDocs: List<PendingDoc> = emptyList(),         // medical docs picked, uploaded after child is created
    // Last Setup
    val username: String = "",
    val relationship: String = "mother",   // mother | father | guardian | other
    val profilePhotoUri: String? = null,
    // True when adding another child to an existing account (vs first-time signup). In add mode we
    // skip the username/LastSetup step and back does NOT cancel/delete the account.
    val addMode: Boolean = false
)

data class AllergyEntry(val name: String, val symptoms: String)
data class PendingDoc(val name: String, val mime: String?, val bytes: ByteArray)

class OnboardingViewModel : ViewModel() {
    private val childRepo = ChildRepository()
    private val healthRepo = HealthRepository()
    private val authRepo = AuthRepository()

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState

    fun updateStep1(name: String, birthdate: String, gender: String, allergies: String, notes: String) {
        _uiState.value = _uiState.value.copy(
            childName = name, birthdate = birthdate, gender = gender,
            allergies = allergies, notes = notes
        )
    }

    fun updateStep2(height: String, weight: String, sleep: Float, activity: String, bloodGroup: String = "") {
        _uiState.value = _uiState.value.copy(
            heightCm = height, weightKg = weight, sleepHours = sleep, activityLevel = activity, bloodGroup = bloodGroup
        )
    }

    fun updateStep3(hasMed: Boolean, medDetails: String, vaccines: List<String>, conditions: String, notes: String = "") {
        _uiState.value = _uiState.value.copy(
            hasTakenMedication = hasMed, medicationDetails = medDetails,
            selectedVaccines = vaccines, underlyingConditions = conditions, additionalNotes = notes
        )
    }

    fun addAllergyEntry(name: String, symptoms: String) {
        if (name.isBlank()) return
        _uiState.value = _uiState.value.copy(
            allergyEntries = _uiState.value.allergyEntries + AllergyEntry(name.trim(), symptoms.trim())
        )
    }

    fun removeAllergyEntry(index: Int) {
        _uiState.value = _uiState.value.copy(
            allergyEntries = _uiState.value.allergyEntries.filterIndexed { i, _ -> i != index }
        )
    }

    fun addPendingDoc(name: String, mime: String?, bytes: ByteArray) {
        _uiState.value = _uiState.value.copy(pendingDocs = _uiState.value.pendingDocs + PendingDoc(name, mime, bytes))
    }

    fun removePendingDoc(index: Int) {
        _uiState.value = _uiState.value.copy(pendingDocs = _uiState.value.pendingDocs.filterIndexed { i, _ -> i != index })
    }

    fun updateLastSetup(username: String, photoUri: String?, relationship: String = "mother") {
        _uiState.value = _uiState.value.copy(username = username, profilePhotoUri = photoUri, relationship = relationship)
    }

    fun completeOnboarding(onSuccess: () -> Unit) {
        // Re-entrancy guard: a fast double-tap on "Complete" was creating two child rows.
        if (_uiState.value.isLoading || _uiState.value.isComplete) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val userId = authRepo.currentUser?.id ?: error("Not logged in")
                val state = _uiState.value

                // Allergy summary string kept on the child row (used by the nutrition allergy watch);
                // the structured entries also go into the allergies table below.
                val allergySummary = state.allergyEntries.joinToString(", ") { it.name }
                    .ifBlank { state.allergies }
                val combinedNotes = listOf(state.notes, state.additionalNotes)
                    .filter { it.isNotBlank() }.joinToString("\n")

                val child = Child(
                    userId = userId,
                    name = state.childName,
                    birthdate = state.birthdate,
                    gender = state.gender,
                    allergies = allergySummary.ifBlank { null },
                    notes = combinedNotes.ifBlank { null },
                    heightCm = state.heightCm.toDoubleOrNull(),
                    weightKg = state.weightKg.toDoubleOrNull(),
                    sleepHours = state.sleepHours.toDouble(),
                    activityLevel = state.activityLevel,
                    bloodGroup = state.bloodGroup.ifBlank { null }
                )
                val savedChild = childRepo.createChild(child).getOrThrow()
                // Make the newly-created child the active one (covers both first signup and "add child").
                com.lullabyai.app.data.settings.ChildSelection.set(savedChild.id)

                // Structured allergy list → allergies table.
                state.allergyEntries.forEach { a ->
                    healthRepo.addAllergy(savedChild.id, a.name, "อื่นๆ", "ไม่ระบุ", a.symptoms.ifBlank { null })
                }
                // Uploaded medical documents → private bucket + metadata.
                state.pendingDocs.forEach { d ->
                    childRepo.uploadMedicalDocument(savedChild.id, d.name, d.mime, d.bytes)
                }

                val history = MedicalHistory(
                    childId = savedChild.id,
                    hasTakenMedication = state.hasTakenMedication,
                    medicationDetails = state.medicationDetails.ifBlank { null },
                    vaccinesGiven = state.selectedVaccines,
                    underlyingConditions = state.underlyingConditions.ifBlank { null }
                )
                childRepo.saveMedicalHistory(history)

                val ageMonths = calculateAgeMonths(state.birthdate)
                childRepo.insertDefaultMilestones(savedChild.id, ageMonths)
                healthRepo.insertDefaultVaccines(savedChild.id, state.birthdate)
                val customVaccines = state.selectedVaccines.filter {
                    com.lullabyai.app.data.VaccineSetupCatalog.normalizeSelection(it) == null
                }
                healthRepo.applyOnboardingVaccineSelections(savedChild.id, state.selectedVaccines, customVaccines)

                if (!state.addMode) {
                    authRepo.updateProfile(null, null, null, state.relationship)
                }

                // Create initial notifications
                val notif = com.lullabyai.app.data.repository.NotificationRepository()
                notif.notify(
                    com.lullabyai.app.data.repository.NotifCategory.NORMAL,
                    "ยินดีต้อนรับสู่ NongRak!",
                    "โปรไฟล์ของ ${state.childName} พร้อมใช้งานแล้ว"
                )
                notif.notify(
                    com.lullabyai.app.data.repository.NotifCategory.SCHEDULE,
                    "ตารางวัคซีนพร้อมแล้ว",
                    "ตรวจสอบกำหนดการฉีดวัคซีนของ ${state.childName} ได้ในสมุดวัคซีน"
                )

                _uiState.value = _uiState.value.copy(isLoading = false, isComplete = true)
                onSuccess()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message ?: "เกิดข้อผิดพลาด")
            }
        }
    }

    private fun calculateAgeMonths(birthdate: String): Int {
        return try {
            val parts = birthdate.split("-")
            val year = parts[0].toInt(); val month = parts[1].toInt()
            val now = java.util.Calendar.getInstance()
            (now.get(java.util.Calendar.YEAR) - year) * 12 + (now.get(java.util.Calendar.MONTH) + 1 - month)
        } catch (e: Exception) { 24 }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }

    /** Clear all fields for a fresh signup onboarding run. */
    fun reset() { _uiState.value = OnboardingUiState() }

    /** Start an "add another child" run: fresh fields, add mode on (skips username, safe back). */
    fun startAddChild() { _uiState.value = OnboardingUiState(addMode = true) }
}
