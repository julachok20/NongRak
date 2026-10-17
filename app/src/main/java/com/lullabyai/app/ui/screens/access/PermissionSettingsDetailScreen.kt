@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AccessCapabilities
import com.lullabyai.app.viewmodel.AccessPermissionViewModel
import com.lullabyai.app.viewmodel.AccessRoleLabels

/**
 * Stitch: "Permission Settings Detail (พร้อมสิทธิ์บันทึกกิจกรรมและการพบแพทย์)"
 * Header is the standard [LullabyTopBar]; everything below is the Stitch layout.
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
    var caps by remember(member?.userId, member?.capabilities) {
        mutableStateOf(member?.let { AccessCapabilities.effectiveCaps(it) } ?: emptySet())
    }

    LaunchedEffect(member) {
        if (member != null) caps = AccessCapabilities.effectiveCaps(member)
    }

    var confirmRemove by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Background,
        topBar = {
            LullabyTopBar(
                title = if (isThai) "การตั้งค่าสิทธิ์ (Permissions)" else "Permission settings",
                onBack = onBack,
                navController = navController
            )
        },
        bottomBar = { AccessPermissionBottomNav(navController) }
    ) { padding ->
        if (member == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.loading) CircularProgressIndicator(color = Primary)
                else Text(if (isThai) "ไม่พบผู้ใช้นี้" else "Member not found")
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    AccessAvatar(member.avatarUrl, member.displayName, Modifier.size(56.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            member.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = OnBackground
                        )
                        Text(
                            AccessCapabilities.roleBadge(member.role),
                            color = Primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            AccessCapabilities.SECTIONS.forEach { section ->
                PermSection(
                    icon = permissionSectionIcon(section.id),
                    titleTh = section.titleTh,
                    titleEn = section.titleEn
                ) {
                    section.items.forEach { item ->
                        PermToggle(
                            titleTh = item.titleTh,
                            titleEn = item.titleEn,
                            checked = item.key in caps,
                            onChange = { on ->
                                val next = if (on) caps + item.key else caps - item.key
                                caps = next
                                viewModel.saveCapabilitySet(userId, member.role, next)
                            }
                        )
                    }
                }
            }

            val canRemoveThisMember = AccessRoleLabels.canRemove(
                myRole = state.myRole,
                targetRole = member.role,
                isSelf = member.userId == state.myUserId
            )

            if (canRemoveThisMember) {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { confirmRemove = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Error.copy(alpha = 0.45f))
                ) {
                    Icon(Icons.Filled.PersonRemove, null, tint = Error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "ลบสิทธิ์การเข้าถึง",
                            color = Error,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "Remove this user's access",
                            color = Error.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            state.error?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (confirmRemove) {
        val targetName = member?.displayName ?: ""
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
internal fun PermSection(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    PermSection(icon = icon, titleTh = title, titleEn = null, content = content)
}

@Composable
internal fun PermSection(
    icon: ImageVector,
    titleTh: String,
    titleEn: String?,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(22.dp))
                Column {
                    Text(
                        titleTh,
                        fontWeight = FontWeight.Bold,
                        color = OnBackground,
                        style = MaterialTheme.typography.titleMedium,
                        lineHeight = 22.sp
                    )
                    if (!titleEn.isNullOrBlank()) {
                        Text(
                            "($titleEn)",
                            color = OnSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            content()
        }
    }
}

@Composable
internal fun PermToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    PermToggle(titleTh = label, titleEn = null, checked = checked, onChange = onChange)
}

@Composable
internal fun PermToggle(
    titleTh: String,
    titleEn: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                titleTh,
                style = MaterialTheme.typography.bodyMedium,
                color = OnBackground,
                fontWeight = FontWeight.Medium
            )
            if (!titleEn.isNullOrBlank()) {
                Text(
                    titleEn,
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                    fontSize = 12.sp
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Primary,
                checkedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFE4E6EB),
                uncheckedThumbColor = Color.White,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}
