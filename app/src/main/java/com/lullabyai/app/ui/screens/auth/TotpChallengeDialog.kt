package com.lullabyai.app.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

private const val CODE_LENGTH = 6

/**
 * The second step of a sign-in when the account has an authenticator app.
 *
 * The password already produced a session, but only an aal1 one; the code raises it to aal2.
 * Cancelling signs out rather than continuing half-authenticated — a half-finished 2FA sign-in that
 * still reached the app would make the setting a decoration.
 */
@Composable
fun TotpChallengeDialog(isThai: Boolean, onVerified: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository() }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {},
        title = { Text(if (isThai) "ยืนยันตัวตน 2 ขั้นตอน" else "Two-factor authentication", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isThai) "เปิดแอป Authenticator แล้วกรอกรหัส 6 หลักของ NongRak AI"
                    else "Open your authenticator app and enter the 6-digit NongRak AI code."
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.filter(Char::isDigit).take(CODE_LENGTH); error = null },
                    label = { Text(if (isThai) "รหัส 6 หลัก" else "6-digit code") },
                    singleLine = true,
                    enabled = !busy,
                    isError = error != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && code.length == CODE_LENGTH,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        repo.verifyTotpChallenge(code)
                            .onSuccess { onVerified() }
                            .onFailure {
                                val raw = it.message.orEmpty()
                                error = if (raw.contains("invalid", true) || raw.contains("expired", true)) {
                                    if (isThai) "รหัสไม่ถูกต้องหรือหมดอายุ ลองรหัสใหม่ในแอป" else "Wrong or expired code. Try the next one."
                                } else {
                                    (if (isThai) "ยืนยันไม่สำเร็จ: " else "Could not verify: ") + (raw.ifBlank { it::class.simpleName.orEmpty() })
                                }
                                code = ""
                            }
                        busy = false
                    }
                }
            ) { Text(if (isThai) "ยืนยัน" else "Verify", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onCancel) { Text(if (isThai) "ยกเลิก" else "Cancel") }
        }
    )
}
