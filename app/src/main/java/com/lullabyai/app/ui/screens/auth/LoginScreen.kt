package com.lullabyai.app.ui.screens.auth

import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.lullabyai.app.ui.components.GoogleLogo
import com.lullabyai.app.ui.components.HCaptchaVerifyButton
import com.lullabyai.app.ui.components.LanguageToggleButton
import com.lullabyai.app.ui.components.LineLogo
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.components.rememberGoogleSignInAction
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AuthViewModel
import com.lullabyai.app.viewmodel.SettingsViewModel

// Requires the OAuth client's SHA-1/SHA-256 fingerprint (debug AND release) to be registered
// against com.publish.NongRak in Google Cloud Console, or Credential Manager sign-in will fail
// even with a valid GOOGLE_WEB_CLIENT_ID. See MEMORY_GRAPH.md for why this was disabled before.
const val ENABLE_GOOGLE_SIGN_IN = true

// Accounts that can sign in/sign up without completing the hCaptcha challenge — trusted dev/test
// accounts, so iterating on the app isn't blocked by the bot-check gate applied to everyone else.
val CAPTCHA_BYPASS_EMAILS = setOf("41801@sbw.ac.th", "julachok@gmail.com")

// Risk-based captcha for Login/SignUp: hidden by default, only shown+required once one of these
// (deliberately simple, honest heuristics — no real ML/device risk signal available client-side)
// trips: submitting faster than a human could plausibly type the form, or repeated attempts on
// the same screen visit (which only happens if earlier attempts failed, since success navigates
// away). Forgot Password is unconditional — see ForgotPasswordScreen.kt.
const val CAPTCHA_FAST_SUBMIT_THRESHOLD_MS = 2500L
const val CAPTCHA_RETRY_BURST_THRESHOLD = 3

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
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var captchaToken by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    val isThai = settings.language == AppLanguage.TH
    val context = androidx.compose.ui.platform.LocalContext.current
    val biometricLogin = com.lullabyai.app.data.settings.BiometricLogin
    var savedBiometricSignIn by remember { mutableStateOf(runCatching { biometricLogin.hasSavedSignIn(context) }.getOrDefault(false)) }
    val savedEmail = remember { runCatching { biometricLogin.enrolledEmail(context) }.getOrNull() }
    val savedName = remember { runCatching { biometricLogin.displayName(context) }.getOrNull() }
    // Opens by itself when this phone has a saved sign-in: "ต้องการเข้าสู่ระบบบัญชีนี้หรือไม่?"
    var showSavedAccountPrompt by remember { mutableStateOf(savedBiometricSignIn) }

    // Set while the 2FA code question is open; running it continues into the app.
    val authScope = rememberCoroutineScope()
    var afterTotp by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun biometricSignIn() {
        formError = null
        com.lullabyai.app.util.AppLock.authenticate(
            context,
            if (isThai) "เข้าสู่ระบบ NongRak" else "Sign in to NongRak",
            savedName ?: savedEmail ?: (if (isThai) "ยืนยันด้วยใบหน้า หรือลายนิ้วมือ" else "Use your face or fingerprint")
        ) { ok, message ->
            if (ok) {
                com.lullabyai.app.util.AppLock.unlocked = true
                authViewModel.signInWithBiometric { mustChange ->
                    // Same gate as the password path: a key saved before 2FA was switched on
                    // restores an aal1 session, which still owes a code.
                    val proceed = { if (mustChange) onNeedChangePassword() else onLoginSuccess() }
                    if (com.lullabyai.app.data.repository.AuthRepository().totpChallengePending()) {
                        afterTotp = proceed
                    } else {
                        proceed()
                    }
                }
                // The key is single-use; hide the button until the next logout saves a new one.
                savedBiometricSignIn = false
            } else if (message != null) {
                formError = message
            }
        }
    }

    afterTotp?.let { proceed ->
        com.lullabyai.app.ui.screens.auth.TotpChallengeDialog(
            isThai = isThai,
            onVerified = { afterTotp = null; proceed() },
            onCancel = {
                afterTotp = null
                // Half-authenticated is not signed in: drop the aal1 session rather than keep it.
                authScope.launch { com.lullabyai.app.data.repository.AuthRepository().signOut() }
                formError = if (isThai) "ยกเลิกการเข้าสู่ระบบแล้ว" else "Sign-in cancelled"
            }
        )
    }

    // Set while the "use biometrics next time?" question is open; running it continues into the app.
    var pendingAfterOffer by remember { mutableStateOf<(() -> Unit)?>(null) }

    /** Every successful sign-in passes through here, so the biometric offer is made once, in one place. */
    fun finishLogin(next: () -> Unit) {
        val userId = authViewModel.getCurrentUserId()
        val offer = userId != null && runCatching { biometricLogin.shouldOffer(context, userId) }.getOrDefault(false)
        if (offer) pendingAfterOffer = next else next()
    }

    val googleSignIn = rememberGoogleSignInAction(
        authViewModel = authViewModel,
        isThai = isThai,
        onSuccess = { finishLogin(onLoginSuccess) }
    )
    val lineSignIn = com.lullabyai.app.ui.components.rememberLineSignInAction(
        authViewModel = authViewModel,
        isThai = isThai,
        onSuccess = { finishLogin(onLoginSuccess) }
    )
    val screenOpenedAtMs = remember { System.currentTimeMillis() }
    var captchaRequired by remember { mutableStateOf(false) }
    var attemptCount by remember { mutableStateOf(0) }
    val bypassed = email.trim().lowercase() in CAPTCHA_BYPASS_EMAILS

    LaunchedEffect(uiState.error) {
        if (uiState.error != null) {
            // Error shown inline
        }
    }

    if (showSavedAccountPrompt && savedBiometricSignIn) {
        AlertDialog(
            onDismissRequest = { showSavedAccountPrompt = false },
            icon = { Icon(Icons.Filled.AccountCircle, null, tint = Primary, modifier = Modifier.size(52.dp)) },
            title = {
                Text(
                    if (isThai) "ต้องการเข้าสู่ระบบบัญชีนี้หรือไม่?" else "Sign in to this account?",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(savedName ?: savedEmail ?: "-", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    if (!savedName.isNullOrBlank() && !savedEmail.isNullOrBlank()) {
                        Text(savedEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isThai) "ยืนยันด้วยลายนิ้วมือหรือใบหน้า ไม่ต้องใส่รหัสผ่าน" else "Confirm with fingerprint or face — no password",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showSavedAccountPrompt = false; biometricSignIn() }, shape = RoundedCornerShape(100.dp)) {
                    Icon(Icons.Filled.Fingerprint, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isThai) "เข้าสู่ระบบ" else "Sign in")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSavedAccountPrompt = false }) {
                    Text(if (isThai) "ใช้บัญชีอื่น" else "Use another account")
                }
            }
        )
    }

    pendingAfterOffer?.let { proceed ->
        val userId = authViewModel.getCurrentUserId()
        val replacing = runCatching { biometricLogin.enrolledUserId(context) }.getOrNull().let { it != null && it != userId }
        fun done() { pendingAfterOffer = null; proceed() }
        AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Filled.Fingerprint, null, tint = Primary, modifier = Modifier.size(36.dp)) },
            title = {
                Text(
                    if (isThai) "ใช้ลายนิ้วมือ / ใบหน้าเข้าสู่ระบบครั้งถัดไป?" else "Use fingerprint / face next time?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    (if (isThai) "ครั้งหน้าหลังออกจากระบบ แตะครั้งเดียวแล้วสแกนก็เข้าได้เลย ไม่ต้องพิมพ์รหัสผ่าน ใช้ได้ 1 บัญชีต่อเครื่อง"
                    else "Next time after logging out, one tap and a scan signs you in — no password. One account per phone.") +
                        if (replacing) (if (isThai) "\n\nบัญชีที่ตั้งไว้ก่อนหน้าบนเครื่องนี้จะถูกแทนที่" else "\n\nThe account set up before on this phone will be replaced.") else ""
                )
            },
            confirmButton = {
                Button(onClick = {
                    com.lullabyai.app.util.AppLock.authenticate(
                        context,
                        if (isThai) "ตั้งค่าเข้าสู่ระบบด้วยลายนิ้วมือ" else "Set up fingerprint sign-in",
                        if (isThai) "ยืนยันว่าเครื่องนี้สแกนได้" else "Confirm this phone can scan you"
                    ) { ok, message ->
                        if (ok && userId != null) {
                            com.lullabyai.app.util.AppLock.unlocked = true
                            runCatching {
                                biometricLogin.enroll(context, userId, com.lullabyai.app.data.repository.AuthRepository().currentUser?.email)
                            }
                        } else if (message != null) {
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                        }
                        done()
                    }
                }, shape = RoundedCornerShape(100.dp)) {
                    Text(if (isThai) "ใช้เลย" else "Turn on")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (userId != null) runCatching { biometricLogin.decline(context, userId) }
                    done()
                }) { Text(if (isThai) "ไม่ตอนนี้" else "Not now") }
            }
        )
    }

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

            // One-tap return for the account enrolled on this phone.
            if (savedBiometricSignIn) {
                Button(
                    onClick = { biometricSignIn() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.Fingerprint, null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(if (isThai) "เข้าสู่ระบบด้วยลายนิ้วมือ / ใบหน้า" else "Sign in with fingerprint / face", fontWeight = FontWeight.Bold)
                        (savedName ?: savedEmail)?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

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
                            onValueChange = { password = it; authViewModel.clearError(); formError = null },
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

                    // Invisible by default — only shown once the risk heuristic below trips (or for
                    // an already-flagged bypassed account, never at all).
                    if (captchaRequired && !bypassed) {
                        HCaptchaVerifyButton(token = captchaToken, onTokenChange = { captchaToken = it })
                    }

                    (formError ?: uiState.error)?.let {
                        Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
                    }
                    // friendlyAuthError's "not confirmed" text: the parent closed the confirm dialog
                    // earlier, so offer the link again right where the error is.
                    if (formError == null && uiState.error?.contains("ยังไม่ได้รับการยืนยัน") == true && email.isNotBlank()) {
                        val resendScope = rememberCoroutineScope()
                        var resendNote by remember { mutableStateOf<String?>(null) }
                        TextButton(onClick = {
                            resendScope.launch {
                                resendNote = com.lullabyai.app.data.repository.AuthRepository()
                                    .resendSignUpConfirmation(email.trim())
                                    .fold(
                                        onSuccess = { if (isThai) "ส่งลิงก์ยืนยันไปที่ ${email.trim()} แล้ว" else "Confirmation link sent to ${email.trim()}" },
                                        onFailure = { (if (isThai) "ส่งไม่สำเร็จ: " else "Could not send: ") + (it.message ?: it::class.simpleName) }
                                    )
                            }
                        }) { Text(if (isThai) "ส่งลิงก์ยืนยันอีกครั้ง" else "Resend confirmation link") }
                        resendNote?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }

                    PrimaryButton(
                        text = s.login,
                        onClick = {
                            formError = null
                            attemptCount++
                            val elapsed = System.currentTimeMillis() - screenOpenedAtMs
                            if (!bypassed && (elapsed < CAPTCHA_FAST_SUBMIT_THRESHOLD_MS || attemptCount >= CAPTCHA_RETRY_BURST_THRESHOLD)) {
                                captchaRequired = true
                            }
                            if (captchaRequired && !bypassed && captchaToken == null) {
                                formError = if (isThai) "ตรวจพบความผิดปกติ กรุณายืนยันว่าคุณไม่ใช่บอทก่อนเข้าสู่ระบบ" else "Unusual activity detected — please verify you're not a robot before signing in"
                            } else {
                                authViewModel.signIn(email, password, captchaToken) { mustChange ->
                                    val proceed = { finishLogin { if (mustChange) onNeedChangePassword() else onLoginSuccess() } }
                                    // The password alone only buys an aal1 session; accounts with an
                                    // authenticator owe a code before they reach any of their data.
                                    if (com.lullabyai.app.data.repository.AuthRepository().totpChallengePending()) {
                                        afterTotp = proceed
                                    } else {
                                        proceed()
                                    }
                                }
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
                            onClick = googleSignIn,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                        ) {
                            GoogleLogo()
                            Spacer(Modifier.width(8.dp))
                            Text("เข้าสู่ระบบด้วย Google", style = MaterialTheme.typography.labelLarge)
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
                            LineLogo()
                            Spacer(Modifier.width(8.dp))
                            Text("เข้าสู่ระบบด้วย LINE", style = MaterialTheme.typography.labelLarge)
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
                    contentDescription = if (isThai) "ย้อนกลับ" else "Back",
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
