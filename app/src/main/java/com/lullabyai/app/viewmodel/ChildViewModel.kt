package com.lullabyai.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lullabyai.app.data.model.Allergy
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.DevelopmentMilestone
import com.lullabyai.app.data.model.GrowthRecord
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.data.model.AiTask
import com.lullabyai.app.data.model.VaccineRecord
import com.lullabyai.app.data.repository.AiRepository
import com.lullabyai.app.data.repository.ChildRepository
import com.lullabyai.app.data.repository.HealthRepository
import com.lullabyai.app.util.DoctorReportGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

data class ChildUiState(
    val isLoading: Boolean = false,
    val children: List<Child> = emptyList(),   // all children on this account (for the switcher)
    val child: Child? = null,
    val vaccines: List<VaccineRecord> = emptyList(),
    val growthRecords: List<GrowthRecord> = emptyList(),
    val healthRecords: List<HealthRecord> = emptyList(),
    val allergies: List<Allergy> = emptyList(),
    val milestones: List<DevelopmentMilestone> = emptyList(),
    val aiTasks: List<AiTask> = emptyList(),
    val tasksLoading: Boolean = false,
    val avatarUploading: Boolean = false,
    val reportLoading: Boolean = false,   // generating the doctor-export PDF
    val medicalDocuments: List<com.lullabyai.app.data.model.MedicalDocument> = emptyList(),
    val docUploading: Boolean = false,
    val medicationSchedules: List<com.lullabyai.app.data.model.MedicationSchedule> = emptyList(),
    val medicationDoses: List<com.lullabyai.app.data.model.MedicationDose> = emptyList(),
    val medicationSaving: Boolean = false,
    val sleepLogs: List<com.lullabyai.app.data.model.SleepLog> = emptyList(),
    val sleepSchedules: List<com.lullabyai.app.data.model.SleepSchedule> = emptyList(),
    val activeSleepSession: com.lullabyai.app.data.model.SleepLog? = null,
    val sleepSaving: Boolean = false,
    /** Short-lived signed URL for the child photo (private bucket). Use this to display the avatar. */
    val childAvatarSignedUrl: String? = null,
    // --- Health-record detail screen (#7) ---
    val openHealthRecordId: String? = null,         // record currently shown on the detail screen
    val healthDetailAdvice: String? = null,         // one-shot AI advice for the open record
    val healthDetailAdviceLoading: Boolean = false,
    val healthPhotoSignedUrl: String? = null,       // signed URL for the open record's photo
    val healthPhotoUploading: Boolean = false,
    val healthDetailSaving: Boolean = false,
    val error: String? = null
)

class ChildViewModel : ViewModel() {
    private val childRepo = ChildRepository()
    private val healthRepo = HealthRepository()
    private val medicationRepo = com.lullabyai.app.data.repository.MedicationRepository()
    private val sleepRepo = com.lullabyai.app.data.repository.SleepRepository()
    private val aiRepo = AiRepository()
    private val chatRepo = com.lullabyai.app.data.repository.ChatRepository()
    private val notifRepo = com.lullabyai.app.data.repository.NotificationRepository()

    private val _uiState = MutableStateFlow(ChildUiState())
    val uiState: StateFlow<ChildUiState> = _uiState

    init { loadChild() }

