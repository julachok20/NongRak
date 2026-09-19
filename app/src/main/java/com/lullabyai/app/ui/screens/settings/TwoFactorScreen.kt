package com.lullabyai.app.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PhonelinkLock
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.data.repository.AuthRepository
import com.lullabyai.app.data.repository.TotpEnrollment
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.Error
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.util.AccessQrHelper
import kotlinx.coroutines.launch

private val OkGreen = Color(0xFF12805C)
private const val CODE_LENGTH = 6

/**
 * ยืนยันตัวตน 2 ขั้นตอน (2FA) with an authenticator app — Supabase MFA (TOTP).
 *
 * Turning it on is two steps on purpose: enrolling only registers an unverified factor, and the
 * account is not protected until a real code from the app is accepted. Turning it off also needs a
 * code, so a found phone with a live session cannot quietly remove the second factor.
 */
@Composable
fun TwoFactorScreen(navController: NavController, onBack: () -> Unit) {
    val isThai = LocalStrings.current.isThai
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository() }

    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var enabledFactorId by remember { mutableStateOf<String?>(null) }
    var enrollment by remember { mutableStateOf<TotpEnrollment?>(null) }
    var code by remember { mutableStateOf("") }
    var disarming by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        repo.totpFactor()
            .onSuccess { enabledFactorId = it?.id }
            .onFailure { error = (if (isThai) "โหลดสถานะไม่สำเร็จ: " else "Couldn't load status: ") + (it.message ?: it::class.simpleName) }
        loading = false
    }
    LaunchedEffect(Unit) { reload() }

    fun friendlyError(raw: String): String = when {
        raw.contains("invalid", true) || raw.contains("expired", true) || raw.contains("incorrect", true) ->
            if (isThai) "รหัสไม่ถูกต้องหรือหมดอายุ ดูรหัสใหม่ในแอปแล้วลองอีกครั้ง" else "Wrong or expired code. Check the app for the new one."
        raw.contains("mfa", true) && raw.contains("disabled", true) ->
            if (isThai) "โปรเจกต์ Supabase ปิดการใช้งาน 2FA อยู่" else "MFA is disabled in the Supabase project"
        else -> (if (isThai) "ไม่สำเร็จ: " else "Failed: ") + raw
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ยืนยันตัวตน 2 ขั้นตอน" else "Two-factor authentication",
                onBack = onBack,
                navController = navController,
                showProfile = false
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- status ----
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (enabledFactorId != null) OkGreen.copy(alpha = 0.08f) else Primary.copy(alpha = 0.06f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        if (enabledFactorId != null) Icons.Outlined.VerifiedUser else Icons.Outlined.PhonelinkLock,
                        null,
                        tint = if (enabledFactorId != null) OkGreen else Primary
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            when {
                                loading -> if (isThai) "กำลังตรวจสอบ…" else "Checking…"
                                enabledFactorId != null -> if (isThai) "เปิดใช้งานอยู่" else "Turned on"
                                else -> if (isThai) "ยังไม่เปิดใช้งาน" else "Not turned on"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (enabledFactorId != null) OkGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (isThai) "ทุกครั้งที่เข้าสู่ระบบด้วยรหัสผ่าน ต้องกรอกรหัส 6 หลักจากแอป Authenticator ด้วย"
                            else "Every password sign-in also needs the 6-digit code from your authenticator app.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            error?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            when {
                loading -> Unit

                // ---- on: offer to turn off (needs a code) ----
                enabledFactorId != null -> {
                    if (!disarming) {
                        OutlinedButton(
                            onClick = { disarming = true; code = ""; error = null },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text(if (isThai) "ปิดการยืนยัน 2 ขั้นตอน" else "Turn off two-factor", color = Error, fontWeight = FontWeight.Bold) }
                    } else {
                        Text(
                            if (isThai) "กรอกรหัส 6 หลักจากแอป Authenticator เพื่อยืนยันว่าเป็นคุณ" else "Enter the 6-digit code to confirm it's you",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        CodeField(code, !busy, isThai) { code = it; error = null }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { disarming = false; code = "" }, enabled = !busy) { Text(if (isThai) "ยกเลิก" else "Cancel") }
                            Button(
                                onClick = {
                                    val factor = enabledFactorId ?: return@Button
                                    busy = true
                                    error = null
                                    scope.launch {
                                        repo.disableTotp(factor, code)
                                            .onSuccess {
                                                disarming = false
                                                code = ""
                                                reload()
                                                Toast.makeText(context, if (isThai) "ปิดการยืนยัน 2 ขั้นตอนแล้ว" else "Two-factor turned off", Toast.LENGTH_SHORT).show()
                                            }
                                            .onFailure { error = friendlyError(it.message ?: it::class.simpleName.orEmpty()) }
                                        busy = false
                                    }
                                },
                                enabled = !busy && code.length == CODE_LENGTH,
                                shape = RoundedCornerShape(16.dp)
                            ) { Text(if (isThai) "ยืนยันการปิด" else "Confirm") }
                        }
                    }
                }

                // ---- off, nothing started: explain and start ----
                enrollment == null -> {
                    Text(
                        if (isThai) "ต้องมีแอป Authenticator เช่น Google Authenticator หรือ Microsoft Authenticator บนโทรศัพท์ก่อน"
                        else "You need an authenticator app such as Google Authenticator or Microsoft Authenticator.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            busy = true
                            error = null
                            scope.launch {
                                repo.startTotpEnrollment()
                                    .onSuccess { enrollment = it; code = "" }
                                    .onFailure { error = friendlyError(it.message ?: it::class.simpleName.orEmpty()) }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(if (isThai) "เริ่มตั้งค่า" else "Start setup", fontWeight = FontWeight.Bold) }
                }

                // ---- off, enrolment started: scan then confirm ----
                else -> {
                    val current = enrollment!!
                    Text(
                        if (isThai) "1. สแกน QR นี้ในแอป Authenticator" else "1. Scan this QR in your authenticator app",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    val qr = remember(current.uri) { runCatching { AccessQrHelper.encodeBitmap(current.uri, 512) }.getOrNull() }
                    if (qr != null) {
                        Surface(shape = RoundedCornerShape(18.dp), color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Image(qr.asImageBitmap(), contentDescription = null, modifier = Modifier.padding(12.dp).size(220.dp))
                        }
                    }
                    Text(
                        if (isThai) "สแกนไม่ได้? พิมพ์รหัสนี้ในแอปแทน" else "Can't scan? Type this key into the app instead",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                current.secret.chunked(4).joinToString(" "),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 15.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { copyKey(context, current.secret, isThai) }) {
                                Icon(Icons.Outlined.ContentCopy, contentDescription = if (isThai) "คัดลอก" else "Copy", tint = Primary)
                            }
                        }
                    }
                    Text(
                        if (isThai) "2. กรอกรหัส 6 หลักที่แอปแสดง" else "2. Enter the 6-digit code the app shows",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    CodeField(code, !busy, isThai) { code = it; error = null }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { enrollment = null; code = ""; error = null }, enabled = !busy) {
                            Text(if (isThai) "ยกเลิก" else "Cancel")
                        }
                        Button(
                            onClick = {
                                busy = true
                                error = null
                                scope.launch {
                                    repo.confirmTotp(current.factorId, code)
                                        .onSuccess {
                                            enrollment = null
                                            code = ""
                                            reload()
                                            Toast.makeText(context, if (isThai) "เปิดการยืนยัน 2 ขั้นตอนแล้ว" else "Two-factor is on", Toast.LENGTH_LONG).show()
                                        }
                                        .onFailure { error = friendlyError(it.message ?: it::class.simpleName.orEmpty()) }
                                    busy = false
                                }
                            },
                            enabled = !busy && code.length == CODE_LENGTH,
                            shape = RoundedCornerShape(16.dp)
                        ) { Text(if (isThai) "เปิดใช้งาน" else "Turn on", fontWeight = FontWeight.Bold) }
                    }
                    Text(
                        if (isThai) "เก็บรหัสด้านบนไว้ในที่ปลอดภัย ถ้าโทรศัพท์หาย คุณจะใช้รหัสนั้นตั้งแอป Authenticator ใหม่ได้"
                        else "Keep the key above somewhere safe: if you lose the phone, it's how you set up the authenticator again.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CodeField(code: String, enabled: Boolean, isThai: Boolean, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = code,
        onValueChange = { onChange(it.filter(Char::isDigit).take(CODE_LENGTH)) },
        label = { Text(if (isThai) "รหัส 6 หลัก" else "6-digit code") },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun copyKey(context: Context, secret: String, isThai: Boolean) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("NongRak 2FA", secret))
    Toast.makeText(context, if (isThai) "คัดลอกรหัสแล้ว" else "Key copied", Toast.LENGTH_SHORT).show()
}
