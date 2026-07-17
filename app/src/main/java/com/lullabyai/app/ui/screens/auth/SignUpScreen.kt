package com.lullabyai.app.ui.screens.auth

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
import com.lullabyai.app.data.settings.AppLanguage
import com.lullabyai.app.ui.components.LanguageToggleButton
import com.lullabyai.app.ui.components.PrimaryButton
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
    val context = androidx.compose.ui.platform.LocalContext.current
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var showConfirmPassword by remember { mutableStateOf(false) }
    var agreedToTerms by remember { mutableStateOf(false) }
    var passwordMismatch by remember { mutableStateOf(false) }
    var formError by remember { mutableStateOf<String?>(null) }
    var captchaToken by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        LanguageToggleButton(
            isThai = settings.language == AppLanguage.TH,
            onToggle = { settingsViewModel.toggleLanguage() },
            modifier = Modifier.align(Alignment.TopEnd).systemBarsPadding().padding(20.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

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
                            placeholder = { Text("ป้อนชื่อและนามสกุลของคุณ", color = Outline) },
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
                    LabeledField(s.password) {
                        OutlinedTextField(
                            value = password, onValueChange = { password = it; passwordMismatch = false },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("อย่างน้อย 8 ตัวอักษร", color = Outline) },
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
                            placeholder = { Text("ยืนยันรหัสผ่านอีกครั้ง", color = Outline) },
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

                    // hCaptcha temporarily disabled — the WebView-based widget renders blank on the
                    // emulator (GPU compositing bug, GFXSTREAM/Mesa errors in Logcat), blocking
                    // signup entirely. Re-enable HCaptchaWidget below once verified working on a
                    // real device. See MEMORY_GRAPH.md for the fix attempts already tried.

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
                            when {
                                fullName.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank() ->
                                    formError = "กรุณากรอกข้อมูลให้ครบทุกช่อง"
                                password.length < 8 ->
                                    formError = "รหัสผ่านต้องมีอย่างน้อย 8 ตัวอักษร"
                                password != confirmPassword ->
                                    passwordMismatch = true
                                !agreedToTerms ->
                                    formError = "กรุณายอมรับข้อตกลงและเงื่อนไขการใช้งานก่อนสมัคร"
                                else ->
                                    authViewModel.signUp(email, password, fullName, captchaToken, onSignUpSuccess)
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
                            onClick = { authViewModel.signInWithGoogle(context, onSignUpSuccess) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Icon(Icons.Filled.AccountCircle, null, tint = Color(0xFF4285F4), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("สมัครด้วย Google", style = MaterialTheme.typography.labelLarge)
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

        // Back to Welcome — placed last so it draws on top of the scrollable Column and gets taps
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).systemBarsPadding().padding(4.dp)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "กลับ", tint = Primary)
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
