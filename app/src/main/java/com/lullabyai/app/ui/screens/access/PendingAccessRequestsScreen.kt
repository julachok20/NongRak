@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.ChildSyncRequest
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AccessRequestStrings
import com.lullabyai.app.viewmodel.AccessRequestViewModel
import com.lullabyai.app.viewmodel.AccessRoleLabels

/**
 * The owner's approve/deny popup. Raised instantly by the Realtime subscription when someone scans a
 * share QR while this app is open, and also openable from the pending list for requests that landed
 * while the app was closed.
 *
 * Dismissing is safe: the request stays `pending` and remains in the list until explicitly resolved.
 */
@Composable
fun PendingAccessRequestDialog(
    request: ChildSyncRequest,
    childName: String,
    isThai: Boolean,
    busy: Boolean,
    onApprove: (String) -> Unit,
    onDeny: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRole by remember(request.id, request.requestedRole) {
        mutableStateOf(if (request.requestedRole in AccessRoleLabels.ROLES) request.requestedRole else "co_owner")
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Primary.copy(0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (request.method == "qr") Icons.Filled.QrCodeScanner else Icons.Filled.SyncAlt,
                    null,
                    tint = Primary,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                if (isThai) "มีคำขอเข้าถึงข้อมูลลูก" else "Access request",
                fontWeight = FontWeight.Bold,
                color = Primary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    requestSummary(request, childName, isThai),
                    style = MaterialTheme.typography.bodyMedium
                )
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        RequestDetailRow(
                            Icons.Filled.ChildCare,
                            if (isThai) "ลูก" else "Child",
                            childName.ifBlank { if (isThai) "ไม่ระบุ" else "Unknown" }
                        )
                        RequestDetailRow(
                            Icons.Filled.Login,
                            if (isThai) "ช่องทาง" else "Via",
                            methodLabel(request.method, isThai)
                        )
                        formatAccessTime(request.createdAt, isThai).takeIf { it.isNotBlank() }?.let {
                            RequestDetailRow(Icons.Filled.Schedule, if (isThai) "เวลา" else "When", it)
                        }
                    }
                }

                Text(
                    if (isThai) "กำหนดบทบาทที่จะมอบให้:" else "Assign role to grant:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AccessRoleLabels.ROLES.forEach { r ->
                        FilterChip(
                            selected = selectedRole == r,
                            onClick = { selectedRole = r },
                            label = { Text(AccessRoleLabels.title(r, isThai), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text(
                    AccessRoleLabels.subtitle(selectedRole, isThai),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    if (isThai)
                        "หากคุณไม่รู้จักผู้ขอ ให้กด \"ปฏิเสธ\" — การอนุมัติจะให้สิทธิ์เข้าถึงข้อมูลสุขภาพของลูกทันที"
                    else
                        "If you don't recognise the requester, tap Deny — approving grants access to your child's health data immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Error
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onApprove(selectedRole) },
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = OnPrimary, strokeWidth = 2.dp)
                } else {
                    Text(if (isThai) "อนุมัติ" else "Approve")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDeny, enabled = !busy) {
                Text(if (isThai) "ปฏิเสธ" else "Deny", color = Error)
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

/**
 * The list an owner sees on next launch for requests that arrived while the app was closed — the
 * offline half of the "no FCM" design. Each row opens the same approve/deny popup.
 */
@Composable
fun PendingAccessRequestsScreen(
    viewModel: AccessRequestViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.startWatching() }

    state.popup?.let { req ->
        PendingAccessRequestDialog(
            request = req,
            childName = state.nameFor(req.childId),
            isThai = isThai,
            busy = state.resolving,
            onApprove = { role -> viewModel.resolve(req.id, true, role) },
            onDeny = { viewModel.resolve(req.id, false) },
            onDismiss = { viewModel.dismissPopup() }
        )
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "คำขอเข้าถึงข้อมูล" else "Access requests",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { AccessPermissionBottomNav(navController) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                if (isThai)
                    "คำขอที่รอการอนุมัติของคุณ ระบบจะไม่ให้สิทธิ์เข้าถึงใดๆ จนกว่าคุณจะกดอนุมัติ"
                else
                    "Requests waiting on you. No access is granted until you approve.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            AccessRequestStrings.error(state.error, isThai)?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }

            if (state.pending.isEmpty()) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = HealthGood, modifier = Modifier.size(48.dp))
                    Text(
                        if (isThai) "ไม่มีคำขอที่รออนุมัติ" else "No requests waiting",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            state.pending.forEach { req ->
                val childName = state.nameFor(req.childId)
                Card(
                    onClick = { viewModel.showPopup(req) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AccessAvatar(url = null, name = req.requesterLabel ?: "?")
                            Column(Modifier.weight(1f)) {
                                Text(
                                    requestSummary(req, childName, isThai),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    listOf(
                                        AccessRoleLabels.title(req.requestedRole, isThai),
                                        methodLabel(req.method, isThai),
                                        formatAccessTime(req.createdAt, isThai)
                                    ).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.resolve(req.id, false) },
                                modifier = Modifier.weight(1f),
                                enabled = !state.resolving
                            ) { Text(if (isThai) "ปฏิเสธ" else "Deny", color = Error) }
                            Button(
                                onClick = { viewModel.resolve(req.id, true) },
                                modifier = Modifier.weight(1f),
                                enabled = !state.resolving,
                                colors = ButtonDefaults.buttonColors(containerColor = Primary)
                            ) { Text(if (isThai) "อนุมัติ" else "Approve") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestDetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Outline, modifier = Modifier.size(16.dp))
        Text("$label:", style = MaterialTheme.typography.labelSmall, color = Outline)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

/** "<requester> ขอเข้าถึงข้อมูลของ <child>" — never includes user ids. */
private fun requestSummary(request: ChildSyncRequest, childName: String, isThai: Boolean): String {
    val who = request.requesterLabel?.takeIf { it.isNotBlank() }
        ?: if (isThai) "ผู้ใช้รายหนึ่ง" else "Someone"
    val child = childName.ifBlank { if (isThai) "ลูกของคุณ" else "your child" }
    return if (isThai) "$who ขอเข้าถึงข้อมูลของ $child" else "$who is requesting access to $child"
}

private fun methodLabel(method: String, isThai: Boolean): String = when (method) {
    "qr" -> if (isThai) "สแกน QR Code" else "QR scan"
    "code" -> if (isThai) "รหัสเชิญ" else "Invite code"
    "search" -> if (isThai) "ค้นหาด้วยชื่อ" else "Name search"
    else -> method
}
