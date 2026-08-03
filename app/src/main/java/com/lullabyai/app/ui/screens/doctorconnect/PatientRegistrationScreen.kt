@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctorconnect

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.MedicalInformation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.Child
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.data.settings.ChildSelection
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.Error
import com.lullabyai.app.ui.theme.HealthGood
import com.lullabyai.app.ui.theme.Outline
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.viewmodel.ChildViewModel

/** doctorId sentinel for a registration started from Pedia Connect, before any clinic is chosen.
 *  The route takes a non-optional path arg, so "no clinic" is encoded rather than routed separately. */
const val PATIENT_REGISTRATION_NO_DOCTOR = "none"

/**
 * "ลงทะเบียนผู้ป่วย" — the high-fidelity hospital intake form.
 *
 * Reached two ways: from a doctor (Search Doctor / reservation) with a real `doctorId`, which also
 * files an appointment request for the clinic to approve; or from Pedia Connect with
 * [PATIENT_REGISTRATION_NO_DOCTOR], which only saves the **Pedia Connect** patient record (separate
 * from app child onboarding — pick an app child to sync fields into the form, or add an app child
 * first via the dashboard dialog).
 *
 * The guardian block supports both modes the owner may need:
 *  - [GuardianSource.FROM_APP] — pick an account that already has access to this child
 *    (`child_access_members`), for co-parents who are app users;
 *  - [GuardianSource.MANUAL] — type in a guardian who is *not* an app user.
 */
