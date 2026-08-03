@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.DoctorViewModel

private val SPECIALTIES = listOf(
    "อายุรศาสตร์" to "Internal Medicine",
    "กุมารเวชศาสตร์" to "Pediatrics",
    "ศัลยศาสตร์" to "Surgery",
    "สูตินรีเวชวิทยา" to "Obstetrics & Gynecology",
    "รังสีวิทยา" to "Radiology",
    "อื่นๆ" to "Others"
)

/**
 * Post-Google doctor registration (Stitch 44.Regis-Google). Shown when Google auth succeeded but
 * [doctor_profiles] is missing license/clinic/specialty.
 */
@Composable
fun DoctorGoogleCompleteProfileScreen(
    authViewModel: AuthViewModel,
    doctorViewModel: DoctorViewModel,
    navController: NavController
) {
    val authState by authViewModel.uiState.collectAsState()
    val doctorState by doctorViewModel.uiState.collectAsState()
    val isThai = com.lullabyai.app.ui.localization.LocalStrings.current.isThai

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var licenseNumber by remember { mutableStateOf("") }
    var clinicName by remember { mutableStateOf("") }
    var clinicAddress by remember { mutableStateOf("") }
    var specialtyExpanded by remember { mutableStateOf(false) }
    var specialty by remember { mutableStateOf(SPECIALTIES.first().first) }
    var agreedToTerms by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        authViewModel.loadProfile()
        authViewModel.getCurrentUserId()?.let { doctorViewModel.loadProfile(it) }
    }

    LaunchedEffect(authState.profile) {
        authState.profile?.fullName?.orEmpty()?.trim()?.split(" ", limit = 2)?.let { parts ->
            if (firstName.isBlank() && parts.isNotEmpty()) firstName = parts[0]
            if (lastName.isBlank() && parts.size > 1) lastName = parts[1]
        }
        authState.profile?.phone?.let { if (phone.isBlank()) phone = it }
    }

    LaunchedEffect(doctorState.profile) {
        doctorState.profile?.let { p ->
            if (licenseNumber.isBlank()) licenseNumber = p.licenseNumber.orEmpty()
            if (clinicName.isBlank()) clinicName = p.clinicName.orEmpty()
            if (clinicAddress.isBlank()) clinicAddress = p.clinicAddress.orEmpty()
            if (specialty == SPECIALTIES.first().first && !p.specialty.isNullOrBlank()) specialty = p.specialty!!
        }
    }

    fun goDashboard() {
        navController.navigate(Screen.DoctorFirstRunTour.route) {
            popUpTo(Screen.DoctorWelcome.route) { inclusive = true }
        }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "สมัครสมาชิกแพทย์" else "Doctor registration",
                navController = navController,
                showNotification = false,
                showProfile = false
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text("NongRak AI Professional", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Primary)
            Text(
                if (isThai) "กรอกข้อมูลวิชาชีพเพื่อเริ่มใช้งานในโหมดแพทย์"
                else "Complete your professional profile to use doctor mode",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val authEmail = supabase.auth.currentUserOrNull()?.email
            if (authEmail != null) {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccountCircle, null, tint = Primary)
                        Column {
                            Text(authEmail, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isThai) "เข้าสู่ระบบด้วย Google แล้ว" else "Signed in with Google",
                                style = MaterialTheme.typography.labelSmall,
                                color = Outline
                            )
                        }
                    }
                }
            }

            GoogleRegFormSection(Icons.Filled.Person, if (isThai) "ข้อมูลส่วนตัว" else "Personal information") {
                GoogleRegField(if (isThai) "ชื่อ" else "First name") {
                    OutlinedTextField(firstName, { firstName = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = googleRegFieldColors())
                }
                GoogleRegField(if (isThai) "นามสกุล" else "Last name") {
                    OutlinedTextField(lastName, { lastName = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = googleRegFieldColors())
                }
                GoogleRegField(if (isThai) "เบอร์โทรศัพท์" else "Phone") {
                    OutlinedTextField(
                        phone, { phone = it }, Modifier.fillMaxWidth(), singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Phone, null, tint = Outline) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        shape = RoundedCornerShape(12.dp), colors = googleRegFieldColors()
                    )
                }
            }

            GoogleRegFormSection(Icons.Filled.Badge, if (isThai) "ข้อมูลวิชาชีพ" else "Professional credentials") {
                GoogleRegField(if (isThai) "เลขใบประกอบวิชาชีพแพทย์" else "Medical license number") {
                    OutlinedTextField(licenseNumber, { licenseNumber = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = googleRegFieldColors())
                }
            }

            GoogleRegFormSection(Icons.Filled.Domain, if (isThai) "รายละเอียดสถานพยาบาล" else "Practice details") {
                GoogleRegField(if (isThai) "ชื่อคลินิก/โรงพยาบาล" else "Clinic/Hospital name") {
                    OutlinedTextField(clinicName, { clinicName = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = googleRegFieldColors())
                }
                GoogleRegField(if (isThai) "ความเชี่ยวชาญ" else "Specialty") {
                    ExposedDropdownMenuBox(specialtyExpanded, { specialtyExpanded = it }) {
                        OutlinedTextField(
                            if (isThai) specialty else (SPECIALTIES.firstOrNull { it.first == specialty }?.second ?: specialty),
                            {}, Modifier.fillMaxWidth().menuAnchor(), readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(specialtyExpanded) },
                            shape = RoundedCornerShape(12.dp), colors = googleRegFieldColors()
                        )
                        ExposedDropdownMenu(specialtyExpanded, { specialtyExpanded = false }) {
                            SPECIALTIES.forEach { (th, en) ->
                                DropdownMenuItem({ Text(if (isThai) th else en) }, { specialty = th; specialtyExpanded = false })
                            }
                        }
                    }
                }
                GoogleRegField(if (isThai) "ที่อยู่สถานพยาบาล" else "Practice address") {
                    DoctorClinicAddressInput(isThai, clinicName, clinicAddress, { clinicAddress = it })
                }
            }

            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Info, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text(
                        if (isThai) "ข้อมูลบัญชีถูกจัดเก็บอย่างปลอดภัยในระบบ NongRak (ไม่เก็บเฉพาะบนอุปกรณ์)"
                        else "Account data is stored securely in NongRak (not device-only).",
                        style = MaterialTheme.typography.bodySmall,
                        color = Outline
                    )
                }
            }

            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(agreedToTerms, { agreedToTerms = it }, colors = CheckboxDefaults.colors(checkedColor = Primary))
                Text(
                    if (isThai) "ฉันยอมรับข้อตกลงและเงื่อนไขการใช้งานสำหรับบุคลากรทางการแพทย์"
                    else "I accept the terms of use for healthcare professionals",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            (formError ?: doctorState.error ?: authState.error)?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }

            PrimaryButton(
                text = if (isThai) "บันทึกและเริ่มใช้งาน" else "Save and continue",
                isLoading = authState.isLoading || doctorState.isLoading,
                enabled = !authState.isLoading && !doctorState.isLoading,
                onClick = {
                    formError = null
                    authViewModel.clearError()
                    val fullName = "$firstName $lastName".trim()
                    when {
                        firstName.isBlank() || lastName.isBlank() ->
                            formError = if (isThai) "กรุณากรอกชื่อและนามสกุล" else "Please enter first and last name"
                        licenseNumber.isBlank() || clinicName.isBlank() ->
                            formError = if (isThai) "กรุณากรอกเลขใบอนุญาตและชื่อสถานพยาบาล" else "License number and clinic name are required"
                        !agreedToTerms ->
                            formError = if (isThai) "กรุณายอมรับข้อตกลงก่อนดำเนินการ" else "Please accept the terms"
                        else -> {
                            val doctorId = authViewModel.getCurrentUserId()
                            if (doctorId == null) {
                                formError = if (isThai) "ไม่พบเซสชันแพทย์ กรุณาเข้าสู่ระบบใหม่" else "Doctor session missing — please sign in again"
                                return@PrimaryButton
                            }
                            doctorViewModel.saveProfile(
                                doctorId,
                                clinicName.trim(),
                                specialty,
                                licenseNumber.trim(),
                                clinicAddress.trim().ifBlank { null },
                                null,
                                null
                            ) {
                                authViewModel.updateProfile(null, fullName, null) {
                                    authViewModel.loadProfile()
                                }
                                goDashboard()
                            }
                        }
                    }
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GoogleRegFormSection(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
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
private fun GoogleRegField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun googleRegFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)
