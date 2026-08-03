@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.ScheduleDatePickerField
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DoctorPatientRegistrationFormViewModel
import com.lullabyai.app.viewmodel.WalkInVaccineEntry

private val GENDER_KEYS = listOf("female", "male", "unspecified")
private val BLOOD_GROUPS = listOf("A Rh+", "A Rh-", "B Rh+", "B Rh-", "AB Rh+", "AB Rh-", "O Rh+", "O Rh-")

private fun genderLabel(key: String, isThai: Boolean): String = when (key) {
    "female" -> if (isThai) "เพศหญิง" else "Female"
    "male" -> if (isThai) "เพศชาย" else "Male"
    else -> if (isThai) "ไม่ระบุ" else "Unspecified"
}

private fun relationshipOptions(isThai: Boolean) =
    if (isThai) listOf("มารดา", "บิดา", "ญาติ") else listOf("Mother", "Father", "Relative")

/**
 * Stitch "Patient Registration with Appointment Details", filled in by the DOCTOR for a walk-in
 * patient who has no parent app account. Reached from จัดการการลงทะเบียน → เพิ่มผู้ป่วยเอง.
 * Every write is done by [DoctorPatientRegistrationFormViewModel.register].
 */
@Composable
fun DoctorPatientRegistrationFormScreen(navController: NavController) {
    val s = LocalStrings.current
    val vm: DoctorPatientRegistrationFormViewModel = viewModel()
    val state by vm.uiState.collectAsState()

    var fullName by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var birthdate by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(GENDER_KEYS.first()) }
    var bloodGroup by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var chronic by remember { mutableStateOf("") }
    val vaccines = remember { mutableStateListOf<WalkInVaccineEntry>() }
    var vaccineName by remember { mutableStateOf("") }
    var vaccineDate by remember { mutableStateOf("") }
    var visitReason by remember { mutableStateOf("") }
    var appointmentDate by remember { mutableStateOf("") }
    var guardianName by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf(relationshipOptions(s.isThai).first()) }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    LaunchedEffect(state.saved) { if (state.saved) navController.popBackStack() }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ลงทะเบียนผู้ป่วย" else "Patient Registration",
                onBack = { navController.popBackStack() },
                navController = navController,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp)
        ) {
            // Hero — mirrors Stitch, but only shows what has actually been typed.
            item {
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                fullName.ifBlank { if (s.isThai) "ผู้ป่วยใหม่" else "New patient" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(genderLabel(gender, s.isThai), style = MaterialTheme.typography.bodySmall, color = Outline)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                allergies.trim().takeIf { it.isNotBlank() }?.let {
                                    Surface(shape = RoundedCornerShape(8.dp), color = ErrorContainer) {
                                        Text(it, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = OnErrorContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                bloodGroup.takeIf { it.isNotBlank() }?.let {
                                    Surface(shape = RoundedCornerShape(8.dp), color = Primary.copy(0.1f)) {
                                        Text(it, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Primary, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ข้อมูลเด็ก
            item {
                FormCard(if (s.isThai) "ข้อมูลเด็ก (แก้ไขเพิ่มเติม)" else "Child details", Icons.Filled.ChildCare) {
                    OutlinedTextField(
                        value = fullName, onValueChange = { fullName = it },
                        label = { Text(if (s.isThai) "ชื่อ-นามสกุล" else "Full name") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = nickname, onValueChange = { nickname = it },
                        label = { Text(if (s.isThai) "ชื่อเล่น" else "Nickname") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    ScheduleDatePickerField(
                        value = birthdate,
                        onValueChange = { birthdate = it },
                        label = if (s.isThai) "วันเกิด" else "Birthdate",
                        placeholder = if (s.isThai) "เลือกวันเกิด" else "Pick birthdate",
                        isThai = s.isThai
                    )
                    FormDropdown(
                        label = if (s.isThai) "เพศ" else "Gender",
                        display = genderLabel(gender, s.isThai),
                        options = GENDER_KEYS,
                        optionLabel = { genderLabel(it, s.isThai) },
                        onSelect = { gender = it }
                    )
                    FormDropdown(
                        label = if (s.isThai) "กรุ๊ปเลือด" else "Blood group",
                        display = bloodGroup.ifBlank { if (s.isThai) "ไม่ระบุ" else "Unspecified" },
                        options = BLOOD_GROUPS,
                        optionLabel = { it },
                        onSelect = { bloodGroup = it }
                    )
                    // Side-by-side on a phone clipped the unit suffixes, so these wrap instead.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = weight, onValueChange = { weight = it },
                            label = { Text(if (s.isThai) "น้ำหนัก (กก.)" else "Weight (kg)") },
                            modifier = Modifier.widthIn(min = 150.dp), singleLine = true,
                            suffix = { Text("kg") }
                        )
                        OutlinedTextField(
                            value = height, onValueChange = { height = it },
                            label = { Text(if (s.isThai) "ส่วนสูง (ซม.)" else "Height (cm)") },
                            modifier = Modifier.widthIn(min = 150.dp), singleLine = true,
                            suffix = { Text("cm") }
                        )
                    }
                }
            }

            // ประวัติทางการแพทย์
            item {
                FormCard(if (s.isThai) "ประวัติทางการแพทย์" else "Medical history", Icons.Filled.MedicalInformation, tint = Error) {
                    OutlinedTextField(
                        value = allergies, onValueChange = { allergies = it },
                        label = { Text(if (s.isThai) "ประวัติการแพ้" else "Allergies") },
                        leadingIcon = { Icon(Icons.Filled.Warning, null, tint = Error) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = chronic, onValueChange = { chronic = it },
                        label = { Text(if (s.isThai) "โรคประจำตัว" else "Chronic conditions") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ประวัติวัคซีน
            item {
                FormCard(if (s.isThai) "ประวัติวัคซีน" else "Vaccine history", Icons.Filled.Vaccines, tint = Tertiary) {
                    vaccines.forEachIndexed { index, entry ->
                        Surface(shape = RoundedCornerShape(12.dp), color = SurfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    entry.dateGiven.takeIf { it.isNotBlank() }?.let {
                                        Text(
                                            (if (s.isThai) "ได้รับเมื่อ: " else "Given: ") + it,
                                            style = MaterialTheme.typography.labelSmall, color = Outline
                                        )
                                    }
                                }
                                IconButton(onClick = { vaccines.removeAt(index) }) {
                                    Icon(Icons.Filled.Delete, null, tint = Error, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = vaccineName, onValueChange = { vaccineName = it },
                        label = { Text(if (s.isThai) "ชื่อวัคซีน" else "Vaccine name") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    ScheduleDatePickerField(
                        value = vaccineDate,
                        onValueChange = { vaccineDate = it },
                        label = if (s.isThai) "ได้รับเมื่อ" else "Date given",
                        placeholder = if (s.isThai) "เลือกวันที่" else "Pick date",
                        isThai = s.isThai
                    )
                    OutlinedButton(
                        onClick = {
                            vaccines.add(WalkInVaccineEntry(vaccineName.trim(), vaccineDate.trim()))
                            vaccineName = ""
                            vaccineDate = ""
                        },
                        enabled = vaccineName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.AddCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (s.isThai) "เพิ่มวัคซีน" else "Add vaccine", maxLines = 1)
                    }
                }
            }

            // รายละเอียดการเข้ารับบริการ
            item {
                FormCard(if (s.isThai) "รายละเอียดการเข้ารับบริการ" else "Visit details", Icons.Filled.MedicalServices) {
                    OutlinedTextField(
                        value = visitReason, onValueChange = { visitReason = it },
                        label = { Text(if (s.isThai) "เหตุผลในการมาพบแพทย์" else "Reason for visit") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2
                    )
                    ScheduleDatePickerField(
                        value = appointmentDate,
                        onValueChange = { appointmentDate = it },
                        label = if (s.isThai) "วันที่ต้องการนัดหมาย" else "Appointment date",
                        placeholder = if (s.isThai) "เลือกวันที่" else "Pick date",
                        isThai = s.isThai
                    )
                }
            }

            // ข้อมูลผู้ปกครอง
            item {
                FormCard(if (s.isThai) "ข้อมูลผู้ปกครอง" else "Guardian", Icons.Filled.Person) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.CheckCircle, null, tint = HealthGood, modifier = Modifier.size(16.dp))
                        Text(
                            if (s.isThai) "ผู้ปกครองหลัก · ติดต่อหลัก" else "Main guardian · primary contact",
                            style = MaterialTheme.typography.labelMedium, color = Outline,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedTextField(
                        value = guardianName, onValueChange = { guardianName = it },
                        label = { Text(if (s.isThai) "ชื่อ-นามสกุล" else "Full name") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    FormDropdown(
                        label = if (s.isThai) "ความสัมพันธ์" else "Relationship",
                        display = relationship,
                        options = relationshipOptions(s.isThai),
                        optionLabel = { it },
                        onSelect = { relationship = it }
                    )
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it },
                        label = { Text(if (s.isThai) "เบอร์โทรศัพท์" else "Phone") },
                        leadingIcon = { Icon(Icons.Filled.Call, null, tint = Primary) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedTextField(
                        value = email, onValueChange = { email = it },
                        label = { Text(if (s.isThai) "อีเมล (Email)" else "Email") },
                        leadingIcon = { Icon(Icons.Filled.Mail, null, tint = Primary) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            }

            state.error?.let { err ->
                item { Text(err, color = Error, style = MaterialTheme.typography.bodySmall) }
            }

            item {
                Button(
                    onClick = {
                        vm.register(
                            fullName = fullName, nickname = nickname, birthdate = birthdate,
                            gender = gender, bloodGroup = bloodGroup, weightKg = weight, heightCm = height,
                            allergies = allergies, chronicConditions = chronic, vaccines = vaccines.toList(),
                            visitReason = visitReason, appointmentDate = appointmentDate,
                            guardianName = guardianName, guardianRelationship = relationship,
                            guardianPhone = phone, guardianEmail = email, isThai = s.isThai
                        )
                    },
                    enabled = !state.isSaving && fullName.isNotBlank() && guardianName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(100.dp)
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.White)
                    } else {
                        Icon(Icons.Filled.HowToReg, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (s.isThai) "ลงทะเบียน" else "Register", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.VerifiedUser, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Text(
                        if (s.isThai) "ข้อมูลจะถูกเก็บรักษาอย่างปลอดภัยและเข้ารหัสระหว่างส่ง เข้าถึงได้เฉพาะคลินิกและผู้ปกครองที่ได้รับอนุญาต"
                        else "Data is stored securely, encrypted in transit, and visible only to this clinic and authorised guardians",
                        style = MaterialTheme.typography.labelSmall, color = Outline,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Card wrapper shared by every section of the form. */
@Composable
private fun FormCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color = Primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
                Text(
                    title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            content()
        }
    }
}

@Composable
private fun <T> FormDropdown(
    label: String,
    display: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}
