package com.lullabyai.app.ui.screens.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lullabyai.app.data.repository.AuthRepository
import com.lullabyai.app.data.repository.DeviceRepository
import com.lullabyai.app.data.repository.TrustedDevice
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.theme.Primary
import com.lullabyai.app.ui.theme.PrimaryContainer
import kotlinx.coroutines.launch

/**
 * Trusted Devices & 2FA Management:
 *
 * Displays all active and pending devices linked to the user account.
 * When 2FA is turned on, new device sign-ins generate a pending request
 * that can be approved or rejected directly from this screen.
 */
@Composable
fun TrustedDevicesScreen(
    navController: androidx.navigation.NavController,
    onBack: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf<List<TrustedDevice>>(emptyList()) }
    var myDeviceId by remember { mutableStateOf<String?>(null) }
    var isTwoFactorEnabled by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmRevoke by remember { mutableStateOf<TrustedDevice?>(null) }

    suspend fun refresh() {
        val prof = AuthRepository().getProfile().getOrNull()
        isTwoFactorEnabled = prof?.twoFactorEnabled == true
        // `list_devices` is not defined in any migration yet, so this call fails. It used to fail
        // into an empty list, which rendered as a calm "no other devices" - the most reassuring
        // possible way to say "this feature does not work". Say what actually happened instead.
        DeviceRepository.list()
            .onSuccess { devices = it }
            .onFailure {
                devices = emptyList()
                message = "ระบบอุปกรณ์ที่เชื่อถือได้ยังไม่พร้อมใช้งาน (${it.message ?: "ไม่ทราบสาเหตุ"})"
            }
        loading = false
    }

    LaunchedEffect(Unit) {
        myDeviceId = SettingsManager(context).getOrCreateDeviceId()
        refresh()
    }

    val thisDeviceApproved = devices.any { it.deviceId == myDeviceId && it.status == "approved" }

    confirmRevoke?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmRevoke = null },
            title = { Text(if (isThai) "ยกเลิกอุปกรณ์นี้?" else "Remove this device?") },
            text = {
                Text(
                    if (isThai)
                        "อุปกรณ์ \"${target.deviceName ?: "Device"}\" จะถูกตัดการเชื่อมต่อ และต้องขออนุญาตใหม่หากเข้าสู่ระบบอีกครั้ง"
                    else
                        "Device \"${target.deviceName ?: "Device"}\" will be disconnected and must be approved again before signing in."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val id = target.id
                        confirmRevoke = null
                        scope.launch {
                            busy = true
                            val caller = myDeviceId
                            if (caller != null) {
                                DeviceRepository.revoke(id, caller)
                                    .onSuccess { ok ->
                                        message = if (ok) {
                                            if (isThai) "ยกเลิกอุปกรณ์แล้ว" else "Device removed."
                                        } else {
                                            if (isThai)
                                                "ยกเลิกไม่ได้ ต้องทำจากอุปกรณ์อื่นที่อนุญาตแล้ว"
                                            else
                                                "Could not remove. Do this from another approved device."
                                        }
                                    }
                                    .onFailure { e ->
                                        message = (if (isThai) "ผิดพลาด: " else "Error: ") + (e.message ?: "")
                                    }
                            }
                            refresh()
                            busy = false
                        }
                    }
                ) {
                    Text(
                        if (isThai) "ยกเลิกอุปกรณ์" else "Remove",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRevoke = null }) {
                    Text(if (isThai) "ไม่ใช่" else "Keep it")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "อุปกรณ์ที่เชื่อมต่อ (2FA)" else "Trusted Devices & 2FA",
                onBack = onBack,
                navController = navController
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        if (loading) {
            Column(
                Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) { CircularProgressIndicator(color = Primary) }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                // 2FA Status Banner Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTwoFactorEnabled) PrimaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isTwoFactorEnabled)
                        androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.3f))
                    else
                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isTwoFactorEnabled) Primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isTwoFactorEnabled) Icons.Filled.VerifiedUser else Icons.Filled.Security,
                                contentDescription = null,
                                tint = if (isTwoFactorEnabled) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isTwoFactorEnabled)
                                    (if (isThai) "ระบบ 2FA เปิดใช้งานอยู่" else "2FA Protection Active")
                                else
                                    (if (isThai) "ระบบ 2FA ปิดใช้งานอยู่" else "2FA Protection Inactive"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isTwoFactorEnabled) Primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isTwoFactorEnabled)
                                    (if (isThai) "อุปกรณ์ใหม่จะต้องได้รับการยืนยันจากหน้านี้ก่อนเข้าใช้งาน" else "New devices require approval from this screen before signing in.")
                                else
                                    (if (isThai) "อุปกรณ์ใหม่สามารถเข้าสู่ระบบได้ทันทีโดยไม่ต้องกดยืนยัน (เปิดใช้งานได้ในหน้า ตั้งค่า)" else "New devices can sign in immediately without verification. (Enable in Settings)"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            message?.let {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Primary.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isThai) "รายการอุปกรณ์ทั้งหมด (${devices.size})" else "Registered Devices (${devices.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                refresh()
                                busy = false
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = Primary, modifier = Modifier.size(20.dp))
                    }
                }
            }

            items(devices, key = { it.id }) { device ->
                val isThisDevice = device.deviceId == myDeviceId
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp),
                    border = if (isThisDevice) androidx.compose.foundation.BorderStroke(1.5.dp, Primary.copy(alpha = 0.4f)) else null
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isThisDevice) Primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Smartphone,
                                    contentDescription = null,
                                    tint = if (isThisDevice) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = device.deviceName ?: (if (isThai) "อุปกรณ์ Android" else "Android Device"),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    if (isThisDevice) {
                                        Surface(
                                            shape = RoundedCornerShape(100.dp),
                                            color = Primary.copy(alpha = 0.12f)
                                        ) {
                                            Text(
                                                text = if (isThai) "เครื่องนี้" else "This device",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Primary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val statusColor = when (device.status) {
                                        "approved" -> Color(0xFF2E7D32)
                                        "pending" -> Color(0xFFE65100)
                                        else -> Color(0xFFD32F2F)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = statusColor.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = statusLabel(device.status, isThai),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp,
                                            color = statusColor,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = (if (isThai) "ใช้งานล่าสุด: " else "Last seen: ") +
                                            device.lastSeenAt.take(16).replace("T", " "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        if (device.status == "pending" && thisDeviceApproved && !isThisDevice) {
                            Spacer(Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            myDeviceId?.let { DeviceRepository.approve(device.id, it) }
                                            refresh()
                                            busy = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(100.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (isThai) "อนุญาตการเข้าใช้" else "Approve Device", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    enabled = !busy,
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            myDeviceId?.let { DeviceRepository.reject(device.id, it) }
                                            refresh()
                                            busy = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(100.dp)
                                ) {
                                    Text(
                                        if (isThai) "ปฏิเสธ" else "Reject",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        if (device.status == "approved" && !isThisDevice && thisDeviceApproved) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                enabled = !busy,
                                onClick = { confirmRevoke = device }
                            ) {
                                Text(
                                    if (isThai) "ยกเลิกอุปกรณ์นี้" else "Remove this device",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun statusLabel(status: String, isThai: Boolean): String = when (status) {
    "approved" -> if (isThai) "อนุมัติแล้ว" else "Approved"
    "pending" -> if (isThai) "รอการอนุญาต" else "Waiting for approval"
    "rejected" -> if (isThai) "ถูกปฏิเสธ" else "Rejected"
    else -> if (isThai) "ถูกยกเลิก" else "Revoked"
}
