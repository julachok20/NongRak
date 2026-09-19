package com.lullabyai.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.AuthRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val RESEND_COOLDOWN_S = 60

/**
 * Shown after sign-up when Supabase "Confirm email" is on. Finishes by itself when the link is opened
 * on this phone (MainActivity imports the session from lullabyai://auth); "ยืนยันแล้ว" covers a link
 * opened on another device by signing in with the password the parent just typed.
 */
@Composable
fun EmailConfirmationDialog(
    email: String,
    password: String,
    isThai: Boolean,
    onConfirmed: () -> Unit,
    onCancel: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository() }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var cooldown by remember { mutableIntStateOf(RESEND_COOLDOWN_S) }
    var finished by remember { mutableStateOf(false) }

    fun finish() {
        if (finished) return
        finished = true
        scope.launch {
            repo.finishConfirmedSignUp()
            onConfirmed()
        }
    }

    LaunchedEffect(Unit) {
        supabase.auth.sessionStatus.collect { if (it is SessionStatus.Authenticated) finish() }
    }
    LaunchedEffect(cooldown) {
        if (cooldown > 0) {
            delay(1_000)
            cooldown--
        }
    }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (isThai) "ยืนยันอีเมลของคุณ" else "Confirm your email", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isThai) "เราส่งลิงก์ยืนยันไปที่ $email แล้ว เปิดอีเมลบนโทรศัพท์เครื่องนี้แล้วกดลิงก์ แอปจะเข้าสู่ระบบให้เอง\n\nไม่เห็นอีเมล? ลองดูในโฟลเดอร์สแปม"
                    else "We sent a confirmation link to $email. Open it on this phone and the app signs you in.\n\nNo email? Check your spam folder."
                )
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                TextButton(
                    enabled = !busy && cooldown == 0,
                    onClick = {
                        busy = true
                        scope.launch {
                            repo.resendSignUpConfirmation(email)
                                .onSuccess {
                                    message = if (isThai) "ส่งลิงก์ใหม่แล้ว" else "New link sent"
                                    cooldown = RESEND_COOLDOWN_S
                                }
                                .onFailure {
                                    message = (if (isThai) "ส่งไม่สำเร็จ: " else "Could not send: ") + (it.message ?: it::class.simpleName)
                                }
                            busy = false
                        }
                    }
                ) {
                    Text(
                        if (cooldown > 0) (if (isThai) "ส่งลิงก์อีกครั้ง ($cooldown)" else "Resend link ($cooldown)")
                        else (if (isThai) "ส่งลิงก์อีกครั้ง" else "Resend link")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    message = null
                    scope.launch {
                        repo.signIn(email, password)
                            .onSuccess { finish() }
                            .onFailure {
                                message = if (repo.isEmailNotConfirmed(it)) {
                                    if (isThai) "ยังไม่ได้ยืนยัน กรุณากดลิงก์ในอีเมลก่อน" else "Not confirmed yet. Open the link in the email first."
                                } else {
                                    (if (isThai) "เข้าสู่ระบบไม่สำเร็จ: " else "Could not sign in: ") + (it.message ?: it::class.simpleName)
                                }
                            }
                        busy = false
                    }
                }
            ) { Text(if (isThai) "ยืนยันแล้ว เข้าสู่ระบบ" else "I confirmed, sign in", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text(if (isThai) "ปิด" else "Close") } }
    )
}
