package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.GrowthRecord
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
    /** The nickname (ชื่อเล่น) shown across the app. */
    val childName: String = "",
    /** The legal name, used only on documents such as the ID card. */
    val childFullName: String = "",
    val birthdate: String = "",
    val gender: String = "male",
    val nationality: String = "",
    val ethnicity: String = "",
    val religion: String = "",
    val allergies: String = "",
    val notes: String = "",
    val childAvatarUri: String? = null,
    val childAvatarBytes: ByteArray? = null,
    val relationship: String = "mother",   // mother | father | guardian | other
    // Step 2
    val heightCm: String = "",
    val weightKg: String = "",
    // Null until the parent actually sets it. It used to default to 14f, which made an
    // "(ไม่บังคับ)" field arrive pre-answered and then SAVED that invented number - and
    // ChatRepository feeds sleep_hours to the AI as a stated fact about the child.
    val sleepHours: Float? = null,
    val activityLevel: String = "moderate",
    val bloodGroup: String = "",   // A | B | AB | O (optional)
    // Step 3
    val hasTakenMedication: Boolean = false,
    val medicationDetails: String = "",
    val selectedVaccines: List<String> = emptyList(),
    val underlyingConditions: String = "",
    val allergyEntries: List<AllergyEntry> = emptyList(),   // list-based allergies (name + symptoms)
    val noKnownAllergies: Boolean = false,   // explicit "checked none" — distinct from just not having filled it in yet
    val additionalNotes: String = "",
    val pendingDocs: List<PendingDoc> = emptyList(),         // medical docs picked, uploaded after child is created
    // Last Setup
    val username: String = "",
    val profilePhotoUri: String? = null,
    // True when adding another child to an existing account (vs first-time signup). In add mode we
    // skip the username/LastSetup step and back does NOT cancel/delete the account.
    val addMode: Boolean = false,
    val existingChildId: String? = null
)

data class AllergyEntry(val name: String, val symptoms: String)
data class PendingDoc(val name: String, val mime: String?, val bytes: ByteArray)

class OnboardingViewModel : ViewModel() {
    private val childRepo = ChildRepository()
    private val healthRepo = HealthRepository()
    private val authRepo = AuthRepository()

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState

    fun initFromChild(child: Child) {
        _uiState.value = _uiState.value.copy(
            childName = child.name,
            childFullName = child.fullName ?: "",
            birthdate = child.birthdate,
            gender = child.gender,
            nationality = child.nationality ?: "",
            ethnicity = child.ethnicity ?: "",
            religion = child.religion ?: "",
            allergies = child.allergies ?: "",
            notes = child.notes ?: "",
            heightCm = child.heightCm?.toString() ?: "",
            weightKg = child.weightKg?.toString() ?: "",
            sleepHours = child.sleepHours?.toFloat(),
            activityLevel = child.activityLevel,
            bloodGroup = child.bloodGroup ?: "",
            noKnownAllergies = child.noKnownAllergies,
            existingChildId = child.id,
            addMode = true
        )
    }

    fun updateStep1(
        name: String, fullName: String = "", birthdate: String, gender: String,
        allergies: String = "", notes: String = "",
        nationality: String = "", ethnicity: String = "", religion: String = "",
        relationship: String = "mother", avatarUri: String? = null, avatarBytes: ByteArray? = null
    ) {
        _uiState.value = _uiState.value.copy(
            childName = name, childFullName = fullName.ifBlank { _uiState.value.childFullName },
            birthdate = birthdate, gender = gender,
            allergies = allergies, notes = notes,
            nationality = nationality, ethnicity = ethnicity, religion = religion,
            relationship = relationship,
            childAvatarUri = avatarUri ?: _uiState.value.childAvatarUri,
            childAvatarBytes = avatarBytes ?: _uiState.value.childAvatarBytes
        )
    }

