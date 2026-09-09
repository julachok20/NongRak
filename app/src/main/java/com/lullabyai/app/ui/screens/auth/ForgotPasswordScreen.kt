@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.ui.components.HCaptchaVerifyButton
import com.lullabyai.app.ui.components.LanguageToggleButton
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ForgotPasswordViewModel
import com.lullabyai.app.viewmodel.ForgotStep
import com.lullabyai.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

@Composable
fun ForgotPasswordScreen(
    viewModel: ForgotPasswordViewModel,
    onBackToLogin: () -> Unit,
    settingsViewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val state by viewModel.state.collectAsState()
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = when (state.step) {
                    ForgotStep.EMAIL -> if (isThai) "ลืมรหัสผ่าน" else "Forgot Password"
                    ForgotStep.OTP -> if (isThai) "ยืนยันรหัส OTP" else "Verify OTP"
                    ForgotStep.TEMP_PASSWORD -> if (isThai) "รหัสผ่านชั่วคราว" else "Temporary Password"
                },
                onBack = {
                    when (state.step) {
                        ForgotStep.OTP -> viewModel.back()
                        else -> onBackToLogin()
                    }
                },
                actions = {
                    LanguageToggleButton(
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
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state.step) {
                ForgotStep.EMAIL -> EmailStep(viewModel, state.isLoading, state.error, onBackToLogin)
                ForgotStep.OTP -> OtpStep(viewModel, state.isLoading, state.error, state.infoMessage)
                ForgotStep.TEMP_PASSWORD -> TempPasswordStep(state.tempPassword ?: "", onBackToLogin)
            }
        }
    }
}

/* ----------------------------- Step 1: Email ----------------------------- */

@Composable
private fun EmailStep(
    viewModel: ForgotPasswordViewModel,
    isLoading: Boolean,
    error: String?,
    onBackToLogin: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var captchaToken by remember { mutableStateOf<String?>(null) }

    Spacer(Modifier.height(24.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(64.dp).clip(CircleShape).background(Primary.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.LockReset, null, tint = Primary, modifier = Modifier.size(34.dp)) }

            Text("รีเซ็ตรหัสผ่าน", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "ไม่ต้องกังวล เราจะช่วยให้คุณกลับมาดูแลลูกน้อยได้โดยเร็วที่สุด กรุณากรอกอีเมลของคุณด้านล่างเพื่อรับรหัส OTP",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ที่อยู่อีเมล", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("เช่น parent@email.com", color = Outline) },
                    leadingIcon = { Icon(Icons.Filled.MailOutline, null, tint = Outline) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }

            // Mandatory for everyone — no risk-based skip, no dev-account bypass (unlike Login/SignUp).
            HCaptchaVerifyButton(token = captchaToken, onTokenChange = { captchaToken = it })

            if (error != null) {
                Text(error, color = Error, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
            }

            Button(
                onClick = { viewModel.sendOtp(email, captchaToken) },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(100.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                } else {
                    Text("ส่งลิงก์รีเซ็ต", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.Send, null, modifier = Modifier.size(18.dp))
                }
            }

            TextButton(onClick = onBackToLogin) {
                Icon(Icons.Filled.Login, null, tint = Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("กลับไปหน้าเข้าสู่ระบบ", color = Primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/* ----------------------------- Step 2: OTP ----------------------------- */

@Composable
private fun OtpStep(
    viewModel: ForgotPasswordViewModel,
    isLoading: Boolean,
    error: String?,
    infoMessage: String?
) {
    var code by remember { mutableStateOf("") }

    Spacer(Modifier.height(16.dp))
    // Decorative illustration circle with a shield badge
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier.size(150.dp).clip(CircleShape).background(Primary.copy(0.10f)),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.MarkEmailRead, null, tint = Primary, modifier = Modifier.size(72.dp)) }
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).background(Primary),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Filled.VerifiedUser, null, tint = Color.White, modifier = Modifier.size(24.dp)) }
    }

    Spacer(Modifier.height(20.dp))
    Text(
        "กรอกรหัสยืนยัน 6 หลักที่เราส่งไปที่อีเมลของคุณ",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Text(
        "(Enter the 6-digit verification code we sent to your email)",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(24.dp))
    OtpBoxes(value = code, onValueChange = { code = it })

    if (error != null) {
        Spacer(Modifier.height(12.dp))
        Text(error, color = Error, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
    }
    if (infoMessage != null) {
        Spacer(Modifier.height(12.dp))
        Text(infoMessage, color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        LaunchedEffect(infoMessage) { delay(2500); viewModel.clearInfo() }
    }

    Spacer(Modifier.height(24.dp))
    Button(
        onClick = { viewModel.verifyOtp(code) },
        enabled = !isLoading && code.length == 6,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary)
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        } else {
            Text("ยืนยัน (Verify)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }

    TextButton(onClick = { viewModel.resendOtp() }, enabled = !isLoading) {
        Text("ส่งรหัสอีกครั้ง (Resend OTP)", color = Primary, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OtpBoxes(value: String, onValueChange: (String) -> Unit, length: Int = 6) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= length && it.all(Char::isDigit)) onValueChange(it) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.focusRequester(focusRequester),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(length) { i ->
                    val ch = value.getOrNull(i)?.toString() ?: ""
                    val filled = ch.isNotEmpty()
                    Box(
                        modifier = Modifier
                            .size(width = 46.dp, height = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                width = 1.5.dp,
                                color = if (filled) Primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(ch, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    )
}

/* ----------------------------- Step 3: Temporary Password ----------------------------- */

@Composable
private fun TempPasswordStep(tempPassword: String, onBackToLogin: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Spacer(Modifier.height(40.dp))
    Text("Temporary Password", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text("รหัสผ่านชั่วคราว", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Spacer(Modifier.height(24.dp))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                tempPassword,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Primary
            )
            IconButton(onClick = {
                clipboard.setText(AnnotatedString(tempPassword))
                copied = true
            }) {
                Icon(Icons.Filled.ContentCopy, "คัดลอก", tint = Primary)
            }
        }
    }

    if (copied) {
        Spacer(Modifier.height(8.dp))
        Text("คัดลอกแล้ว", color = Color(0xFF2E7D32), style = MaterialTheme.typography.labelMedium)
        LaunchedEffect(Unit) { delay(2000); copied = false }
    }

    Spacer(Modifier.height(16.dp))
    Text(
        "กรุณาใช้รหัสผ่านชั่วคราวนี้ในการเข้าสู่ระบบ และเปลี่ยนรหัสผ่านใหม่ในการตั้งค่า",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 8.dp)
    )

    Spacer(Modifier.height(40.dp))
    Button(
        onClick = onBackToLogin,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(100.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Primary)
    ) {
        Text("กลับไปยังหน้าเข้าสู่ระบบ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
    Spacer(Modifier.height(24.dp))
}
