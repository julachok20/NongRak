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
import com.lullabyai.app.ui.components.HCaptchaVerifyButton
import com.lullabyai.app.ui.components.LanguageToggleButton
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.SettingsViewModel

// Toggle this to true once Google OAuth is fully set up to show the Google button again.
const val ENABLE_GOOGLE_SIGN_IN = false

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    settingsViewModel: SettingsViewModel,
    onLoginSuccess: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit = {},
    onNeedChangePassword: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val uiState by authViewModel.uiState.collectAsState()
    val settings by settingsViewModel.settings.collectAsState()
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var captchaToken by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            // Error shown inline
        }
    }

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

            // Logo
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
            }

            Spacer(Modifier.height(24.dp))

            Text(s.loginTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                s.loginSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // Form card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Email
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(s.email, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; authViewModel.clearError() },
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
                    // Password
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(s.password, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = onNavigateToForgotPassword, contentPadding = PaddingValues(0.dp)) {
                                Text(s.forgotPassword, style = MaterialTheme.typography.labelMedium, color = Primary)
                            }
                        }
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it; authViewModel.clearError() },
                            modifier = Modifier.fillMaxWidth(),
                            leadingIcon = { Icon(Icons.Filled.Lock, null, tint = Outline) },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, null, tint = Outline)
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

                    // Re-added for the test version — kept NON-BLOCKING: the login button below
                    // never gates on captchaToken, so if the widget fails to render on a given
                    // device (the WebView GPU-compositing bug it once hit on some emulators —
                    // HCaptchaWidget already forces software rendering to work around that) login
                    // still goes through with a null token instead of locking anyone out.
                    HCaptchaVerifyButton(token = captchaToken, onTokenChange = { captchaToken = it })

                    uiState.error?.let {
                        Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
                    }

                    PrimaryButton(
                        text = s.login,
                        onClick = {
                            authViewModel.signIn(email, password, captchaToken) { mustChange ->
                                if (mustChange) onNeedChangePassword() else onLoginSuccess()
                            }
                        },
                        isLoading = uiState.isLoading,
                        enabled = email.isNotBlank() && password.isNotBlank()
                    )

                    // Google sign-in temporarily hidden. Set ENABLE_GOOGLE = true to restore.
                    @Suppress("KotlinConstantConditions")
                    if (ENABLE_GOOGLE_SIGN_IN) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                            Text(" หรือ ", style = MaterialTheme.typography.bodySmall, color = Outline)
                            HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                        }

                        OutlinedButton(
                            onClick = { authViewModel.signInWithGoogle(context, onLoginSuccess) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            Icon(Icons.Filled.AccountCircle, null, tint = Color(0xFF4285F4), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("เข้าสู่ระบบด้วย Google", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(s.noAccount, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onNavigateToSignUp, contentPadding = PaddingValues(4.dp)) {
                    Text(s.signUp, color = Primary, fontWeight = FontWeight.Bold)
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
