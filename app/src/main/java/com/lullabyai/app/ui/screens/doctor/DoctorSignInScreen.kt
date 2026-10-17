package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.GoogleLogo
import com.lullabyai.app.ui.components.HCaptchaVerifyButton
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.components.rememberGoogleSignInAction
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.auth.CAPTCHA_BYPASS_EMAILS
import com.lullabyai.app.ui.screens.auth.CAPTCHA_FAST_SUBMIT_THRESHOLD_MS
import com.lullabyai.app.ui.screens.auth.CAPTCHA_RETRY_BURST_THRESHOLD
import com.lullabyai.app.ui.screens.auth.ENABLE_GOOGLE_SIGN_IN
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel

/**
 * Real doctor email/password sign-in form. Stitch only ships a "Signing In" loading/transition
 * screen ([DoctorLoginScreen]) with no credentials fields — this fills that product gap by
 * mirroring parent [com.lullabyai.app.ui.screens.auth.LoginScreen] patterns (AuthViewModel.signIn,
 * risk-based captcha, forgot-password + sign-up links) while matching DoctorWelcome / DoctorSignUp
 * branding (Scaffold + LullabyTopBar, MedicalServices mark, Professional subtitle).
 *
 * Decision #9 B (multi-account): session persists; no device binding on sign-in.
 * Google Sign-In uses parent accountType on the wire, then [AuthViewModel.resolveHomeAfterAuth]
 * rejects non-doctor profiles.
 */
@Composable
fun DoctorSignInScreen(
    authViewModel: AuthViewModel,
    navController: NavController,
    settingsViewModel: com.lullabyai.app.viewmodel.SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by authViewModel.uiState.collectAsState()
    val s = LocalStrings.current
    val isThai = s.isThai
    var formError by remember { mutableStateOf<String?>(null) }
    val googleSignIn = rememberGoogleSignInAction(
        authViewModel = authViewModel,
        isThai = isThai,
        accountType = "doctor",
        onSuccess = {
            authViewModel.resolveDoctorPortalAfterAuth(
                isThai = isThai,
                onNeedsRegistration = {
                    navController.navigate(Screen.DoctorGoogleCompleteProfile.route) {
                        popUpTo(Screen.DoctorWelcome.route) { inclusive = true }
                    }
                },
                onDashboard = {
                    navController.navigate(Screen.DoctorDashboard.route) {
                        popUpTo(Screen.DoctorWelcome.route) { inclusive = true }
                    }
                },
                onRejected = { formError = it }
            )
        }
    )

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var captchaToken by remember { mutableStateOf<String?>(null) }
    var captchaRequired by remember { mutableStateOf(false) }
    var attemptCount by remember { mutableStateOf(0) }
    val screenOpenedAtMs = remember { System.currentTimeMillis() }
    val bypassed = email.trim().lowercase() in CAPTCHA_BYPASS_EMAILS

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "เข้าสู่ระบบแพทย์" else "Doctor Sign In",
                onBack = { navController.popBackStack() },
                navController = navController,
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
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            Box(
                Modifier.size(72.dp).clip(CircleShape).background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MedicalServices, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }

            Text(
                "NongRak AI Professional",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Primary,
                textAlign = TextAlign.Center
            )
            Text(
                if (isThai) "เข้าสู่ระบบสำหรับบุคลากรทางการแพทย์" else "Sign in for healthcare professionals",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            s.email,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; authViewModel.clearError(); formError = null },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("name@example.com", color = Outline) },
                            leadingIcon = { Icon(Icons.Filled.Email, null, tint = Outline) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                s.password,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            // Doctor-specific route (same reset-flow screen as parent, different
                            // "back to login" destination) so completing the reset never dumps a
                            // doctor into parent mode.
                            TextButton(
                                onClick = { navController.navigate(Screen.DoctorForgotPassword.route) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(s.forgotPassword, style = MaterialTheme.typography.labelMedium, color = Primary)
                            }
                        }
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; authViewModel.clearError(); formError = null },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Filled.Lock, null, tint = Outline) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        null,
                                        tint = Outline
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    }

                    if (captchaRequired && !bypassed) {
                        HCaptchaVerifyButton(token = captchaToken, onTokenChange = { captchaToken = it })
                    }

                    (formError ?: uiState.error)?.let {
                        Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
                    }

                    PrimaryButton(
                        text = if (isThai) "เข้าสู่ระบบแพทย์" else "Doctor sign in",
                        onClick = {
                            formError = null
                            attemptCount++
                            val elapsed = System.currentTimeMillis() - screenOpenedAtMs
                            if (!bypassed && (elapsed < CAPTCHA_FAST_SUBMIT_THRESHOLD_MS || attemptCount >= CAPTCHA_RETRY_BURST_THRESHOLD)) {
                                captchaRequired = true
                            }
                            when {
                                email.isBlank() || password.isBlank() ->
                                    formError = if (isThai) "กรุณากรอกอีเมลและรหัสผ่านให้ครบถ้วน" else "Please enter email and password"
                                captchaRequired && !bypassed && captchaToken == null ->
                                    formError = if (isThai)
                                        "ตรวจพบความผิดปกติ กรุณายืนยันว่าคุณไม่ใช่บอทก่อนเข้าสู่ระบบ"
                                    else
                                        "Unusual activity detected — please verify you're not a robot before signing in"
                                else -> authViewModel.signIn(email, password, captchaToken) { mustChange ->
                                    if (mustChange) {
                                        navController.navigate(Screen.ForceChangePassword.route) {
                                            popUpTo(Screen.DoctorWelcome.route)
                                        }
                                        return@signIn
                                    }
                                    authViewModel.resolveDoctorPortalAfterAuth(
                                        isThai = isThai,
                                        onNeedsRegistration = {
                                            navController.navigate(Screen.DoctorGoogleCompleteProfile.route) {
                                                popUpTo(Screen.DoctorWelcome.route) { inclusive = true }
                                            }
                                        },
                                        onDashboard = {
                                            navController.navigate(Screen.DoctorDashboard.route) {
                                                popUpTo(Screen.DoctorWelcome.route) { inclusive = true }
                                            }
                                        },
                                        onRejected = { formError = it }
                                    )
                                }
                            }
                        },
                        isLoading = uiState.isLoading,
                        enabled = email.isNotBlank() && password.isNotBlank() && !uiState.isLoading
                    )

                    if (ENABLE_GOOGLE_SIGN_IN) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                if (isThai) " หรือ " else " or ",
                                style = MaterialTheme.typography.bodySmall,
                                color = Outline
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        OutlinedButton(
                            onClick = {
                                formError = null
                                googleSignIn()
                            },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(100.dp),
                            enabled = !uiState.isLoading,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            GoogleLogo()
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (isThai) "เข้าสู่ระบบด้วย Google" else "Sign in with Google",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    s.noAccount,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { navController.navigate(Screen.DoctorSignUp.route) },
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text(
                        if (isThai) "สมัครสมาชิกแพทย์" else "Doctor sign up",
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                "NongRak AI © 2026 All Rights Reserved.",
                style = MaterialTheme.typography.labelSmall,
                color = Outline
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
