package com.lullabyai.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhonelinkLock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.data.remote.supabase
import com.lullabyai.app.data.repository.AuthRepository
import com.lullabyai.app.data.repository.DeviceRepository
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.ui.theme.PrimaryContainer
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private sealed interface GateState {
    data object Clear : GateState
    data object Pending : GateState
}

/**
 * 2FA & Device Trust Gate:
 *
 * 1. Default Behavior (2FA OFF):
 *    Users can sign in directly on any device without requiring confirmation on the owner's phone.
 *    The device is registered in the background without gating the user.
 *
 * 2. When 2FA is Turned ON in Settings:
 *    Signing into a new, unrecognized device requires verification/approval from an existing
 *    signed-in trusted device (via Settings > Trusted Devices).
 */
@Composable
fun DeviceTrustGate(settingsManager: SettingsManager, content: @Composable () -> Unit) {
    val sessionStatus by supabase.auth.sessionStatus.collectAsState()
    val userId = (sessionStatus as? SessionStatus.Authenticated)?.session?.user?.id

    var state by remember { mutableStateOf<GateState>(GateState.Clear) }
    var currentDeviceName by remember { mutableStateOf("") }

    LaunchedEffect(userId) {
        if (userId == null) {
            // No session: nothing to gate. Welcome/Login must render.
            state = GateState.Clear
            return@LaunchedEffect
        }
        val id = settingsManager.getOrCreateDeviceId()
        val name = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        currentDeviceName = name

        // The gate is inert until the server side of 2FA exists.
        //
        // It used to branch on profiles.two_factor_enabled and call register_device - an RPC no
        // migration defines. The call failed, runCatching turned the failure into null, and
        // `null != "pending"` resolved to Clear. So the gate already admitted every unapproved
        // device; it simply did so while appearing to guard.
        //
        // Deliberately NOT switched to fail-closed. Anyone whose profile still carries
        // two_factor_enabled = true from the old toggle would be locked out of their own account
        // permanently, because there is no working approval path to let them back in. Blocking
        // real parents to enforce a check that does not exist is the worse mistake.
        //
        // When the migration lands (trusted_devices, the six security definer RPCs, and RLS keyed
        // to device status), restore the branch here AND make an unknown status mean blocked.
        runCatching { DeviceRepository.register(id, name) }
        state = GateState.Clear
    }

    when {
        state is GateState.Pending -> PendingApprovalScreen(
            deviceName = currentDeviceName,
            onCheckAgain = {
                val id = settingsManager.getOrCreateDeviceId()
                if (DeviceRepository.myStatus(id).getOrNull() == "approved") {
                    state = GateState.Clear
                }
            }
        )
        else -> content()
    }
}

@Composable
private fun PendingApprovalScreen(
    deviceName: String,
    onCheckAgain: suspend () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(Modifier.height(20.dp))

            Surface(
                shape = RoundedCornerShape(100.dp),
                color = Primary.copy(alpha = 0.12f)
            ) {
                Text(
                    text = if (isThai) "การยืนยันตัวตน 2 ขั้นตอน (2FA)" else "Two-Factor Authentication (2FA)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                if (isThai) "ยืนยันการเข้าสู่ระบบบนเครื่องเดิม" else "Approve Login on Current Device",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(10.dp))

            Text(
                if (isThai)
                    "บัญชีนี้เปิดใช้งาน 2FA ไว้ เพื่อความปลอดภัย กรุณาเปิดแอปบนอุปกรณ์ที่คุณลงชื่อเข้าใช้อยู่แล้ว แล้วกดอนุญาตการเข้าสู่ระบบสำหรับอุปกรณ์นี้"
                else
                    "2FA is enabled on this account. For security, please open the app on an already signed-in device and approve this sign-in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.PhonelinkLock,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            if (isThai) "คำขอเข้าสู่ระบบจากเครื่องนี้:" else "Sign-in request from this device:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (deviceName.isNotBlank()) deviceName else "Android Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isThai)
                            "คำแนะนำ: ไปที่ การตั้งค่า > อุปกรณ์ที่เชื่อมต่อ ในเครื่องเดิมของคุณ แล้วกด \"อนุญาต\""
                        else
                            "Instruction: Go to Settings > Trusted Devices on your existing phone and tap \"Approve\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            CircularProgressIndicator(
                color = Primary,
                modifier = Modifier.size(32.dp),
                strokeWidth = 3.dp
            )

            Spacer(Modifier.height(10.dp))

            Text(
                if (isThai) "กำลังรอการยืนยันจากเครื่องเดิม..." else "Waiting for approval from your active device...",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            checking = true
                            onCheckAgain()
                            delay(500)
                            checking = false
                        }
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(100.dp),
                    enabled = !checking
                ) {
                    Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isThai) "ตรวจสอบอีกครั้ง" else "Check Again")
                }

                Button(
                    onClick = { scope.launch { AuthRepository().signOut() } },
                    modifier = Modifier.weight(1f).height(46.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isThai) "ออกจากระบบ" else "Sign out")
                }
            }
        }
    }
}
