package com.lullabyai.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
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
import com.lullabyai.app.data.settings.AppLanguage
import com.lullabyai.app.ui.components.HCaptchaVerifyButton
import com.lullabyai.app.ui.components.LanguageToggleButton
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.components.rememberGoogleSignInAction
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.SettingsViewModel

@Composable
fun SignUpScreen(
    authViewModel: AuthViewModel,
    settingsViewModel: SettingsViewModel,
    onSignUpSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onBack: () -> Unit = {}
) {
    val uiState by authViewModel.uiState.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = settings.language == AppLanguage.TH
    val googleSignIn = rememberGoogleSignInAction(
        authViewModel = authViewModel,
        isThai = isThai,
        onSuccess = onSignUpSuccess
    )
    val lineSignIn = com.lullabyai.app.ui.components.rememberLineSignInAction(
        authViewModel = authViewModel,
        isThai = isThai,
        onSuccess = onSignUpSuccess
    )
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var phoneError by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(false) }
    var passwordMismatch by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var captchaToken by remember { mutableStateOf<String?>(null) }
    val screenOpenedAtMs = remember { System.currentTimeMillis() }
    var captchaRequired by remember { mutableStateOf(false) }
    var attemptCount by remember { mutableStateOf(0) }
    val bypassed = email.trim().lowercase() in CAPTCHA_BYPASS_EMAILS

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.PersonAdd, null, tint = Color.White, modifier = Modifier.size(36.dp))
            }

            Spacer(Modifier.height(24.dp))

            Text(s.createAccount, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                s.signupSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LabeledField(s.fullName) {
                        OutlinedTextField(
                            value = fullName, onValueChange = { fullName = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(if (s.isThai) "ป้อนชื่อและนามสกุลของคุณ" else "Enter your full name", color = Outline) },
                            leadingIcon = { Icon(Icons.Filled.Person, null, tint = Outline) },
                            singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = fieldColors()
                        )
                    }
                    LabeledField(s.email) {
                        OutlinedTextField(
                            value = email, onValueChange = { email = it; authViewModel.clearError() },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("example@email.com", color = Outline) },
                            leadingIcon = { Icon(Icons.Filled.Email, null, tint = Outline) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = fieldColors()
                        )
                    }
                    LabeledField(if (s.isThai) "เบอร์โทรศัพท์" else "Phone number") {
                        OutlinedTextField(
                            value = phone, onValueChange = { phone = it; phoneError = false },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("0812345678", color = Outline) },
                            leadingIcon = { Icon(Icons.Filled.Phone, null, tint = Outline) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            isError = phoneError,
                            singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = fieldColors()
                        )
                        if (phoneError) Text(
                            if (s.isThai) "เบอร์โทรศัพท์ไม่ถูกต้อง กรุณากรอกเป็นตัวเลข เช่น 0812345678 หรือ +66812345678"
                            else "That phone number doesn't look right. Use a Thai number, e.g. 0812345678 or +66812345678",
                            color = Error, style = MaterialTheme.typography.bodySmall
                        )
                    }
                    LabeledField(s.password) {
                        OutlinedTextField(
                            value = password, onValueChange = { password = it; passwordMismatch = false },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(if (s.isThai) "อย่างน้อย 8 ตัวอักษร" else "At least 8 characters", color = Outline) },
                            leadingIcon = { Icon(Icons.Filled.Lock, null, tint = Outline) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = Outline)
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = fieldColors()
                        )
                        if (password.isNotEmpty()) {
                            PasswordStrengthBar(password, settings.language == AppLanguage.TH)
                        }
                    }

                    LabeledField(s.confirmPassword) {
                        OutlinedTextField(
                            value = confirmPassword, onValueChange = { confirmPassword = it; passwordMismatch = false },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(if (s.isThai) "ยืนยันรหัสผ่านอีกครั้ง" else "Re-enter your password", color = Outline) },
                            leadingIcon = { Icon(Icons.Filled.LockReset, null, tint = Outline) },
                            trailingIcon = {
                                IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                                    Icon(if (showConfirmPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = Outline)
                                }
                            },
                            visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            isError = passwordMismatch,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            singleLine = true, shape = RoundedCornerShape(12.dp),
                            colors = fieldColors()
                        )
                        if (passwordMismatch) Text(s.passwordMismatch, color = Error, style = MaterialTheme.typography.bodySmall)
                    }

                    // Invisible by default — only shown once the risk heuristic in the `when` block
                    // below trips (or never, for an already-flagged bypassed account).
                    if (captchaRequired && !bypassed) {
                        HCaptchaVerifyButton(token = captchaToken, onTokenChange = { captchaToken = it })
                    }

                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Checkbox(
                            checked = agreedToTerms,
                            onCheckedChange = { agreedToTerms = it },
                            colors = CheckboxDefaults.colors(checkedColor = Primary)
                        )
                        Text(
                            s.agreeTerms,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    (formError ?: uiState.error)?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }

                    PrimaryButton(
                        text = "${s.signUp} →",
                        onClick = {
                            // Always pressable: validate on tap and tell the user exactly what's missing,
                            // instead of leaving the button silently disabled.
                            formError = null
                            passwordMismatch = false
                            phoneError = false
                            attemptCount++
                            val elapsed = System.currentTimeMillis() - screenOpenedAtMs
                            if (!bypassed && (elapsed < CAPTCHA_FAST_SUBMIT_THRESHOLD_MS || attemptCount >= CAPTCHA_RETRY_BURST_THRESHOLD)) {
                                captchaRequired = true
                            }
                            val normalizedPhone = com.lullabyai.app.data.repository.ThaiPhone.normalize(phone)
                            when {
                                fullName.isBlank() || email.isBlank() || phone.isBlank() || password.isBlank() || confirmPassword.isBlank() ->
                                    formError = "กรุณากรอกข้อมูลให้ครบทุกช่อง"
                                normalizedPhone == null ->
                                    phoneError = true
                                password.length < 8 ->
                                    formError = "รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร"
                                password != confirmPassword ->
                                    passwordMismatch = true
                                !agreedToTerms ->
                                    formError = "กรุณายอมรับข้อตกลงและเงื่อนไขการใช้งานก่อนสมัคร"
                                captchaRequired && !bypassed && captchaToken == null ->
                                    formError = "ตรวจพบความผิดปกติ กรุณายืนยันว่าคุณไม่ใช่บอทก่อนสมัครสมาชิก"
                                else ->
                                    // phone passed NAMED and pre-normalized. It defaults to null in
                                    // signUp(), so omitting it still compiled but silently threw the
                                    // number away — the field would have looked like it worked.
                                    authViewModel.signUp(
                                        email, password, fullName, captchaToken,
                                        phone = normalizedPhone,
                                        onSuccess = onSignUpSuccess
                                    )
                            }
                        },
                        isLoading = uiState.isLoading,
                        enabled = !uiState.isLoading
                    )

                    @Suppress("KotlinConstantConditions")
                    if (ENABLE_GOOGLE_SIGN_IN) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                            Text(" หรือ ", style = MaterialTheme.typography.bodySmall, color = Outline)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        OutlinedButton(
                            onClick = googleSignIn,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Icon(Icons.Filled.AccountCircle, null, tint = Color(0xFF4285F4), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("สมัครด้วย Google", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    // Hidden until a Login channel is configured, so a build without one shows no
                    // button that cannot work. LINE sign-in also links notifications in the same
                    // step — there is no second pairing screen afterwards.
                    if (com.lullabyai.app.util.LineAuthHelper.isConfigured) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = lineSignIn,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF06C755),   // LINE brand green
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Filled.Chat, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("สมัครด้วย LINE", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.haveAccount, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onNavigateToLogin, contentPadding = PaddingValues(4.dp)) {
                    Text(s.login, color = Primary, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        // Top bar actions: Back and Language Toggle — placed last so they draw on top of the scrollable Column and receive all taps
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (s.isThai) "ย้อนกลับ" else "Back",
                    tint = Primary
                )
            }
            LanguageToggleButton(
                isThai = settings.language == AppLanguage.TH,
                onToggle = { settingsViewModel.toggleLanguage() }
            )
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
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
)

/** Live strength meter below the password field — advisory only, doesn't block submission
 *  (the 8-char minimum is still enforced separately on submit). */
@Composable
internal fun PasswordStrengthBar(password: String, isThai: Boolean) {
    val result = com.lullabyai.app.util.PasswordStrength.check(password)
    val (barColor, fraction) = when (result.level) {
        com.lullabyai.app.util.PasswordStrength.Level.WEAK -> Error to 0.25f
        com.lullabyai.app.util.PasswordStrength.Level.FAIR -> Color(0xFFEF6C00) to 0.5f
        com.lullabyai.app.util.PasswordStrength.Level.GOOD -> Color(0xFFFFC107) to 0.75f
        com.lullabyai.app.util.PasswordStrength.Level.STRONG -> Color(0xFF2E7D32) to 1f
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(100.dp)),
            color = barColor, trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Text(
            com.lullabyai.app.util.PasswordStrength.label(result.level, isThai) +
                (if (isThai) " • ใช้ตัวพิมพ์ใหญ่-เล็ก ตัวเลข และสัญลักษณ์เพื่อความปลอดภัยมากขึ้น" else " • Mix upper/lowercase, numbers, and symbols for a stronger password"),
            style = MaterialTheme.typography.labelSmall, color = barColor
        )
    }
}