    fun updateStep2(height: String, weight: String, sleep: Float?, activity: String, bloodGroup: String = "") {
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

    fun setNoKnownAllergies(value: Boolean) {
        _uiState.value = _uiState.value.copy(noKnownAllergies = value)
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

                val targetChildId: String = if (state.existingChildId != null) {
                    val childId = state.existingChildId
                    val updates = buildMap<String, Any?> {
                        if (state.childName.isNotBlank()) put("name", state.childName)
                        if (state.childFullName.isNotBlank()) put("full_name", state.childFullName)
                        if (state.birthdate.isNotBlank()) put("birthdate", state.birthdate)
                        if (state.gender.isNotBlank()) put("gender", state.gender)
                        if (allergySummary.isNotBlank()) put("allergies", allergySummary)
                        if (combinedNotes.isNotBlank()) put("notes", combinedNotes)
                        state.heightCm.toDoubleOrNull()?.let { put("height_cm", it) }
                        state.weightKg.toDoubleOrNull()?.let { put("weight_kg", it) }
                        // Only when set, exactly like height_cm and weight_kg above. An
                        // untouched optional field must not write anything.
                        state.sleepHours?.let { put("sleep_hours", it.toDouble()) }
                        put("activity_level", state.activityLevel)
                        if (state.bloodGroup.isNotBlank()) put("blood_group", state.bloodGroup)
                        if (state.nationality.isNotBlank()) put("nationality", state.nationality)
                        if (state.ethnicity.isNotBlank()) put("ethnicity", state.ethnicity)
                        if (state.religion.isNotBlank()) put("religion", state.religion)
                        put("no_known_allergies", state.noKnownAllergies)
                    }
                    childRepo.updateChild(childId, updates)
                    childId
                } else {
                    val child = Child(
                        userId = userId,
                        name = state.childName,
                        fullName = state.childFullName.ifBlank { null },
                        birthdate = state.birthdate,
                        gender = state.gender,
                        allergies = allergySummary.ifBlank { null },
                        notes = combinedNotes.ifBlank { null },
                        heightCm = state.heightCm.toDoubleOrNull(),
                        weightKg = state.weightKg.toDoubleOrNull(),
                        sleepHours = state.sleepHours?.toDouble(),
                        activityLevel = state.activityLevel,
                        bloodGroup = state.bloodGroup.ifBlank { null },
                        nationality = state.nationality.ifBlank { null },
                        ethnicity = state.ethnicity.ifBlank { null },
                        religion = state.religion.ifBlank { null }
                    )
                    val savedChild = childRepo.createChild(child).getOrThrow()
                    com.lullabyai.app.data.settings.ChildSelection.set(savedChild.id)

                    val ageMonths = calculateAgeMonths(state.birthdate)
                    childRepo.insertDefaultMilestones(savedChild.id, ageMonths)
                    healthRepo.insertDefaultVaccines(savedChild.id, state.birthdate)

                    savedChild.id
                }

                // Upload child avatar if provided
                state.childAvatarBytes?.let { bytes ->
                    runCatching { childRepo.uploadChildAvatar(targetChildId, bytes) }
                }

                // Seed growth_records from Step 2 vitals
                val h = state.heightCm.toDoubleOrNull()
                val w = state.weightKg.toDoubleOrNull()
                if (w != null || h != null) {
                    healthRepo.addGrowthRecord(
                        GrowthRecord(
                            childId = targetChildId,
                            weightKg = w,
                            heightCm = h,
                            recordedAt = java.time.LocalDate.now().toString(),
                            recordType = "onboarding"
                        )
                    )
                }

                // Structured allergy list → allergies table.
                state.allergyEntries.forEach { a ->
                    healthRepo.addAllergy(targetChildId, a.name, "อื่นๆ", "ไม่ระบุ", a.symptoms.ifBlank { null })
                }
                if (state.noKnownAllergies) {
                    childRepo.setNoKnownAllergies(targetChildId, true)
                }

                // Uploaded medical documents → private bucket + metadata.
                state.pendingDocs.forEach { d ->
                    val category = if (d.name.contains("vaccine_book", ignoreCase = true)) "certs" else "other"
                    childRepo.uploadMedicalDocument(targetChildId, d.name, d.mime, d.bytes, category = category)
                }

                val history = MedicalHistory(
                    childId = targetChildId,
                    hasTakenMedication = state.hasTakenMedication,
                    medicationDetails = state.medicationDetails.ifBlank { null },
                    vaccinesGiven = state.selectedVaccines,
                    underlyingConditions = state.underlyingConditions.ifBlank { null }
                )
                childRepo.saveMedicalHistory(history)

                val noConditionMarkers = setOf("none", "no", "ไม่มี", "ไม่มีค่ะ", "ไม่มีครับ", "-")
                state.underlyingConditions.split(",", "\n")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it.lowercase() !in noConditionMarkers }
                    .forEach { name -> healthRepo.addChronicCondition(targetChildId, name, null) }

                val customVaccines = state.selectedVaccines.filter {
                    com.lullabyai.app.data.VaccineSetupCatalog.normalizeSelection(it) == null
                }
                if (state.selectedVaccines.isNotEmpty() || customVaccines.isNotEmpty()) {
                    healthRepo.applyOnboardingVaccineSelections(targetChildId, state.selectedVaccines, customVaccines)
                }

                if (!state.addMode) {
                    authRepo.updateProfile(null, null, null, state.relationship)
                }

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
