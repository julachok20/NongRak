@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.ChildAccessMember
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.components.PrimaryButton
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AccessPermissionViewModel
import com.lullabyai.app.viewmodel.AccessRoleLabels

/**
 * Stitch: "Permission Settings Detail" — per-member granular toggles + role.
 */
@Composable
fun PermissionSettingsDetailScreen(
    userId: String,
    viewModel: AccessPermissionViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(userId) {
        viewModel.load()
        viewModel.selectMember(userId)
    }

    val member = state.selectedMember ?: state.members.find { it.userId == userId }
    var draft by remember(member?.userId, member?.role) { mutableStateOf(member) }

    LaunchedEffect(member) {
        if (member != null) draft = member
    }

    var confirmRemove by remember { mutableStateOf(false) }
    var showTransferConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "ตั้งค่าสิทธิ์ (Permissions)" else "Permission settings",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { AccessPermissionBottomNav(navController) }
    ) { padding ->
        if (draft == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator(color = Primary)
                else Text(if (isThai) "ไม่พบผู้ใช้นี้" else "Member not found")
            }
            return@Scaffold
        }
        val d = draft!!

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccessAvatar(d.avatarUrl, d.displayName, Modifier.size(56.dp))
                Column {
                    Text(d.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(AccessRoleLabels.title(d.role, isThai), color = Primary, style = MaterialTheme.typography.bodyMedium)
                    d.email?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text(if (isThai) "บทบาท" else "Role", fontWeight = FontWeight.Bold)
            AccessRoleLabels.ROLES.forEach { r ->
                FilterChip(
                    selected = d.role == r || (d.role == "member" && r == "co_owner"),
                    onClick = { draft = d.copy(role = r) },
                    label = { Text(AccessRoleLabels.title(r, isThai)) },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            PermSection(
                icon = Icons.Filled.HealthAndSafety,
                title = if (isThai) "ข้อมูลสุขภาพ (Health Records)" else "Health Records"
            ) {
                PermToggle(if (isThai) "ดูประวัติสุขภาพ" else "View Health History", d.canViewHealth) {
                    draft = d.copy(canViewHealth = it)
                }
                PermToggle(if (isThai) "บันทึกอาการป่วย" else "Log Symptoms", d.canLogSymptoms) {
                    draft = d.copy(canLogSymptoms = it)
                }
                PermToggle(if (isThai) "แก้ไขข้อมูลการเจริญเติบโต" else "Edit Growth Data", d.canEditGrowth) {
                    draft = d.copy(canEditGrowth = it)
                }
            }

            PermSection(
                icon = Icons.Filled.CalendarMonth,
                title = if (isThai) "การนัดหมาย (Appointments)" else "Appointments"
            ) {
                PermToggle(if (isThai) "ดูนัดหมาย" else "View Appointments", d.canViewAppointments) {
                    draft = d.copy(canViewAppointments = it)
                }
                PermToggle(if (isThai) "จองนัดคุณหมอ" else "Book Doctor", d.canBookDoctor) {
                    draft = d.copy(canBookDoctor = it)
                }
                PermToggle(if (isThai) "ยกเลิกนัดหมาย" else "Cancel Appointments", d.canCancelAppointments) {
                    draft = d.copy(canCancelAppointments = it)
                }
            }

            PermSection(
                icon = Icons.Filled.Description,
                title = if (isThai) "เอกสารและข้อมูล (Documents & Info)" else "Documents & Info"
            ) {
                PermToggle(if (isThai) "ดูคลังเอกสาร" else "View Document Vault", d.canViewDocuments) {
                    draft = d.copy(canViewDocuments = it)
                }
                PermToggle(if (isThai) "อัปโหลดเอกสารใหม่" else "Upload Documents", d.canUploadDocuments) {
                    draft = d.copy(canUploadDocuments = it)
                }
                PermToggle(if (isThai) "แก้ไขโปรไฟล์เด็ก" else "Edit Child Profile", d.canEditProfile) {
                    draft = d.copy(canEditProfile = it)
                }
            }

            PrimaryButton(
                text = if (state.savedOk) (if (isThai) "บันทึกแล้ว" else "Saved")
                else (if (isThai) "บันทึกสิทธิ์" else "Save permissions"),
                onClick = { viewModel.savePermissions(d) },
                isLoading = state.saving
            )

            val isCurrentOwner = state.isOwner || state.myRole == "owner"
            val canTransferToThisMember = isCurrentOwner && d.userId != state.myUserId && d.role != "owner"

            if (canTransferToThisMember) {
                OutlinedButton(
                    onClick = { showTransferConfirm = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE65100)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE65100).copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Filled.SwapHoriz, null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isThai) "โอนสิทธิ์เจ้าของหลักให้ผู้ใช้นี้" else "Transfer primary ownership",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            val canRemoveThisMember = AccessRoleLabels.canRemove(
                myRole = state.myRole,
                targetRole = d.role,
                isSelf = d.userId == state.myUserId
            )

            if (canRemoveThisMember) {
                TextButton(
                    onClick = { confirmRemove = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Delete, null, tint = Error)
                    Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "ถอดสิทธิ์ผู้ใช้นี้" else "Remove access", color = Error)
                }
            }

            state.error?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showTransferConfirm) {
        val targetName = draft?.displayName ?: ""
        AlertDialog(
            onDismissRequest = { if (!state.transferring) showTransferConfirm = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.SwapHoriz, null, tint = Primary)
                    Text(
                        if (isThai) "โอนสิทธิ์ความเป็นเจ้าของ" else "Transfer Ownership",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    if (isThai)
                        "คุณแน่ใจหรือไม่ว่าต้องการโอนสิทธิ์ความเป็นเจ้าของของ \"${state.childName.ifBlank { "ลูก" }}\" ให้กับ \"$targetName\"?\n\nเมื่อโอนแล้ว คุณจะถูกเปลี่ยนบทบาทเป็น \"ผู้ปกครองร่วม (Co-owner)\" และผู้ใช้ใหม่จะได้รับสิทธิ์เจ้าของหลักเต็มรูปแบบ"
                    else
                        "Are you sure you want to transfer ownership of \"${state.childName.ifBlank { "your child" }}\" to \"$targetName\"?\n\nYou will become a Co-owner and they will receive full primary owner authority."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.transferOwnership(userId) { ok ->
                            if (ok) {
                                showTransferConfirm = false
                                onBack()
                            }
                        }
                    },
                    enabled = !state.transferring
                ) {
                    if (state.transferring) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                    } else {
                        Text(
                            if (isThai) "ยืนยันโอนสิทธิ์" else "Transfer",
                            color = Primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransferConfirm = false }, enabled = !state.transferring) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }

    if (confirmRemove) {
        val targetName = draft?.displayName ?: ""
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(if (isThai) "ถอดสิทธิ์ $targetName?" else "Remove $targetName?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (isThai) "ผู้ใช้นี้จะเข้าถึงข้อมูลของ \"${state.childName.ifBlank { "ลูก" }}\" ไม่ได้ทันที"
                    else "This person will lose access to \"${state.childName.ifBlank { "your child" }}\" immediately"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    viewModel.removeMember(userId)
                    onBack()
                }) { Text(if (isThai) "ถอดสิทธิ์" else "Remove", color = Error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }
}

@Composable
internal fun PermSection(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, null, tint = Primary)
                Text(title, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
internal fun PermToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Primary)
        )
    }
}