@Composable
fun PatientRegistrationScreen(doctorId: String, childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val vm: PatientIntakeViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    val childState by childViewModel.uiState.collectAsState()

    val clinicDoctorId = doctorId.takeIf { it.isNotBlank() && it != PATIENT_REGISTRATION_NO_DOCTOR }

    val selectedChildId by ChildSelection.id.collectAsState()
    val selectedChild = remember(selectedChildId, childState.children) {
        selectedChildId?.let { id -> childState.children.firstOrNull { it.id == id } }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    // Editable child fields, seeded from the synced record whenever the selection changes.
    var name by remember { mutableStateOf("") }
    var birthdate by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("unspecified") }
    var bloodGroup by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var conditions by remember { mutableStateOf("") }

    var guardianSource by remember { mutableStateOf(GuardianSource.MANUAL) }
    var selectedGuardianUserId by remember { mutableStateOf<String?>(null) }
    var guardianName by remember { mutableStateOf("") }
    var guardianRelationship by remember { mutableStateOf(if (s.isThai) "มารดา" else "Mother") }
    var guardianPhone by remember { mutableStateOf("") }
    var guardianEmail by remember { mutableStateOf("") }
    var consented by remember { mutableStateOf(false) }

    LaunchedEffect(doctorId) { vm.loadDoctor(clinicDoctorId) }
    LaunchedEffect(selectedChildId) {
        if (selectedChildId.isNullOrBlank()) {
            snackbarHostState.showSnackbar(pediaConnectMissingChildMessage(s.isThai))
        }
        vm.loadAppGuardians(selectedChildId)
        selectedChild?.let { c ->
            name = c.name
            birthdate = c.birthdate
            gender = c.gender
            bloodGroup = c.bloodGroup.orEmpty()
            weight = c.weightKg?.toString().orEmpty()
            height = c.heightCm?.toString().orEmpty()
            allergies = c.allergies.orEmpty()
            conditions = c.notes.orEmpty()
        }
    }
    val isPediaConnectOnly = clinicDoctorId == null

    LaunchedEffect(ui.redirectToReservationDoctorId) {
        ui.redirectToReservationDoctorId?.let { id ->
            navController.navigate(Screen.DoctorReservation.route(id)) {
                popUpTo(Screen.PatientRegistration.route) { inclusive = true }
            }
        }
    }
    LaunchedEffect(ui.savedWithoutDoctor) {
        if (ui.savedWithoutDoctor) {
            navController.navigate(Screen.PediaConnectDashboard.route) {
                popUpTo(Screen.PatientRegistration.route) { inclusive = true }
            }
        }
    }

    val guardianReady = when (guardianSource) {
        GuardianSource.FROM_APP -> selectedGuardianUserId != null
        GuardianSource.MANUAL -> guardianName.isNotBlank()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = when {
                    isPediaConnectOnly && s.isThai -> "ลงทะเบียน Pedia Connect"
                    isPediaConnectOnly -> "Pedia Connect registration"
                    s.isThai -> "ลงทะเบียนผู้ป่วย"
                    else -> "Patient Registration"
                },
                onBack = onBack,
                navController = navController
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            if (isPediaConnectOnly) {
                Text(
                    if (s.isThai)
                        "ลงทะเบียนกับ Pedia Connect แยกจากการเพิ่มลูกในแอป — ใช้ลูกที่เลือกจากหน้า Pedia Connect เพื่อดึงข้อมูลมาเติมแบบฟอร์ม (ไม่แก้โปรไฟล์ลูกในแอปโดยอัตโนมัติ)"
                    else
                        "Pedia Connect registration is separate from adding a child in the app. The child you picked on Pedia Connect syncs into this form — your app child profile is not changed automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Outline
                )
            }

            ui.doctorProfile?.clinicName?.takeIf { it.isNotBlank() }?.let {
                Text(
                    (if (s.isThai) "ลงทะเบียนกับ " else "Registering with ") + it,
                    style = MaterialTheme.typography.bodyMedium, color = Primary, fontWeight = FontWeight.SemiBold
                )
            }

            IntakeSection(if (s.isThai) "ลูกที่เลือกจาก Pedia Connect" else "Child selected on Pedia Connect") {
                when {
                    selectedChild != null -> ReadOnlySelectedChildChip(selectedChild, s.isThai)
                    selectedChildId.isNullOrBlank() -> Text(
                        pediaConnectMissingChildMessage(s.isThai),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> Text(
                        if (s.isThai) "ไม่พบข้อมูลลูกที่เลือก — กลับไปเลือกใหม่ที่หน้า Pedia Connect"
                        else "Selected child not found — go back to Pedia Connect and pick again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (!isPediaConnectOnly) {
                // Clinic booking: Stitch shows a read-only synced summary plus editable fields.
                selectedChild?.let { child ->
                    IntakeSection(
                        if (s.isThai) "ข้อมูลเด็ก (ซิงค์จากแอป)" else "Child info (synced from app)",
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Sync, null, tint = HealthGood, modifier = Modifier.size(14.dp))
                                Text(
                                    if (s.isThai) "อัปเดตล่าสุด: วันนี้" else "Updated today",
                                    style = MaterialTheme.typography.labelSmall, color = HealthGood
                                )
                            }
                        }
                    ) {
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Row(
                                Modifier.padding(14.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                DoctorAvatar(child.avatarUrl, size = 56.dp)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        child.name.ifBlank { if (s.isThai) "ไม่ระบุชื่อ" else "Unnamed" },
                                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        childAgeLabel(child, s.isThai) + " • " + genderLabel(child.gender, s.isThai),
                                        style = MaterialTheme.typography.bodySmall, color = Outline
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        child.allergies?.takeIf { it.isNotBlank() }?.let {
                                            StatusPill(it, MaterialTheme.colorScheme.error, Icons.Filled.Warning)
                                        }
                                        child.bloodGroup?.takeIf { it.isNotBlank() }?.let { StatusPill(it, Primary) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            IntakeSection(
                title = when {
                    isPediaConnectOnly && s.isThai -> "ข้อมูลลงทะเบียน Pedia Connect"
                    isPediaConnectOnly -> "Pedia Connect registration"
                    s.isThai -> "ข้อมูลเด็ก (แก้ไขเพิ่มเติม)"
                    else -> "Child info (edit)"
                },
                trailing = if (isPediaConnectOnly && selectedChild != null) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Sync, null, tint = HealthGood, modifier = Modifier.size(14.dp))
                            Text(
                                if (s.isThai) "ดึงจากแอป" else "Synced from app",
                                style = MaterialTheme.typography.labelSmall, color = HealthGood
                            )
                        }
                    }
                } else {
                    null
                }
            ) {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text(if (s.isThai) "ชื่อ-นามสกุล" else "Full name") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        birthdate, { birthdate = it },
                        label = { Text(if (s.isThai) "วันเกิด" else "Birthdate") },
                        placeholder = { Text("YYYY-MM-DD") },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        bloodGroup, { bloodGroup = it },
                        label = { Text(if (s.isThai) "กรุ๊ปเลือด" else "Blood group") },
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                    )
                }
                GenderChips(gender, s.isThai) { gender = it }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        weight, { weight = it },
                        label = { Text(if (s.isThai) "น้ำหนัก (กก.)" else "Weight (kg)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        height, { height = it },
                        label = { Text(if (s.isThai) "ส่วนสูง (ซม.)" else "Height (cm)") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                    )
                }
                OutlinedTextField(
                    allergies, { allergies = it },
                    label = { Text(if (s.isThai) "ประวัติการแพ้" else "Allergy history") },
                    leadingIcon = { Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    conditions, { conditions = it },
                    label = { Text(if (s.isThai) "โรคประจำตัว" else "Chronic conditions") },
                    placeholder = { Text(if (s.isThai) "ระบุโรคประจำตัว (ถ้ามี)" else "If any") },
                    leadingIcon = { Icon(Icons.Filled.MedicalInformation, null, tint = Outline) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
            }

            // ---- Guardian: from the app, or filled in by hand -------------------------------------
            IntakeSection(if (s.isThai) "ข้อมูลผู้ปกครอง" else "Guardian information") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = guardianSource == GuardianSource.FROM_APP,
                        onClick = { guardianSource = GuardianSource.FROM_APP },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Filled.Person, null, modifier = Modifier.size(16.dp)) }
                    ) { Text(if (s.isThai) "เลือกจากในแอป" else "From the app", style = MaterialTheme.typography.labelMedium) }
                    SegmentedButton(
                        selected = guardianSource == GuardianSource.MANUAL,
                        onClick = { guardianSource = GuardianSource.MANUAL },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Filled.Add, null, modifier = Modifier.size(16.dp)) }
                    ) { Text(if (s.isThai) "กรอกเอง" else "Fill in myself", style = MaterialTheme.typography.labelMedium) }
                }

                when (guardianSource) {
                    GuardianSource.FROM_APP -> {
                        if (ui.appGuardians.isEmpty()) {
                            Text(
                                if (s.isThai) "ยังไม่มีบัญชีผู้ปกครองอื่นที่เข้าถึงข้อมูลของลูกคนนี้ — เลือก \"กรอกเอง\" เพื่อเพิ่มผู้ปกครองที่ไม่มีบัญชีในแอป"
                                else "No other app accounts have access to this child yet — use \"Fill in myself\" to add a guardian who isn't an app user.",
                                style = MaterialTheme.typography.bodySmall, color = Outline
                            )
                        } else {
                            ui.appGuardians.forEach { member ->
                                GuardianAppRow(
                                    member = member,
                                    selected = selectedGuardianUserId == member.userId,
                                    isThai = s.isThai,
                                    onSelect = { selectedGuardianUserId = member.userId }
                                )
                            }
                        }
                    }
                    GuardianSource.MANUAL -> {
                        OutlinedTextField(
                            guardianName, { guardianName = it },
                            label = { Text(if (s.isThai) "ชื่อ-นามสกุล" else "Full name") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            guardianRelationship, { guardianRelationship = it },
                            label = { Text(if (s.isThai) "ความสัมพันธ์" else "Relationship") },
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            guardianPhone, { guardianPhone = it },
                            label = { Text(if (s.isThai) "เบอร์โทรศัพท์" else "Phone number") },
                            leadingIcon = { Icon(Icons.Filled.Call, null, tint = Outline) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                        )
                        OutlinedTextField(
                            guardianEmail, { guardianEmail = it },
                            label = { Text(if (s.isThai) "อีเมล (Email)" else "Email") },
                            leadingIcon = { Icon(Icons.Filled.Mail, null, tint = Outline) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            // ---- Consent -------------------------------------------------------------------------
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.05f))) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Checkbox(checked = consented, onCheckedChange = { consented = it })
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (s.isThai) "ยินยอมให้แบ่งปันข้อมูลและรับคำปรึกษา" else "Consent to share data and receive consultation",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (s.isThai) "ฉันยินยอมให้แบ่งปันข้อมูลกับบุคคลที่สามตามนโยบายความเป็นส่วนตัว เพื่อประโยชน์ในการรับคำปรึกษาทางการแพทย์และการส่งต่อข้อมูลที่จำเป็น"
                            else "I consent to sharing this data with third parties per the privacy policy, for the purpose of medical consultation and necessary referrals.",
                            style = MaterialTheme.typography.bodySmall, color = Outline
                        )
                    }
                }
            }

            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Button(
                onClick = {
                    val child = selectedChild ?: return@Button
                    val appGuardian = ui.appGuardians.firstOrNull { it.userId == selectedGuardianUserId }
                    val mainName = when (guardianSource) {
                        GuardianSource.FROM_APP -> appGuardian?.displayName.orEmpty()
                        GuardianSource.MANUAL -> guardianName
                    }
                    vm.submit(
                        doctorId = clinicDoctorId,
                        linkedChildId = child.id,
                        name = name.ifBlank { child.name },
                        birthdate = birthdate.ifBlank { child.birthdate },
                        gender = gender,
                        bloodGroup = bloodGroup.takeIf { it.isNotBlank() },
                        allergies = allergies.takeIf { it.isNotBlank() },
                        notes = conditions.takeIf { it.isNotBlank() },
                        guardianBlock = guardianBlock(
                            source = guardianSource,
                            appGuardian = appGuardian,
                            name = guardianName,
                            relationship = guardianRelationship,
                            phone = guardianPhone,
                            email = guardianEmail,
                            isThai = s.isThai
                        ),
                        mainGuardianUserId = if (guardianSource == GuardianSource.FROM_APP) selectedGuardianUserId else null,
                        mainGuardianName = mainName.ifBlank { guardianName },
                        mainGuardianRelationship = guardianRelationship,
                        mainGuardianPhone = guardianPhone,
                        mainGuardianEmail = guardianEmail
                    )
                },
                enabled = !ui.submitting && selectedChild != null && consented && guardianReady,
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)
            ) {
                if (ui.submitting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White)
                } else {
                    Icon(Icons.Filled.HowToReg, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (s.isThai) "ลงทะเบียน" else "Register", fontWeight = FontWeight.Bold)
                }
            }

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.VerifiedUser, null, tint = HealthGood, modifier = Modifier.size(16.dp))
                    Text("Secure Encryption", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = HealthGood)
                }
                Text(
                    if (s.isThai) "ข้อมูลของคุณถูกเก็บรักษาอย่างปลอดภัยและเข้ารหัสด้วยมาตรฐานสากล"
                    else "Your data is stored securely and encrypted to international standards.",
                    style = MaterialTheme.typography.labelSmall, color = Outline, textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun pediaConnectMissingChildMessage(isThai: Boolean): String =
    if (isThai) "กรุณากลับไปเลือกลูกที่หน้า Pedia Connect ก่อนลงทะเบียน"
    else "Go back to Pedia Connect and select a child before registering."

@Composable
private fun ReadOnlySelectedChildChip(child: Child, isThai: Boolean) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DoctorAvatar(child.avatarUrl, size = 28.dp)
            Text(
                child.name.ifBlank { if (isThai) "ไม่ระบุชื่อ" else "Unnamed" },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** The guardian block is stored in `appointments.notes` (there is no intake-fields table), so it has
 *  to be self-describing for the doctor reading the registration request. */
private fun guardianBlock(
    source: GuardianSource,
    appGuardian: ChildAccessMember?,
    name: String,
    relationship: String,
    phone: String,
    email: String,
    isThai: Boolean
): String = buildString {
    append(if (isThai) "ผู้ปกครอง: " else "Guardian: ")
    if (source == GuardianSource.FROM_APP && appGuardian != null) {
        append(appGuardian.displayName)
        append(" (").append(appGuardian.role).append(")")
        appGuardian.email?.takeIf { it.isNotBlank() }?.let { append(" • ").append(it) }
        append(if (isThai) " [บัญชีในแอป]" else " [app account]")
    } else {
        append(name)
        if (relationship.isNotBlank()) append(" (").append(relationship).append(")")
        if (phone.isNotBlank()) append(" • ").append(phone)
        if (email.isNotBlank()) append(" • ").append(email)
        append(if (isThai) " [กรอกเอง ไม่มีบัญชีในแอป]" else " [manually entered, not an app user]")
    }
}

private fun genderLabel(gender: String, isThai: Boolean): String = when (gender) {
    "male" -> if (isThai) "เพศชาย" else "Male"
    "female" -> if (isThai) "เพศหญิง" else "Female"
    else -> if (isThai) "ไม่ระบุ" else "Unspecified"
}

@Composable
private fun GenderChips(selected: String, isThai: Boolean, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("female", "male", "unspecified").forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(genderLabel(value, isThai), style = MaterialTheme.typography.labelMedium) },
                shape = RoundedCornerShape(100.dp)
            )
        }
    }
}

@Composable
private fun GuardianAppRow(member: ChildAccessMember, selected: Boolean, isThai: Boolean, onSelect: () -> Unit) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Primary.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, Primary) else null
    ) {
        Row(
            Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Box(
                Modifier.size(38.dp).background(Primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Person, null, tint = Primary, modifier = Modifier.size(19.dp)) }
            Column(Modifier.weight(1f)) {
                Text(
                    member.displayName.ifBlank { if (isThai) "ไม่ระบุชื่อ" else "Unnamed" },
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                )
                Text(
                    member.email ?: member.role,
                    style = MaterialTheme.typography.labelSmall, color = Outline
                )
            }
            if (member.role == "owner") {
                StatusPill(if (isThai) "ติดต่อหลัก" else "Primary", HealthGood, Icons.Filled.CheckCircle)
            }
        }
    }
}

@Composable
private fun IntakeSection(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        content()
    }
}
