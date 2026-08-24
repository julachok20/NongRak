package com.lullabyai.app.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lullabyai.app.data.repository.DeviceRepository
import com.lullabyai.app.data.repository.TrustedDevice
import com.lullabyai.app.data.settings.SettingsManager
import com.lullabyai.app.ui.components.LullabyTopBar
import kotlinx.coroutines.launch

/**
 * Where a parent approves a new phone, and removes one they no longer use.
 *
 * Without this screen the trust gate is a dead end: a second device would sit pending forever
 * with nowhere to grant it. Approval is only offered from a device that is itself approved — the
 * server enforces the same rule, so a pending device opening this screen can look but not act.
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
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmRevoke by remember { mutableStateOf<TrustedDevice?>(null) }

    suspend fun refresh() {
        devices = DeviceRepository.list().getOrDefault(emptyList())
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
                        "อุปกรณ์นี้จะต้องขออนุญาตใหม่ก่อนเข้าใช้งานได้อีกครั้ง"
                    else
                        "This device will have to be approved again before it can be used."
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
                        color = MaterialTheme.colorScheme.error
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
                title = if (isThai) "อุปกรณ์ที่เชื่อมต่อ" else "Trusted Devices",
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
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    if (isThai)
                        "อุปกรณ์ใหม่ที่เข้าสู่ระบบบัญชีนี้ต้องได้รับอนุญาตจากอุปกรณ์ที่เชื่อมต่ออยู่แล้ว"
                    else
                        "A new device signing into this account must be approved from a device you already use.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            message?.let {
                item {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            items(devices, key = { it.id }) { device ->
                val isThisDevice = device.deviceId == myDeviceId
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            (device.deviceName ?: (if (isThai) "อุปกรณ์" else "Device")) +
                                if (isThisDevice) (if (isThai) " (เครื่องนี้)" else " (this device)") else "",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            statusLabel(device.status, isThai),
                            style = MaterialTheme.typography.bodySmall,
                            color = when (device.status) {
                                "approved" -> MaterialTheme.colorScheme.primary
                                "pending" -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            (if (isThai) "ใช้ล่าสุด " else "Last seen ") +
                                device.lastSeenAt.take(16).replace("T", " "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (device.status == "pending" && thisDeviceApproved && !isThisDevice) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    enabled = !busy,
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            myDeviceId?.let { DeviceRepository.approve(device.id, it) }
                                            refresh()
                                            busy = false
                                        }
                                    }
                                ) { Text(if (isThai) "อนุญาต" else "Approve") }
                                TextButton(
                                    enabled = !busy,
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            myDeviceId?.let { DeviceRepository.reject(device.id, it) }
                                            refresh()
                                            busy = false
                                        }
                                    }
                                ) {
                                    Text(
                                        if (isThai) "ปฏิเสธ" else "Reject",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        // Revoking the device in your hand would strand the account with no
                        // trusted device left to approve a replacement, so it is not offered
                        // here and the server refuses it as well.
                        if (device.status == "approved" && !isThisDevice && thisDeviceApproved) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                enabled = !busy,
                                onClick = { confirmRevoke = device }
                            ) {
                                Text(
                                    if (isThai) "ยกเลิกอุปกรณ์นี้" else "Remove this device",
                                    color = MaterialTheme.colorScheme.error
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
    "approved" -> if (isThai) "อนุญาตแล้ว" else "Approved"
    "pending" -> if (isThai) "รอการอนุญาต" else "Waiting for approval"
    "rejected" -> if (isThai) "ถูกปฏิเสธ" else "Rejected"
    else -> if (isThai) "ถูกยกเลิก" else "Revoked"
}
