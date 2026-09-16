package com.lullabyai.app.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.formatRelativeTime
import com.lullabyai.app.data.repository.AuthRepository
import com.lullabyai.app.data.repository.AuthSessionRow
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.Error
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.util.DeviceLabel
import kotlinx.coroutines.launch

private val CurrentGreen = Color(0xFF12805C)

/**
 * ประวัติการเข้าสู่ระบบและอุปกรณ์ (Active Sessions): every device signed into this account, from
 * auth.sessions via list_my_sessions(). Other devices can be signed out; the phone in hand uses
 * ออกจากระบบ instead.
 */
@Composable
fun ActiveSessionsScreen(navController: NavController, onBack: () -> Unit) {
    val isThai = LocalStrings.current.isThai
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { AuthRepository() }

    var sessions by remember { mutableStateOf<List<AuthSessionRow>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var confirmOne by remember { mutableStateOf<AuthSessionRow?>(null) }
    var confirmAll by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            error = null
            repo.listSessions()
                .onSuccess { sessions = it }
                .onFailure { error = it.message ?: it::class.simpleName }
        }
    }
    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "อุปกรณ์ที่เข้าสู่ระบบ" else "Active sessions",
                onBack = onBack,
                navController = navController,
                showProfile = false
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(16.dp), color = Primary.copy(alpha = 0.06f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Shield, null, tint = Primary)
                        Text(
                            if (isThai) "อุปกรณ์ทั้งหมดที่กำลังเข้าสู่ระบบบัญชีนี้ หากเห็นอุปกรณ์ที่ไม่รู้จัก ให้ออกจากระบบอุปกรณ์นั้น แล้วเปลี่ยนรหัสผ่านทันที"
                            else "Every device signed into this account. If you see one you don't recognise, sign it out and change your password.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            error?.let { err ->
                item {
                    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                (if (isThai) "โหลดรายการไม่สำเร็จ: " else "Couldn't load sessions: ") + err,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            OutlinedButton(onClick = { load() }, shape = RoundedCornerShape(100.dp)) {
                                Text(if (isThai) "ลองอีกครั้ง" else "Try again")
                            }
                        }
                    }
                }
            }

            val list = sessions
            if (list == null && error == null) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
            if (list != null) {
                items(list, key = { it.id }) { row ->
                    SessionCard(row, isThai, busy) { confirmOne = row }
                }
                if (list.any { !it.isCurrent }) {
                    item {
                        OutlinedButton(
                            onClick = { confirmAll = true },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Error.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Outlined.PhonelinkErase, null, tint = Error)
                            Spacer(Modifier.width(8.dp))
                            Text(if (isThai) "ออกจากระบบอุปกรณ์อื่นทั้งหมด" else "Sign out all other devices", color = Error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item {
                    Text(
                        if (isThai) "อุปกรณ์ที่ถูกออกจากระบบจะหลุดภายในไม่เกิน 1 ชั่วโมง"
                        else "A signed-out device loses access within an hour.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    confirmOne?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmOne = null },
            title = { Text(if (isThai) "ออกจากระบบอุปกรณ์นี้?" else "Sign this device out?", fontWeight = FontWeight.Bold) },
            text = { Text(deviceName(target, isThai)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmOne = null
                    busy = true
                    scope.launch {
                        repo.revokeSession(target.id)
                            .onSuccess { removed ->
                                Toast.makeText(
                                    context,
                                    if (removed) (if (isThai) "ออกจากระบบอุปกรณ์แล้ว" else "Device signed out")
                                    else (if (isThai) "อุปกรณ์นี้ออกจากระบบไปแล้ว" else "That device was already signed out"),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .onFailure { Toast.makeText(context, it.message ?: "revoke_failed", Toast.LENGTH_LONG).show() }
                        busy = false
                        load()
                    }
                }) { Text(if (isThai) "ออกจากระบบ" else "Sign out", color = Error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmOne = null }) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
        )
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(if (isThai) "ออกจากระบบอุปกรณ์อื่นทั้งหมด?" else "Sign out all other devices?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isThai) "ทุกอุปกรณ์ยกเว้นเครื่องนี้จะต้องเข้าสู่ระบบใหม่ รวมถึงการเข้าสู่ระบบด้วยลายนิ้วมือบนเครื่องอื่น"
                    else "Every device except this one will need to sign in again, including fingerprint sign-in on other phones."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    busy = true
                    scope.launch {
                        repo.revokeOtherSessions()
                            .onSuccess { n ->
                                Toast.makeText(
                                    context,
                                    if (isThai) "ออกจากระบบ $n อุปกรณ์แล้ว" else "Signed out $n device(s)",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            .onFailure { Toast.makeText(context, it.message ?: "revoke_failed", Toast.LENGTH_LONG).show() }
                        busy = false
                        load()
                    }
                }) { Text(if (isThai) "ออกจากระบบทั้งหมด" else "Sign out all", color = Error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmAll = false }) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
        )
    }
}

private fun deviceName(row: AuthSessionRow, isThai: Boolean): String =
    DeviceLabel.parse(row.userAgent)
        ?: when {
            row.userAgent == DeviceLabel.LEGACY_AGENT -> if (isThai) "อุปกรณ์ Android (ก่อนอัปเดตแอป)" else "Android device (older app version)"
            !row.userAgent.isNullOrBlank() -> row.userAgent.take(48)
            else -> if (isThai) "อุปกรณ์ที่ไม่ทราบชื่อ" else "Unknown device"
        }

private fun epochMillis(iso: String?): Long? =
    iso?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }

@Composable
private fun SessionCard(row: AuthSessionRow, isThai: Boolean, busy: Boolean, onSignOut: () -> Unit) {
    val lastActive = epochMillis(row.lastActiveAt)
    val signedIn = epochMillis(row.createdAt)
    val dateFormat = remember(isThai) {
        java.text.SimpleDateFormat("d MMM yyyy HH:mm", if (isThai) java.util.Locale("th", "TH") else java.util.Locale.ENGLISH)
    }
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        border = if (row.isCurrent) androidx.compose.foundation.BorderStroke(1.dp, CurrentGreen.copy(alpha = 0.4f)) else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (row.isCurrent) CurrentGreen.copy(alpha = 0.10f) else Primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.PhoneAndroid, null, tint = if (row.isCurrent) CurrentGreen else Primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(deviceName(row, isThai), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    if (row.isCurrent) {
                        Surface(shape = RoundedCornerShape(100.dp), color = CurrentGreen.copy(alpha = 0.12f)) {
                            Text(
                                if (isThai) "อุปกรณ์นี้" else "This device",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = CurrentGreen,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                Text(
                    (if (isThai) "ใช้งานล่าสุด " else "Last active ") + (lastActive?.let { formatRelativeTime(it, isThai) } ?: "-"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    (if (isThai) "เข้าสู่ระบบเมื่อ " else "Signed in ") + (signedIn?.let { dateFormat.format(java.util.Date(it)) } ?: "-") +
                        (row.ip?.let { " • IP $it" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!row.isCurrent) {
                TextButton(onClick = onSignOut, enabled = !busy) {
                    Text(if (isThai) "ออกจากระบบ" else "Sign out", color = Error, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