    fun loadChild() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val children = childRepo.getChildren().getOrDefault(emptyList())
            childRepo.getSelectedChild()
                .onSuccess { child ->
                    val signed = com.lullabyai.app.data.remote.StorageHelper.signedAvatarUrl(child?.avatarUrl)
                    _uiState.value = _uiState.value.copy(children = children, child = child, childAvatarSignedUrl = signed, isLoading = false)
                    child?.let { loadAllData(it.id) }
                }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false, error = it.message) }
        }
    }

    /**
     * Builds a doctor-export PDF for the active child (profile, growth, allergies, vaccines, health
     * records, milestones, and an AI summary of recent parent concerns) and returns the File so the
     * screen can share it. Off the main thread; uses already-loaded uiState plus a couple of fetches.
     */
    fun exportDoctorReport(context: android.content.Context, isThai: Boolean, onResult: (java.io.File?, String?) -> Unit) {
        val st = _uiState.value
        val child = st.child ?: run { onResult(null, "no child selected"); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(reportLoading = true)
            val result = runCatching {
                val med = childRepo.getMedicalHistory(child.id).getOrNull()
                val concerns = runCatching {
                    val sessions = chatRepo.getSessions().getOrDefault(emptyList())
                    val msgs = sessions.firstOrNull()?.let { chatRepo.getMessages(it.id).getOrDefault(emptyList()) } ?: emptyList()
                    msgs.filter { it.role == "user" }.takeLast(12).joinToString("\n") { it.content }
                }.getOrDefault("")

                val achieved = st.milestones.count { it.isAchieved }
                val pending = st.milestones.filter { !it.isAchieved }

                // AI summary for the doctor
                val aiPrompt = buildString {
                    append(if (isThai) "สรุปภาพรวมสุขภาพและพัฒนาการของเด็กแบบกระชับเป็นภาษาไทยสำหรับแพทย์ 3-5 ประโยค จากข้อมูลนี้ และระบุข้อกังวลของผู้ปกครองหากมี\n"
                           else "Summarize this child's health and development concisely for a doctor in 3-5 sentences, noting parent concerns if any.\n")
                    val genderForAi = when (child.gender.lowercase()) { "male","boy","m","ชาย" -> "ชาย"; "female","girl","f","หญิง" -> "หญิง"; else -> "ไม่ระบุ" }
                    append("ชื่อ ${child.name}, อายุ ${calculateAge(isThai)}, เพศ $genderForAi\n")
                    append("ส่วนสูง ${child.heightCm ?: "-"} ซม., น้ำหนัก ${child.weightKg ?: "-"} กก.\n")
                    append("แพ้: ${st.allergies.joinToString(", ") { it.name }.ifBlank { "ไม่มีข้อมูล" }}\n")
                    append("โรคประจำตัว: ${med?.underlyingConditions ?: "ไม่มีข้อมูล"}\n")
                    append("พัฒนาการ: ทำได้ $achieved/${st.milestones.size}\n")
                    if (concerns.isNotBlank()) append("ข้อกังวลของผู้ปกครองจากการสนทนา:\n$concerns\n")
                    append(if (isThai) "ตอบเป็นย่อหน้าเดียว ไม่ใช้สัญลักษณ์พิเศษ" else "Reply as one paragraph, no special symbols.")
                }
                val aiSummary = runCatching {
                    com.lullabyai.app.data.remote.ClaudeClient.generateContent(aiPrompt, useSearch = false, maxTokens = 800)
                }.getOrNull()?.takeIf { it.isNotBlank() && !it.contains("ขออภัย") }
                    ?: (if (isThai) "ไม่สามารถสร้างบทสรุป AI ได้ในขณะนี้" else "AI summary unavailable.")

                // ----- Map the child's data onto the official "ข้อมูลเด็ก" form (Thai, 6 sections) -----
                fun d10(s: String?) = com.lullabyai.app.util.TimeUtils.localDate(s) ?: ""   // ISO → local yyyy-MM-dd
                val today = java.time.LocalDate.now()
                val reportId = "NR-" + child.id.replace("-", "").take(8).uppercase() + "-" + today.toString().replace("-", "")
                val recordDate = "%02d/%02d/%04d".format(today.dayOfMonth, today.monthValue, today.year + 543)
                val genderTh = when (child.gender.lowercase()) {
                    "male", "boy", "m", "ชาย" -> "ชาย"
                    "female", "girl", "f", "หญิง" -> "หญิง"
                    else -> "ไม่ระบุ"
                }

                // Child age in whole months (for development "due" checks).
                val birth = runCatching { java.time.LocalDate.parse(child.birthdate) }.getOrNull()
                val ageMonths = birth?.let { val pp = java.time.Period.between(it, today); pp.years * 12 + pp.months } ?: Int.MAX_VALUE

                // §2 vaccines actually received (completed).
                val vacRows = st.vaccines.filter { it.status == "completed" }
                    .sortedBy { it.ageOrder ?: 0 }
                    .map { DoctorReportGenerator.Vac(it.ageGroup ?: "", it.vaccineName, d10(it.dateGiven), "", it.notes ?: it.description ?: "") }

                // §3 development — one status row per official domain (mapped from milestone categories).
                val domainCats = listOf("gross_motor", "fine_motor", "communication", "social", "self_help")
                val devStatuses = domainCats.map { cat ->
                    val inDomain = st.milestones.filter { it.category == cat }
                    val due = inDomain.filter { (it.ageMonthsExpected ?: Int.MAX_VALUE) <= ageMonths }
                    val pendingDue = due.filter { !it.isAchieved }
                    val hasData = inDomain.isNotEmpty()
                    val monitor = pendingDue.isNotEmpty()
                    DoctorReportGenerator.DevStatus(
                        normal = hasData && !monitor,
                        monitor = monitor,
                        note = if (monitor) "ควรติดตาม ${pendingDue.size} รายการ" else ""
                    )
                }
                val devObservation = pending.filter { (it.ageMonthsExpected ?: Int.MAX_VALUE) <= ageMonths }
                    .take(8).joinToString("\n") { "• ${it.milestoneName}" }

                // §4 growth history.
                val growthRows = st.growthRecords.map {
                    DoctorReportGenerator.Growth(d10(it.recordedAt), it.weightKg?.let { w -> "$w" } ?: "", it.heightCm?.let { h -> "$h" } ?: "", "", it.notes ?: "")
                }
                val foodAllergies = st.allergies.filter { it.type == "food" }.joinToString(", ") { it.name }
                val allAllergies = st.allergies.joinToString(", ") {
                    it.name + (it.symptoms?.takeIf { sm -> sm.isNotBlank() }?.let { sm -> " ($sm)" } ?: "")
                }

                // §5 health-history detail = recent health records.
                val healthDetail = st.healthRecords.take(10).joinToString("\n") {
                    "${d10(it.recordedAt)}  ${it.title}" + (it.temperature?.let { t -> "  ${t}°C" } ?: "") +
                        (it.description?.takeIf { dsc -> dsc.isNotBlank() }?.let { dsc -> " — $dsc" } ?: "")
                }

                // §6 recommendations = overdue vaccines + pending milestones (concrete follow-ups).
                val overdueVac = st.vaccines.filter { it.status == "overdue" }
                val recommendations = buildString {
                    if (overdueVac.isNotEmpty()) append("วัคซีนที่เลยกำหนด: " + overdueVac.joinToString(", ") { it.vaccineName } + "\n")
                    val devFollow = devStatuses.withIndex().filter { it.value.monitor }
                        .joinToString(", ") { DoctorReportGenerator.DEV_DOMAINS[it.index] }
                    if (devFollow.isNotBlank()) append("ด้านพัฒนาการที่ควรติดตาม: $devFollow")
                }.trim()

                val formData = DoctorReportGenerator.ChildData(
                    documentRef = reportId, recordDate = recordDate,
                    // §1
                    childCode = child.id.replace("-", "").take(8).uppercase(),
                    nickname = child.name, birthdate = child.birthdate, age = calculateAge(true),
                    gender = genderTh, idNumber = "", unitClass = "",
                    recorder = "ผู้ปกครอง", updatedDate = d10(child.updatedAt).ifBlank { today.toString() },
                    // §2
                    vaccines = vacRows,
                    // §3
                    development = devStatuses, devObservation = devObservation,
                    // §4
                    regularFood = "", allergyAvoid = foodAllergies, eatingProblem = "",
                    growth = growthRows, nutritionAdvice = "",
                    // §5
                    chronicDisease = med?.underlyingConditions ?: "",
                    drugFoodAllergy = allAllergies,
                    medications = med?.medicationDetails ?: "",
                    majorIllness = "", surgeryHospital = "",
                    followupDoctor = "",
                    healthHistoryDetail = healthDetail, currentCare = "",
                    // §6
                    overallSummary = aiSummary, recommendations = recommendations
                )
                DoctorReportGenerator.generate(context, formData)
            }
            result.exceptionOrNull()?.let { android.util.Log.e("DoctorReport", "export failed", it) }
            _uiState.value = _uiState.value.copy(reportLoading = false)
            onResult(result.getOrNull(), result.exceptionOrNull()?.message)
        }
    }

    /** Delete a child (after the user confirms). Falls back to another child, or none if it was the last. */
    fun deleteChild(childId: String) {
        viewModelScope.launch {
            childRepo.deleteChild(childId)
            // If the deleted child was active, clear the selection so the next load picks another.
            if (com.lullabyai.app.data.settings.ChildSelection.current() == childId) {
                com.lullabyai.app.data.settings.ChildSelection.set(null)
            }
            vaccineSeededChildId = null
            milestonesEnsuredChildId = null
            _uiState.value = _uiState.value.copy(
                child = null, vaccines = emptyList(), growthRecords = emptyList(),
                healthRecords = emptyList(), allergies = emptyList(), milestones = emptyList(),
                aiTasks = emptyList(), childAvatarSignedUrl = null
            )
            loadChild()
        }
    }

    /** Switch the active child (multi-child). Persists the selection and reloads everything for it. */
    fun selectChild(childId: String) {
        if (childId == _uiState.value.child?.id) return
        com.lullabyai.app.data.settings.ChildSelection.set(childId)
        // Reset per-child guards so the new child seeds its milestones/vaccines and reloads cleanly.
        vaccineSeededChildId = null
        milestonesEnsuredChildId = null
        // Clear the previous child's data so stale info never shows during the switch.
        _uiState.value = _uiState.value.copy(
            child = null, vaccines = emptyList(), growthRecords = emptyList(),
            healthRecords = emptyList(), allergies = emptyList(), milestones = emptyList(),
            aiTasks = emptyList(), childAvatarSignedUrl = null
        )
        loadChild()
    }

    private fun loadAllData(childId: String) {
        viewModelScope.launch {
            // Recompute statuses against today's date first so the recommended vaccine stays current
            healthRepo.refreshVaccineStatuses(childId)
            healthRepo.getVaccineRecords(childId).onSuccess {
                _uiState.value = _uiState.value.copy(vaccines = it)
            }
        }
        viewModelScope.launch {
            healthRepo.getGrowthRecords(childId).onSuccess {
                _uiState.value = _uiState.value.copy(growthRecords = it)
            }
        }
        viewModelScope.launch {
            healthRepo.getHealthRecords(childId).onSuccess {
                _uiState.value = _uiState.value.copy(healthRecords = it)
            }
        }
        viewModelScope.launch {
            childRepo.getMilestones(childId).onSuccess {
                _uiState.value = _uiState.value.copy(milestones = it)
            }
        }
        viewModelScope.launch {
            healthRepo.getAllergies(childId).onSuccess {
                _uiState.value = _uiState.value.copy(allergies = it)
            }
        }
    }

    private fun reloadAllergies() {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            healthRepo.getAllergies(childId).onSuccess { _uiState.value = _uiState.value.copy(allergies = it) }
        }
    }

    fun addAllergy(name: String, type: String, severity: String, symptoms: String?) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            healthRepo.addAllergy(childId, name, type, severity, symptoms).onSuccess { reloadAllergies() }
        }
    }

    fun updateAllergy(id: String, name: String, type: String, severity: String, symptoms: String?) {
        viewModelScope.launch {
            healthRepo.updateAllergy(id, name, type, severity, symptoms).onSuccess { reloadAllergies() }
        }
    }

    fun deleteAllergy(id: String) {
        viewModelScope.launch {
            healthRepo.deleteAllergy(id).onSuccess { reloadAllergies() }
        }
    }

    /** Sync AI daily tasks: daily refresh, 5h-after-complete replacement, refill to 4. */
    fun loadTasks(child: Child) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(tasksLoading = true)
            // Ground the AI activities in the child's not-yet-achieved, age-appropriate official milestones.
            val ageM = getAgeMonths()
            val hints = _uiState.value.milestones
                .filter { !it.isAchieved && (it.ageMonthsExpected ?: 0) <= ageM }
                .sortedBy { it.ageMonthsExpected ?: 0 }
                .map { it.milestoneName }
            val tasks = aiRepo.syncTasks(child, hints)
            _uiState.value = _uiState.value.copy(aiTasks = tasks, tasksLoading = false)
        }
    }

    /** Toggle a task's done status (select / deselect). */
    fun toggleTask(taskId: String, currentlyCompleted: Boolean) {
        viewModelScope.launch {
            if (currentlyCompleted) aiRepo.uncompleteTask(taskId) else aiRepo.completeTask(taskId)
            val childId = _uiState.value.child?.id ?: return@launch
            _uiState.value = _uiState.value.copy(aiTasks = aiRepo.getActiveTasks(childId))
        }
    }

    /** "Not available" button — delete the task and generate a replacement. */
    fun notAvailableTask(taskId: String) {
        viewModelScope.launch {
            val child = _uiState.value.child ?: return@launch
            val ageM = getAgeMonths()
            val hints = _uiState.value.milestones
                .filter { !it.isAchieved && (it.ageMonthsExpected ?: 0) <= ageM }
                .sortedBy { it.ageMonthsExpected ?: 0 }
                .map { it.milestoneName }
            aiRepo.replaceTask(taskId, child, hints)
            _uiState.value = _uiState.value.copy(aiTasks = aiRepo.getActiveTasks(child.id))
        }
    }

    fun loadTaskHistory(onLoaded: (List<AiTask>) -> Unit) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            onLoaded(aiRepo.getHistoryTasks(childId))
        }
    }

    /** Upload a new child photo (raw image bytes), then update the child in state. */
    fun uploadChildAvatar(bytes: ByteArray) {
        viewModelScope.launch {
            val current = _uiState.value.child ?: return@launch
            _uiState.value = _uiState.value.copy(avatarUploading = true, error = null)
            childRepo.uploadChildAvatar(current.id, bytes)
                .onSuccess { signedUrl ->
                    // repo returns a fresh signed URL — display it immediately
                    _uiState.value = _uiState.value.copy(
                        childAvatarSignedUrl = signedUrl,
                        avatarUploading = false
                    )
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        avatarUploading = false,
                        error = "อัปโหลดรูปไม่สำเร็จ กรุณาลองใหม่อีกครั้ง"
                    )
                }
        }
    }

    fun updateChildInfo(
        name: String, birthdate: String, weightKg: Double?, heightCm: Double?, notes: String?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val current = _uiState.value.child ?: return@launch
            val updated = current.copy(
                name = name,
                birthdate = birthdate,
                weightKg = weightKg,
                heightCm = heightCm,
                notes = notes
            )
            childRepo.updateChild(updated)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(child = updated)
                    notifRepo.notify(
                        com.lullabyai.app.data.repository.NotifCategory.NORMAL,
                        "อัปเดตข้อมูลลูกแล้ว",
                        "ข้อมูลของ ${updated.name} ได้รับการบันทึกเรียบร้อย"
                    )
                    onDone()
                }
                .onFailure { _uiState.value = _uiState.value.copy(error = it.message) }
        }
    }

    fun updateMilestone(milestone: DevelopmentMilestone) {
        viewModelScope.launch {
            childRepo.updateMilestone(milestone).onSuccess { loadChild() }
        }
    }

    // Tops up the milestone checklist once per child per session (existing children seeded with the
    // older sparse list pick up the full 0–3y set). Idempotent on the repo side; flag avoids re-runs.
    private var milestonesEnsuredChildId: String? = null
    fun ensureDefaultMilestones() {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            if (milestonesEnsuredChildId == childId) return@launch
            milestonesEnsuredChildId = childId
            childRepo.ensureDefaultMilestones(childId)
            childRepo.getMilestones(childId).onSuccess { _uiState.value = _uiState.value.copy(milestones = it) }
        }
    }

    /** Toggle a milestone achieved/not, then refresh the list. */
    fun setMilestoneAchieved(id: String, achieved: Boolean) {
        viewModelScope.launch {
            childRepo.setMilestoneAchieved(id, achieved)
            val childId = _uiState.value.child?.id ?: return@launch
            childRepo.getMilestones(childId).onSuccess { _uiState.value = _uiState.value.copy(milestones = it) }
        }
    }

    /** Save (or clear) the reason a milestone is skipped, then refresh the list. */
    fun setMilestoneSkipReason(id: String, reason: String?) {
        viewModelScope.launch {
            childRepo.setMilestoneSkipReason(id, reason)
            val childId = _uiState.value.child?.id ?: return@launch
            childRepo.getMilestones(childId).onSuccess { _uiState.value = _uiState.value.copy(milestones = it) }
        }
    }

    /** Load the current child's uploaded medical documents (for the Edit Child screen). */
    fun loadMedicalDocuments() {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            childRepo.getMedicalDocuments(childId).onSuccess {
                _uiState.value = _uiState.value.copy(medicalDocuments = it)
            }
        }
    }

    /** Upload a medical document (PDF/JPG/PNG) for the current child to the private bucket. */
    fun uploadMedicalDocument(name: String, mime: String?, bytes: ByteArray) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            _uiState.value = _uiState.value.copy(docUploading = true)
            childRepo.uploadMedicalDocument(childId, name, mime, bytes)
            childRepo.getMedicalDocuments(childId).onSuccess {
                _uiState.value = _uiState.value.copy(medicalDocuments = it)
            }
            _uiState.value = _uiState.value.copy(docUploading = false)
        }
    }

    /** Remove an uploaded medical document for the current child. */
    fun deleteMedicalDocument(id: String, filePath: String) {
        viewModelScope.launch {
            childRepo.deleteMedicalDocument(id, filePath)
            val childId = _uiState.value.child?.id ?: return@launch
            childRepo.getMedicalDocuments(childId).onSuccess {
                _uiState.value = _uiState.value.copy(medicalDocuments = it)
            }
        }
    }

    /** Load the current child's medication schedules + individual dose timeline. */
    fun loadMedications() {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            medicationRepo.getSchedules(childId).onSuccess { _uiState.value = _uiState.value.copy(medicationSchedules = it) }
            medicationRepo.getDoses(childId).onSuccess { _uiState.value = _uiState.value.copy(medicationDoses = it) }
        }
    }

    fun addMedicationSchedule(
        medicineName: String, dosage: Double?, dosageUnit: String, frequency: String,
        scheduleType: String, startDate: String?, endDate: String?, numDoses: Int?, notes: String?,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            _uiState.value = _uiState.value.copy(medicationSaving = true)
            medicationRepo.createSchedule(childId, medicineName, dosage, dosageUnit, frequency, scheduleType, startDate, endDate, numDoses, notes)
            loadMedications()
            _uiState.value = _uiState.value.copy(medicationSaving = false)
            onDone()
        }
    }

    fun markDoseTaken(id: String) {
        viewModelScope.launch { medicationRepo.markDoseTaken(id); loadMedications() }
    }

    fun skipDose(id: String) {
        viewModelScope.launch { medicationRepo.skipDose(id); loadMedications() }
    }

    fun deleteMedicationSchedule(id: String) {
        viewModelScope.launch { medicationRepo.deleteSchedule(id); loadMedications() }
    }

    /** Load the current child's sleep sessions + recurring schedules. */
    fun loadSleep() {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            sleepRepo.getLogs(childId).onSuccess { logs ->
                _uiState.value = _uiState.value.copy(sleepLogs = logs, activeSleepSession = logs.firstOrNull { it.endTime == null })
            }
            sleepRepo.getSchedules(childId).onSuccess { _uiState.value = _uiState.value.copy(sleepSchedules = it) }
        }
    }

    /** One-tap sleep tracking: starts a session if none is active, otherwise ends the active one. */
    fun toggleSleepSession(sleepType: String = "main", mood: String? = null) {
        // Guard against a double-tap firing two inserts: startSession/endSession are both async,
        // so without this a second tap before loadSleep() refreshes state would still see the old
        // activeSleepSession and repeat the same action (e.g. two "start" calls → two open sessions).
        if (_uiState.value.sleepSaving) return
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            _uiState.value = _uiState.value.copy(sleepSaving = true)
            val active = _uiState.value.activeSleepSession
            if (active != null) sleepRepo.endSession(active.id, mood)
            else sleepRepo.startSession(childId, sleepType)
            loadSleep()
            _uiState.value = _uiState.value.copy(sleepSaving = false)
        }
    }

    fun addManualSleepLog(sleepType: String, start: String, end: String) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            _uiState.value = _uiState.value.copy(sleepSaving = true)
            sleepRepo.addManualLog(childId, sleepType, start, end)
            loadSleep()
            _uiState.value = _uiState.value.copy(sleepSaving = false)
        }
    }

    fun deleteSleepLog(id: String) {
        viewModelScope.launch { sleepRepo.deleteLog(id); loadSleep() }
    }

    fun addSleepSchedule(label: String, sleepType: String, startTime: String, endTime: String, daysOfWeek: String, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            _uiState.value = _uiState.value.copy(sleepSaving = true)
            sleepRepo.createSchedule(childId, label, sleepType, startTime, endTime, daysOfWeek)
            loadSleep()
            _uiState.value = _uiState.value.copy(sleepSaving = false)
            onDone()
        }
    }

    fun deleteSleepSchedule(id: String) {
        viewModelScope.launch { sleepRepo.deleteSchedule(id); loadSleep() }
    }

    fun addGrowthRecord(record: GrowthRecord) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            healthRepo.addGrowthRecord(record.copy(childId = childId)).onSuccess {
                healthRepo.getGrowthRecords(childId).onSuccess {
                    _uiState.value = _uiState.value.copy(growthRecords = it)
                }
            }
        }
    }

    fun addHealthRecord(record: HealthRecord) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            healthRepo.addHealthRecord(record.copy(childId = childId)).onSuccess {
                healthRepo.getHealthRecords(childId).onSuccess {
                    _uiState.value = _uiState.value.copy(healthRecords = it)
                }
                // High temperature → warning notification, otherwise normal
                val temp = record.temperature
                if (temp != null && temp >= 37.5) {
                    notifRepo.notify(
                        com.lullabyai.app.data.repository.NotifCategory.WARNING,
                        "พบอุณหภูมิสูง",
                        "บันทึกอุณหภูมิ $temp°C — หากมีไข้สูงต่อเนื่องควรปรึกษาแพทย์"
                    )
                } else {
                    notifRepo.notify(
                        com.lullabyai.app.data.repository.NotifCategory.NORMAL,
                        "บันทึกสุขภาพใหม่",
                        record.title
                    )
                }
            }
        }
    }

    /** Remember which record the detail screen should open (the app passes selection via VM state,
     *  not nav args — same pattern as RecipeDetail/ExerciseInstruction). */
    fun selectHealthRecord(recordId: String) {
        _uiState.value = _uiState.value.copy(openHealthRecordId = recordId)
    }

    /**
     * Opens the health-record detail (#7): signs the attached photo (if any) and generates a
     * short, one-shot AI care suggestion grounded in this record's temperature + symptoms + notes.
     */
    fun loadHealthDetail(recordId: String, isThai: Boolean) {
        val record = _uiState.value.healthRecords.firstOrNull { it.id == recordId } ?: return
        viewModelScope.launch {
            val signed = com.lullabyai.app.data.remote.StorageHelper.signedAvatarUrl(record.photoUrl)
            _uiState.value = _uiState.value.copy(
                healthPhotoSignedUrl = signed,
                healthDetailAdvice = null,
                healthDetailAdviceLoading = true
            )
            val child = _uiState.value.child
            val temp = record.temperature?.let { "${it}°C" } ?: (if (isThai) "ไม่ได้บันทึก" else "not recorded")
            val symptoms = (listOf(record.title) + (record.additionalSymptoms ?: emptyList()))
                .filter { it.isNotBlank() }.joinToString(", ")
            val notes = record.description?.takeIf { it.isNotBlank() } ?: (if (isThai) "ไม่มี" else "none")
            val prompt = if (isThai)
                "เด็กอายุ ${child?.let { calculateAge(true) } ?: "-"} มีบันทึกสุขภาพดังนี้ " +
                "อุณหภูมิร่างกาย $temp อาการที่พบ $symptoms บันทึกเพิ่มเติมจากผู้ปกครอง: $notes " +
                "ช่วยให้คำแนะนำการดูแลเบื้องต้นสำหรับคุณพ่อคุณแม่ สั้นกระชับ 3-4 ประโยค " +
                "บอกสัญญาณที่ควรพาไปพบแพทย์ด้วย ใช้ภาษาพูดที่อบอุ่นเป็นกันเอง ไม่ใช้สัญลักษณ์หรืออิโมจิ"
            else
                "A child aged ${child?.let { calculateAge(false) } ?: "-"} has this health log: " +
                "body temperature $temp, symptoms $symptoms, parent notes: $notes. " +
                "Give brief first-care guidance for the parent in 3-4 sentences, including warning " +
                "signs that warrant seeing a doctor. Warm, plain language, no symbols or emoji."
            val advice = com.lullabyai.app.data.remote.ClaudeClient.generateContent(prompt, useSearch = false, maxTokens = 500)
            _uiState.value = _uiState.value.copy(healthDetailAdvice = advice, healthDetailAdviceLoading = false)
        }
    }

    /** Upload/replace the photo on a health record, persist its path, and refresh the list + preview. */
    fun uploadHealthPhoto(recordId: String, bytes: ByteArray) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            val record = _uiState.value.healthRecords.firstOrNull { it.id == recordId } ?: return@launch
            _uiState.value = _uiState.value.copy(healthPhotoUploading = true, error = null)
            healthRepo.uploadHealthPhoto(childId, recordId, bytes)
                .onSuccess { path ->
                    healthRepo.updateHealthRecord(
                        recordId, record.title, record.description,
                        record.additionalSymptoms ?: emptyList(), path, record.temperature
                    )
                    val signed = com.lullabyai.app.data.remote.StorageHelper.signedAvatarUrl(path)
                    healthRepo.getHealthRecords(childId).onSuccess {
                        _uiState.value = _uiState.value.copy(healthRecords = it)
                    }
                    _uiState.value = _uiState.value.copy(healthPhotoSignedUrl = signed, healthPhotoUploading = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        healthPhotoUploading = false,
                        error = "อัปโหลดรูปไม่สำเร็จ กรุณาลองใหม่อีกครั้ง"
                    )
                }
        }
    }

    /** Save edits to a health record from the detail screen, then refresh the list. */
    fun saveHealthRecord(
        recordId: String, title: String, description: String?,
        additionalSymptoms: List<String>, temperature: Double?, onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            val current = _uiState.value.healthRecords.firstOrNull { it.id == recordId }
            _uiState.value = _uiState.value.copy(healthDetailSaving = true)
            healthRepo.updateHealthRecord(
                recordId, title, description, additionalSymptoms, current?.photoUrl, temperature
            ).onSuccess {
                healthRepo.getHealthRecords(childId).onSuccess {
                    _uiState.value = _uiState.value.copy(healthRecords = it)
                }
            }
            _uiState.value = _uiState.value.copy(healthDetailSaving = false)
            onDone()
        }
    }

    // Maps a Thai age-group label to its month order so an added vaccine sorts into the right category.
    private fun ageOrderForGroup(group: String): Int = when (group.trim()) {
        "แรกเกิด" -> 0
        "1 เดือน" -> 1
        "2 เดือน" -> 2
        "4 เดือน" -> 4
        "6 เดือน" -> 6
        "9 เดือน" -> 9
        "1 ปี" -> 12
        "1 ปี 6 เดือน" -> 18
        "2 ปี" -> 24
        "2 ปี 6 เดือน" -> 30
        "3 ปี" -> 36
        else -> 999  // "อื่นๆ" / custom → sorts last
    }

    fun addVaccine(name: String, ageGroup: String, scheduledDate: String?, description: String?, place: String? = null) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            val rec = VaccineRecord(
                childId = childId, vaccineName = name, description = description, place = place,
                ageGroup = ageGroup, ageOrder = ageOrderForGroup(ageGroup), scheduledDate = scheduledDate,
                status = if (scheduledDate != null) "appointment" else "upcoming"
            )
            healthRepo.addVaccineRecord(rec).onSuccess {
                healthRepo.getVaccineRecords(childId).onSuccess { _uiState.value = _uiState.value.copy(vaccines = it) }
                notifRepo.notify(
                    com.lullabyai.app.data.repository.NotifCategory.SCHEDULE,
                    "เพิ่มวัคซีนใหม่",
                    "$name${if (scheduledDate != null) " • นัดวันที่ $scheduledDate" else ""}"
                )
            }
        }
    }

    /**
     * Safety net: if the child has no vaccine records yet (e.g. created before the schedule
     * was auto-seeded at onboarding), seed the full Thai EPI 0–3y schedule and reload.
     */
    // Guards against the seeding race: the screen's LaunchedEffect can fire ensureVaccineSchedule()
    // several times in quick succession (recompositions), and two coroutines could each read an
    // empty table before either insert lands — producing 2–4× duplicate schedules. We serialize on
    // a Mutex AND remember which child we've already ensured this session.
    private val vaccineSeedMutex = kotlinx.coroutines.sync.Mutex()
    private var vaccineSeededChildId: String? = null

    fun ensureVaccineSchedule() {
        viewModelScope.launch {
            val child = _uiState.value.child ?: return@launch
            vaccineSeedMutex.withLock {
                if (vaccineSeededChildId != child.id) {
                    // Re-check the DB directly (not in-memory state) so we don't seed duplicates
                    // while the initial vaccine load is still in flight.
                    val existing = healthRepo.getVaccineRecords(child.id).getOrDefault(emptyList())
                    if (existing.isEmpty()) {
                        healthRepo.insertDefaultVaccines(child.id, child.birthdate)
                    }
                    vaccineSeededChildId = child.id
                }
            }
            // Keep statuses current with the child's age so the recommended vaccine advances over time
            healthRepo.refreshVaccineStatuses(child.id)
            healthRepo.getVaccineRecords(child.id).onSuccess {
                _uiState.value = _uiState.value.copy(vaccines = it)
            }
        }
    }

    /** Edit / reschedule a vaccine (name, appointment date, description, place). */
    fun updateVaccineSchedule(record: VaccineRecord, name: String, scheduledDate: String?, description: String?, place: String?) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            healthRepo.updateVaccineSchedule(record.id, name, scheduledDate, description, place).onSuccess {
                healthRepo.getVaccineRecords(childId).onSuccess {
                    _uiState.value = _uiState.value.copy(vaccines = it)
                }
                notifRepo.notify(
                    com.lullabyai.app.data.repository.NotifCategory.SCHEDULE,
                    "อัปเดตกำหนดการวัคซีน",
                    "$name${if (scheduledDate != null) " • นัดวันที่ $scheduledDate" else ""}"
                )
            }
        }
    }

    /** Mark a vaccine as skipped with a reason (like skipping a development milestone). */
    fun skipVaccine(record: VaccineRecord, reason: String) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            healthRepo.skipVaccine(record.id, reason.ifBlank { null }).onSuccess {
                healthRepo.getVaccineRecords(childId).onSuccess {
                    _uiState.value = _uiState.value.copy(vaccines = it)
                }
            }
        }
    }

    /** Delete a vaccine record (called after the user confirms). */
    fun deleteVaccine(record: VaccineRecord) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            healthRepo.deleteVaccineRecord(record.id).onSuccess {
                healthRepo.getVaccineRecords(childId).onSuccess {
                    _uiState.value = _uiState.value.copy(vaccines = it)
                }
                notifRepo.notify(
                    com.lullabyai.app.data.repository.NotifCategory.NORMAL,
                    "ลบรายการวัคซีน",
                    "ลบ ${record.vaccineName} ออกจากสมุดวัคซีนแล้ว"
                )
            }
        }
    }

    fun toggleVaccine(record: VaccineRecord) {
        viewModelScope.launch {
            val childId = _uiState.value.child?.id ?: return@launch
            val nowComplete = record.status != "completed"
            val today = java.time.LocalDate.now().toString()
            healthRepo.updateVaccineStatus(
                record.id,
                if (nowComplete) "completed" else "upcoming",
                if (nowComplete) today else null
            ).onSuccess {
                healthRepo.getVaccineRecords(childId).onSuccess { _uiState.value = _uiState.value.copy(vaccines = it) }
                if (nowComplete) {
                    notifRepo.notify(
                        com.lullabyai.app.data.repository.NotifCategory.NORMAL,
                        "บันทึกการฉีดวัคซีน",
                        "${record.vaccineName} เสร็จสิ้นแล้ว"
                    )
                }
            }
        }
    }

    fun calculateAge(isThai: Boolean = true): String {
        val child = _uiState.value.child ?: return ""
        return try {
            val parts = child.birthdate.split("-")
            val year = parts[0].toInt(); val month = parts[1].toInt()
            val now = java.util.Calendar.getInstance()
            var years = now.get(java.util.Calendar.YEAR) - year
            var months = now.get(java.util.Calendar.MONTH) + 1 - month
            if (months < 0) { years--; months += 12 }
            if (isThai) {
                if (years > 0) "$years ปี $months เดือน" else "$months เดือน"
            } else {
                if (years > 0) "$years y $months mo" else "$months mo"
            }
        } catch (e: Exception) { "" }
    }

    fun getAgeMonths(): Int {
        val child = _uiState.value.child ?: return 0
        return try {
            val parts = child.birthdate.split("-")
            val year = parts[0].toInt(); val month = parts[1].toInt()
            val now = java.util.Calendar.getInstance()
            (now.get(java.util.Calendar.YEAR) - year) * 12 + (now.get(java.util.Calendar.MONTH) + 1 - month)
        } catch (e: Exception) { 0 }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
