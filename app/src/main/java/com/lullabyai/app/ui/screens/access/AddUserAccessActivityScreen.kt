@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import android.content.Intent
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AccessPermissionViewModel
import com.lullabyai.app.viewmodel.AccessRoleLabels

/**
 * Stitch: "Add User & Access Activity" — invite form (role + name/email) + activity feed.
 */
@Composable
fun AddUserAccessActivityScreen(
    viewModel: AccessPermissionViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf("co_owner") }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(state.inviteCreated, state.invite) {
        if (state.inviteCreated && state.invite != null) {
            val inv = state.invite!!
            val shareText = if (isThai) {
                "เชิญเข้าถึงข้อมูลลูกใน NongRak AI\nรหัส: ${inv.code}\nบทบาท: ${AccessRoleLabels.title(inv.role, true)}\nหมดอายุ: ${inv.expiresAt}"
            } else {
                "Join this child's data on NongRak AI\nCode: ${inv.code}\nRole: ${AccessRoleLabels.title(inv.role, false)}\nExpires: ${inv.expiresAt}"
            }
            context.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    },
                    if (isThai) "แชร์รหัสเชิญ" else "Share invite"
                )
            )
            viewModel.clearInviteFlag()
        }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "เพิ่มบุคคล & ประวัติ" else "Add User & Activity",
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
                if (isThai) "จัดการสิทธิ์เข้าถึง" else "Manage access",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Primary
            )
            Text(
                if (isThai) "เชิญผู้ปกครองหรือผู้ดูแลด้วยชื่อ อีเมล และบทบาท — ระบบจะสร้างรหัสเชิญ 7 วัน"
                else "Invite a co-parent or caretaker with name, email, and role — creates a 7-day invite code",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(if (isThai) "ชื่อผู้ถูกเชิญ" else "Invitee name") },
                leadingIcon = { Icon(Icons.Filled.Person, null) }
            )
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(if (isThai) "อีเมล (ถ้ามี)" else "Email (optional)") },
                leadingIcon = { Icon(Icons.Filled.Email, null) }
            )

            Text(
                if (isThai) "เลือกบทบาท" else "Select role",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            AccessRoleLabels.ROLES.forEach { r ->
                val selected = role == r
                Card(
                    onClick = { role = r },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) Primary.copy(0.12f) else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected, onClick = { role = r }, colors = RadioButtonDefaults.colors(selectedColor = Primary))
                        Column(Modifier.weight(1f)) {
                            Text(AccessRoleLabels.title(r, isThai), fontWeight = FontWeight.Bold)
                            Text(
                                AccessRoleLabels.subtitle(r, isThai),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            PrimaryButton(
                text = if (isThai) "ส่งคำเชิญ" else "Send invite",
                onClick = {
                    viewModel.createInvite(
                        role = role,
                        inviteType = "code",
                        inviteeName = name.ifBlank { null },
                        inviteeEmail = email.ifBlank { null }
                    )
                },
                isLoading = state.saving,
                enabled = !state.saving
            )

            state.error?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.History, null, tint = Primary)
                Text(
                    if (isThai) "ประวัติการขอเข้าถึง" else "Access activity",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (state.activity.isEmpty() && !state.loading) {
                Text(
                    if (isThai) "ยังไม่มีประวัติ" else "No activity yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            state.activity.forEach { event ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
                ) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            when (event.eventType) {
                                "access_approved" -> Icons.Filled.CheckCircle
                                "access_rejected", "member_removed" -> Icons.Filled.Cancel
                                "qr_generated" -> Icons.Filled.QrCode2
                                "access_requested" -> Icons.Filled.Timer
                                else -> Icons.Filled.Info
                            },
                            null,
                            tint = Primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(AccessRoleLabels.activityLabel(event.eventType, isThai), fontWeight = FontWeight.Bold)
                            val roleBit = event.role?.let { AccessRoleLabels.title(it, isThai) }
                            Text(
                                listOfNotNull(event.detail, roleBit).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatAccessTime(event.createdAt, isThai),
                                style = MaterialTheme.typography.labelSmall,
                                color = Outline
                            )
                        }
                    }
                }
            }
        }
    }
}
