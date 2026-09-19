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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.remote.supabase
import io.github.jan.supabase.auth.auth
import com.lullabyai.app.ui.components.HCaptchaVerifyButton
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.components.rememberGoogleSignInAction
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.auth.CAPTCHA_BYPASS_EMAILS
import com.lullabyai.app.ui.screens.auth.CAPTCHA_FAST_SUBMIT_THRESHOLD_MS
import com.lullabyai.app.ui.screens.auth.CAPTCHA_RETRY_BURST_THRESHOLD
import com.lullabyai.app.ui.screens.auth.ENABLE_GOOGLE_SIGN_IN
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
 * "Doctor Sign Up Form (สมัครสมาชิกแพทย์)". Follows SignUpScreen.kt's captcha/validation pattern —
 * account fields (name/email/password) go through AuthViewModel.signUp(accountType = "doctor");
 * the practice-details fields shown in the Stitch design (clinic name, specialty, license,
 * address) are saved right after via DoctorViewModel so nothing the doctor typed is thrown away,
 * then we hand off to DoctorEditProfile to finish (photo, bio, weekly hours).
 *
 * Cloud Sync / Google Drive is **optional** — not required to sign up (Supabase is the system of record).
 */
@Composable
fun DoctorSignUpScreen(
    authViewModel: AuthViewModel,
    doctorViewModel: DoctorViewModel,
    navController: NavController,
    settingsViewModel: com.lullabyai.app.viewmodel.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by authViewModel.uiState.collectAsState()
    val doctorState by doctorViewModel.uiState.collectAsState()
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai

    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var licenseNumber by remember { mutableStateOf("") }
    var clinicName by remember { mutableStateOf("") }
    var clinicAddress by remember { mutableStateOf("") }
    var specialtyExpanded by remember { mutableStateOf(false) }
    var specialty by remember { mutableStateOf(SPECIALTIES.first().first) }
    var agreedToTerms by remember { mutableStateOf(false) }
    var passwordMismatch by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var captchaToken by remember { mutableStateOf<String?>(null) }
    var captchaRequired by remember { mutableStateOf(false) }
    var attemptCount by remember { mutableStateOf(0) }
    var cloudSyncEnabled by remember { mutableStateOf(true) }
    val screenOpenedAtMs = remember { System.currentTimeMillis() }
    val bypassed = email.trim().lowercase() in CAPTCHA_BYPASS_EMAILS
    val alreadySignedIn = remember { supabase.auth.currentUserOrNull() != null }
    fun goDoctorHome() {
        navController.navigate(Screen.DoctorFirstRunTour.route) {
            popUpTo(Screen.DoctorWelcome.route) { inclusive = true }
        }
    }
    /** Runs once a session exists: straight after sign-up, or after the email link is confirmed. */
    fun finishDoctorSignUp() {
        val doctorId = authViewModel.getCurrentUserId()
        if (doctorId != null) {
            doctorViewModel.saveProfile(doctorId, clinicName.ifBlank { null }, specialty, licenseNumber.ifBlank { null }, clinicAddress.ifBlank { null }, null, null) {
                goDoctorHome()
            }
        } else {
            goDoctorHome()
        }
    }
    var awaitingConfirmation by remember { mutableStateOf(false) }
    if (awaitingConfirmation) {
        com.lullabyai.app.ui.screens.auth.EmailConfirmationDialog(
            email = email.trim(),
            password = password,
            isThai = isThai,
            onConfirmed = { awaitingConfirmation = false; finishDoctorSignUp() },
            onCancel = { awaitingConfirmation = false }
        )
    }

    val googleSignUp = rememberGoogleSignInAction(
        authViewModel = authViewModel,
        isThai = isThai,
        accountType = "doctor",
        onSuccess = {
            authViewModel.attachDoctorRoleAfterGoogle(
                onSuccess = {
                    val doctorId = authViewModel.getCurrentUserId()
                    if (doctorId != null && (clinicName.isNotBlank() || specialty.isNotBlank() || licenseNumber.isNotBlank() || clinicAddress.isNotBlank())) {
                        doctorViewModel.saveProfile(
                            doctorId,
                            clinicName.ifBlank { null },
                            specialty,
                            licenseNumber.ifBlank { null },
                            clinicAddress.ifBlank { null },
                            null,
                            null
                        ) {
                            authViewModel.resolveDoctorPortalAfterAuth(
                                isThai = isThai,
                                onNeedsRegistration = {
                                    navController.navigate(Screen.DoctorGoogleCompleteProfile.route) {
                                        popUpTo(Screen.DoctorWelcome.route) { inclusive = true }
                                    }
                                },
                                onDashboard = { goDoctorHome() },
                                onRejected = { formError = it }
                            )
                        }
                    } else {
                        authViewModel.resolveDoctorPortalAfterAuth(
                            isThai = isThai,
                            onNeedsRegistration = {
                                navController.navigate(Screen.DoctorGoogleCompleteProfile.route) {
                                    popUpTo(Screen.DoctorWelcome.route) { inclusive = true }
                                }
                            },
                            onDashboard = { goDoctorHome() },
                            onRejected = { formError = it }
                        )
                    }
                },
                onFailure = { formError = it }
            )
        }
    )

    LaunchedEffect(Unit) {
        doctorViewModel.checkDriveConnection()
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "สมัครสมาชิกแพทย์" else "Doctor Sign Up",
                onBack = { navController.popBackStack() },
                navController = navController,
                // Same reason as DoctorLoginScreen: no bell / avatar pre-auth, and both actions
                // navigate to PARENT destinations, which would break out of doctor mode.
                showNotification = false,
                showProfile = false,
                actions = {
                    com.lullabyai.app.ui.components.LanguageToggleButton(
                        isThai = isThai,
                        onToggle = { settingsViewModel.toggleLanguage() },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text("NongRak AI Professional", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Primary)
            Text(
                if (isThai) "ยินดีต้อนรับสู่เครือข่ายบุคลากรทางการแพทย์ของ NongRak AI" else "Welcome to the NongRak AI medical professional network",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (alreadySignedIn) {
                Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Tertiary.copy(alpha = 0.12f))) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.Warning, null, tint = Tertiary)
                        Text(
                            if (isThai)
                                "มีบัญชีเข้าสู่ระบบอยู่แล้ว — ออกจากระบบหรือลบบัญชีเดิมก่อนสมัครบัญชีแพทย์ใหม่"
                            else
                                "You're already signed in — sign out or delete your existing account before creating a new doctor account.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f))) {
                Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = Primary, modifier = Modifier.size(20.dp))
                    Text(
                        if (isThai) "ข้อมูลบัญชีของคุณจะถูกจัดเก็บอย่างปลอดภัยและเข้ารหัสในระบบของเรา"
                        else "Your account information is stored securely and encrypted on our servers",
                        style = MaterialTheme.typography.bodySmall, color = Primary
                    )
                }
            }

            FormSection(icon = Icons.Filled.Person, title = if (isThai) "ข้อมูลส่วนตัว" else "Personal Information") {
                LabeledField(if (isThai) "ชื่อ" else "First name") {
                    OutlinedTextField(value = firstName, onValueChange = { firstName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                }
                LabeledField(if (isThai) "นามสกุล" else "Last name") {
                    OutlinedTextField(value = lastName, onValueChange = { lastName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                }
                LabeledField(if (isThai) "อีเมล" else "Email") {
                    OutlinedTextField(
                        value = email, onValueChange = { email = it; authViewModel.clearError() },
                        modifier = Modifier.fillMaxWidth(), placeholder = { Text("example@email.com", color = Outline) },
                        leadingIcon = { Icon(Icons.Filled.Email, null, tint = Outline) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors()
                    )
                }
                LabeledField(if (isThai) "เบอร์โทรศัพท์" else "Phone number") {
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it }, modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Filled.Phone, null, tint = Outline) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors()
                    )
                }
                LabeledField(if (isThai) "รหัสผ่าน" else "Password") {
                    OutlinedTextField(
                        value = password, onValueChange = { password = it; passwordMismatch = false },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Filled.Lock, null, tint = Outline) },
                        trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = Outline) } },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors()
                    )
                }
                LabeledField(if (isThai) "ยืนยันรหัสผ่าน" else "Confirm password") {
                    OutlinedTextField(
                        value = confirmPassword, onValueChange = { confirmPassword = it; passwordMismatch = false },
                        modifier = Modifier.fillMaxWidth(), isError = passwordMismatch,
                        leadingIcon = { Icon(Icons.Filled.LockReset, null, tint = Outline) },
                        trailingIcon = { IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) { Icon(if (showConfirmPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = Outline) } },
                        visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors()
                    )
                    if (passwordMismatch) Text(s.passwordMismatch, color = Error, style = MaterialTheme.typography.bodySmall)
                }
            }

            FormSection(icon = Icons.Filled.Badge, title = if (isThai) "ข้อมูลวิชาชีพ" else "Professional Credentials") {
                LabeledField(if (isThai) "เลขใบประกอบวิชาชีพแพทย์" else "Medical license number") {
                    OutlinedTextField(value = licenseNumber, onValueChange = { licenseNumber = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                }
                Text(
                    if (isThai) "กรุณาตรวจสอบข้อมูลให้ถูกต้อง เพื่อการยืนยันตัวตนที่รวดเร็ว" else "Please double-check this for fast verification",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FormSection(icon = Icons.Filled.Domain, title = if (isThai) "รายละเอียดสถานพยาบาล" else "Practice Details") {
                LabeledField(if (isThai) "ชื่อคลินิก/โรงพยาบาล" else "Clinic/Hospital name") {
                    OutlinedTextField(value = clinicName, onValueChange = { clinicName = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp), colors = fieldColors())
                }
                LabeledField(if (isThai) "ความเชี่ยวชาญ" else "Specialty") {
                    ExposedDropdownMenuBox(expanded = specialtyExpanded, onExpandedChange = { specialtyExpanded = it }) {
                        OutlinedTextField(
                            value = if (isThai) specialty else (SPECIALTIES.firstOrNull { it.first == specialty }?.second ?: specialty),
                            onValueChange = {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = specialtyExpanded) },
                            shape = RoundedCornerShape(12.dp), colors = fieldColors()
                        )
                        ExposedDropdownMenu(expanded = specialtyExpanded, onDismissRequest = { specialtyExpanded = false }) {
                            SPECIALTIES.forEach { (th, en) ->
                                DropdownMenuItem(text = { Text(if (isThai) th else en) }, onClick = { specialty = th; specialtyExpanded = false })
                            }
                        }
                    }
                }
                LabeledField(if (isThai) "ที่อยู่สถานพยาบาล" else "Practice address") {
                    DoctorClinicAddressInput(
                        isThai = isThai,
                        clinicName = clinicName,
                        address = clinicAddress,
                        onAddressChange = { clinicAddress = it }
                    )
                }
            }

            FormSection(icon = Icons.Filled.Cloud, title = if (isThai) "ซิงค์คลาวด์ / Google Drive" else "Cloud Sync / Google Drive") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            if (isThai) "เก็บรายละเอียดคนไข้ใน Google Drive" else "Store patient details in Google Drive",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            when {
                                doctorState.driveChecking -> if (isThai) "กำลังตรวจสอบการเชื่อมต่อ..." else "Checking connection..."
                                doctorState.driveConnected == true -> if (isThai) "พร้อมใช้งาน (Drive app-data)" else "Ready (Drive app-data)"
                                else -> if (isThai) "ทางเลือก — สำรองรายละเอียดเพิ่มเติมใน Google Drive" else "Optional — extra backup to Google Drive"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = cloudSyncEnabled,
                        onCheckedChange = {
                            cloudSyncEnabled = it
                            if (it) doctorViewModel.checkDriveConnection()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Primary)
                    )
                }
                if (cloudSyncEnabled && doctorState.driveConnected == false) {
                    TextButton(onClick = { doctorViewModel.checkDriveConnection() }) {
                        Text(if (isThai) "ลองเชื่อมต่ออีกครั้ง" else "Retry connection", color = Primary)
                    }
                }
            }

            if (captchaRequired && !bypassed) {
                HCaptchaVerifyButton(token = captchaToken, onTokenChange = { captchaToken = it })
            }

            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = agreedToTerms, onCheckedChange = { agreedToTerms = it }, colors = CheckboxDefaults.colors(checkedColor = Primary))
                Text(s.agreeTerms, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            (formError ?: uiState.error)?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }

            PrimaryButton(
                text = if (isThai) "สมัครสมาชิก" else "Register",
                isLoading = uiState.isLoading,
                enabled = !uiState.isLoading,
                onClick = {
                    formError = null
                    passwordMismatch = false
                    attemptCount++
                    val elapsed = System.currentTimeMillis() - screenOpenedAtMs
                    if (!bypassed && (elapsed < CAPTCHA_FAST_SUBMIT_THRESHOLD_MS || attemptCount >= CAPTCHA_RETRY_BURST_THRESHOLD)) {
                        captchaRequired = true
                    }
                    val fullName = "$firstName $lastName".trim()
                    when {
                        alreadySignedIn ->
                            formError = if (isThai) "กรุณาออกจากระบบบัญชีเดิมก่อนสมัครใหม่" else "Please sign out of your current account before registering a new one"
                        firstName.isBlank() || lastName.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank() ->
                            formError = if (isThai) "กรุณากรอกข้อมูลให้ครบทุกช่อง" else "Please fill in all required fields"
                        password.length < 8 ->
                            formError = if (isThai) "รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร" else "Password must be at least 8 characters"
                        password != confirmPassword ->
                            passwordMismatch = true
                        !agreedToTerms ->
                            formError = if (isThai) "กรุณายอมรับข้อตกลงและเงื่อนไขการใช้งานก่อนสมัคร" else "Please accept the terms before registering"
                        captchaRequired && !bypassed && captchaToken == null ->
                            formError = if (isThai) "ตรวจพบความผิดปกติ กรุณายืนยันว่าคุณไม่ใช่บอทก่อนสมัครสมาชิก" else "Please verify you're not a robot before registering"
                        // phone is named, NOT positional: it sits before the trailing onSuccess lambda,
                        // and leaving it to its default is exactly how the number was being discarded.
                        else -> authViewModel.signUpDoctor(
                            email, password, fullName, captchaToken,
                            // Raw: AuthRepository.signUp already runs ThaiPhone.normalize on it.
                            phone = phone.ifBlank { null },
                            onNeedsConfirmation = { awaitingConfirmation = true },
                            onSuccess = { finishDoctorSignUp() }
                        )
                    }
                }
            )

            if (ENABLE_GOOGLE_SIGN_IN) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        if (isThai) " หรือ " else " or ",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.lullabyai.app.ui.theme.Outline
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }
                OutlinedButton(
                    onClick = {
                        formError = null
                        when {
                            alreadySignedIn ->
                                formError = if (isThai) "กรุณาออกจากระบบบัญชีเดิมก่อนสมัครใหม่" else "Please sign out before registering a new account"
                            !agreedToTerms ->
                                formError = if (isThai) "กรุณายอมรับข้อตกลงก่อนสมัครด้วย Google" else "Please accept the terms before signing up with Google"
                            else -> googleSignUp()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(100.dp),
                    enabled = !uiState.isLoading,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Filled.AccountCircle, null, tint = Color(0xFF4285F4), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isThai) "สมัครด้วย Google" else "Sign up with Google",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isThai) "หากมีบัญชีอยู่แล้ว?" else "Already have an account?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { navController.navigate(Screen.DoctorSignIn.route) }, contentPadding = PaddingValues(4.dp)) {
                    Text(if (isThai) "เข้าสู่ระบบที่นี่" else "Sign in here", color = Primary, fontWeight = FontWeight.Bold)
                }
            }

            Text("NongRak AI © 2026 All Rights Reserved.", style = MaterialTheme.typography.labelSmall, color = Outline)
            Spacer(Modifier.height(24.dp))
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
