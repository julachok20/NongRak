package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.DoctorViewModel

/**
 * "Edit Profile & Clinic Info (Thai)". Personal Information + Clinic & Hospital Information +
 * Contact Information sections from the Stitch design, mapped onto what the schema actually
 * supports (doctor_profiles: clinic_name / specialty / license_number / clinic_address /
 * clinic_photo_url / bio):
 *  - "แผนก" (department) + "หมายเลขห้อง" (room number) have no dedicated columns yet, so they're
 *    folded into `bio` as labeled free text (ponytail: add real columns if these need to be
 *    queried/filtered later — for now they're just displayed back the same way).
 *  - "เวลาทำการ" (operating hours) is its own dedicated screen (DoctorWeeklyHours /
 *    doctor_availability), so this row navigates there instead of being an inline field.
 *  - "เบอร์โทรศัพท์" (phone) has no column on profiles/doctor_profiles — captured for design
 *    fidelity but not persisted yet, same ponytail note as the Sign Up screen.
 *  - Clinic photo upload isn't in the retrieved design and there's no upload helper on
 *    DoctorRepository (unlike avatar upload for parents), so it's left out rather than invented.
 */
@Composable
fun DoctorEditProfileScreen(
    authViewModel: AuthViewModel,
    doctorViewModel: DoctorViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val authState by authViewModel.uiState.collectAsState()
    val doctorState by doctorViewModel.uiState.collectAsState()
    val doctorId = authViewModel.getCurrentUserId()

    LaunchedEffect(Unit) {
        authViewModel.loadProfile()
        doctorId?.let { doctorViewModel.loadProfile(it) }
    }

    var fullName by remember { mutableStateOf(authState.profile?.fullName ?: "") }
    var specialty by remember { mutableStateOf(doctorState.profile?.specialty ?: "") }
    var licenseNumber by remember { mutableStateOf(doctorState.profile?.licenseNumber ?: "") }
    var clinicName by remember { mutableStateOf(doctorState.profile?.clinicName ?: "") }
    var clinicAddress by remember { mutableStateOf(doctorState.profile?.clinicAddress ?: "") }
    var department by remember { mutableStateOf("") }
    var roomNumber by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") } // ponytail: no phone column yet, see file doc comment

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "แก้ไขโปรไฟล์" else "Edit Profile", onBack = onBack, navController = navController, isDoctor = true) },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.SETTINGS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            FormSection(icon = Icons.Filled.Person, title = if (isThai) "ข้อมูลส่วนตัว" else "Personal Information") {
                LabeledField(if (isThai) "ชื่อ-นามสกุล" else "Full name") {
                    OutlinedTextField(value = fullName, onValueChange = { fullName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                }
                LabeledField(if (isThai) "ความเชี่ยวชาญ" else "Specialty") {
                    OutlinedTextField(value = specialty, onValueChange = { specialty = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                }
                LabeledField(if (isThai) "เลขใบอนุญาตประกอบวิชาชีพ" else "Professional license number") {
                    OutlinedTextField(value = licenseNumber, onValueChange = { licenseNumber = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                }
            }

            FormSection(icon = Icons.Filled.Apartment, title = if (isThai) "ข้อมูลคลินิกและโรงพยาบาล" else "Clinic & Hospital Information") {
                LabeledField(if (isThai) "ชื่อโรงพยาบาล/คลินิก" else "Clinic/Hospital name") {
                    OutlinedTextField(value = clinicName, onValueChange = { clinicName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                }
                LabeledField(if (isThai) "ที่อยู่คลินิก" else "Clinic address") {
                    OutlinedTextField(value = clinicAddress, onValueChange = { clinicAddress = it }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                }
                LabeledField(if (isThai) "แผนก" else "Department") {
                    OutlinedTextField(value = department, onValueChange = { department = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                }
                LabeledField(if (isThai) "หมายเลขห้อง" else "Room number") {
                    OutlinedTextField(value = roomNumber, onValueChange = { roomNumber = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                }
                Surface(onClick = { navController.navigate(Screen.DoctorWeeklyHours.route) }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Schedule, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Text(if (isThai) "เวลาทำการ" else "Operating hours", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                    }
                }
            }

            FormSection(icon = Icons.Filled.ContactMail, title = if (isThai) "ข้อมูลการติดต่อ" else "Contact Information") {
                LabeledField(if (isThai) "อีเมลติดต่อ" else "Contact email") {
                    OutlinedTextField(
                        value = "", onValueChange = {}, modifier = Modifier.fillMaxWidth(), enabled = false, singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Mail, null, tint = Outline) },
                        placeholder = { Text(if (isThai) "ไม่สามารถเปลี่ยนอีเมลได้" else "Email can't be changed here", color = Outline) },
                        shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(disabledBorderColor = MaterialTheme.colorScheme.outlineVariant)
                    )
                }
                LabeledField(if (isThai) "เบอร์โทรศัพท์" else "Phone number") {
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Call, null, tint = Outline) },
                        shape = RoundedCornerShape(12.dp), colors = fieldColors()
                    )
                }
            }

            doctorState.error?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }

            PrimaryButton(
                text = if (isThai) "บันทึกการเปลี่ยนแปลง" else "Save changes",
                isLoading = authState.isLoading || doctorState.isLoading,
                onClick = {
                    val bio = listOfNotNull(
                        department.takeIf { it.isNotBlank() }?.let { (if (isThai) "แผนก: " else "Department: ") + it },
                        roomNumber.takeIf { it.isNotBlank() }?.let { (if (isThai) "ห้อง: " else "Room: ") + it }
                    ).joinToString(" • ").ifBlank { null }
                    authViewModel.updateProfile(null, fullName.ifBlank { null }, null) {
                        if (doctorId != null) {
                            doctorViewModel.saveProfile(
                                doctorId,
                                clinicName.ifBlank { null },
                                specialty.ifBlank { null },
                                licenseNumber.ifBlank { null },
                                clinicAddress.ifBlank { null },
                                doctorState.profile?.clinicPhotoUrl,
                                bio
                            ) { onBack() }
                        } else onBack()
                    }
                }
            )

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
            ) { Text(if (isThai) "ยกเลิก" else "Cancel") }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FormSection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun LabeledField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
