@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.access

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AccessPermissionViewModel
import com.lullabyai.app.viewmodel.AccessRoleLabels

/**
 * Stitch: "Permission Access (การจัดการสิทธิ์เข้าถึง)".
 *
 * Who can reach this child's record, what each of them may do, and the ways to add someone new.
 * Only an owner/co-owner may change anything; everyone else reads the same list inert.
 */
@Composable
fun PermissionAccessScreen(
    viewModel: AccessPermissionViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    val iCanManage = AccessRoleLabels.isFullAccess(state.myRole)
    val isOwner = state.isOwner
    var confirmLeave by remember { mutableStateOf(false) }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = {
                Text(
                    if (isThai) "ออกจากการดูแล?" else "Leave this child?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    // Says what is lost and what is kept: the records this person wrote stay in the
                    // child's history, which is not obvious and is the thing people worry about.
                    if (isThai)
                        "คุณจะเข้าถึงข้อมูลของ${state.childName}ไม่ได้อีก " +
                            "บันทึกที่คุณเคยเพิ่มไว้จะยังอยู่ในประวัติของเด็ก " +
                            "หากต้องการกลับมาดูแลอีกครั้ง ต้องขอสิทธิ์ใหม่จากเจ้าของ"
                    else
                        "You will lose access to ${state.childName}'s data. Records you already " +
                            "wrote stay in the child's history. To return you must request access " +
                            "from the owner again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    state.myUserId?.let { viewModel.removeMember(it) }
                    onBack()
                }) {
                    Text(
                        if (isThai) "ออกจากการดูแล" else "Leave",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text(if (isThai) "ยกเลิก" else "Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "การจัดการสิทธิ์เข้าถึง" else "Permission access",
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
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Hero Header Card
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.15f))
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Primary.copy(alpha = 0.08f),
                                    Color(0xFFEDE7F6).copy(alpha = 0.45f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Primary.copy(0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.LockPerson, null,
                                tint = Primary, modifier = Modifier.size(26.dp)
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (isThai) "ศูนย์จัดการสิทธิ์การเข้าถึง" else "Access & Permissions Hub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (isThai) "ข้อมูลของ: ${state.childName.ifBlank { "ลูกน้อย" }}"
                                else "Profile: ${state.childName.ifBlank { "Child" }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Text(
                        if (isThai) "ควบคุมสิทธิ์สำหรับผู้ปกครอง ญาติ และผู้ช่วยดูแลเด็ก บันทึกการเจริญเติบโต และเวชระเบียนทั้งหมดอย่างปลอดภัย"
                        else "Safely manage and control access permissions for parents, relatives, caregivers, and medical records.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }

            // Quick Invite Actions Bar (3 equal-width Bento cards)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickAccessActionTile(
                    title = if (isThai) "เพิ่มบุคคล" else "Invite",
                    subtitle = if (isThai) "อีเมล / ชื่อผู้ใช้" else "Email / name",
                    icon = Icons.Filled.PersonAdd,
                    iconTint = Color.White,
                    iconBg = Primary,
                    isPrimary = true,
                    enabled = iCanManage,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Screen.AddUserAccessActivity.route) }
                )
                QuickAccessActionTile(
                    title = if (isThai) "QR Code" else "QR Code",
                    subtitle = if (isThai) "สแกนรับสิทธิ์" else "Nearby scan",
                    icon = Icons.Filled.QrCode2,
                    iconTint = Color(0xFF7B1FA2),
                    iconBg = Color(0xFFF3E5F5),
                    isPrimary = false,
                    enabled = iCanManage,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Screen.ShareAccessQr.route) }
                )
                QuickAccessActionTile(
                    title = if (isThai) "บทบาท" else "Roles",
                    subtitle = if (isThai) "จัดการสิทธิ์" else "Custom roles",
                    icon = Icons.Filled.Badge,
                    iconTint = Color(0xFF00897B),
                    iconBg = Color(0xFFE0F2F1),
                    isPrimary = false,
                    enabled = iCanManage,
                    modifier = Modifier.weight(1f),
                    onClick = { navController.navigate(Screen.RoleManagement.route) }
                )
            }

            // People with access header
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (isThai) "ผู้ที่มีสิทธิ์เข้าถึง" else "People with access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = Primary.copy(alpha = 0.10f)
                ) {
                    Text(
                        if (isThai) "${state.members.size} คน" else "${state.members.size} members",
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                state.members.forEach { m ->
                    val permissionsLocked = AccessRoleLabels.isFullAccess(m.role)
                    val (badge, isFull, canLog) = AccessRoleLabels.accessLevel(m, isThai)
                    val canManageThisMember = if (isOwner) m.userId != state.myUserId else (iCanManage && !permissionsLocked)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = canManageThisMember) {
                                navController.navigate(Screen.PermissionSettingsDetail.route(m.userId))
                            },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AccessAvatar(m.avatarUrl, m.displayName)
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        m.displayName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        m.email ?: AccessRoleLabels.title(m.role, isThai),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (canManageThisMember) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            navController.navigate(Screen.PermissionSettingsDetail.route(m.userId))
                                        },
                                        modifier = Modifier.size(36.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = Primary.copy(alpha = 0.1f),
                                            contentColor = Primary
                                        )
                                    ) {
                                        Icon(
                                            Icons.Filled.Tune,
                                            contentDescription = if (isThai) "ตั้งค่าสิทธิ์" else "Permission settings",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val badgeTint = when {
                                    isFull -> HealthGood
                                    canLog -> Color(0xFFF59E0B)
                                    else -> Primary
                                }
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = badgeTint.copy(alpha = 0.12f)
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            when {
                                                isFull -> Icons.Filled.CheckCircle
                                                canLog -> Icons.Filled.EditNote
                                                else -> Icons.Filled.Visibility
                                            },
                                            null, tint = badgeTint, modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = badgeTint,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Surface(
                                    onClick = {
                                        if (iCanManage) viewModel.setCaregiver(m.userId, !m.isCaregiver)
                                    },
                                    enabled = iCanManage,
                                    shape = RoundedCornerShape(100.dp),
                                    color = if (m.isCaregiver) Primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = if (m.isCaregiver) androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.3f)) else null
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            if (m.isCaregiver) Icons.Filled.VolunteerActivism else Icons.Filled.PersonOutline,
                                            contentDescription = null,
                                            tint = if (m.isCaregiver) Primary else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            if (m.isCaregiver) (if (isThai) "ผู้ช่วยดูแล" else "Caregiver")
                                            else (if (isThai) "ไม่ใช่ผู้ช่วยดูแล" else "Not caregiver"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (m.isCaregiver) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (m.isCaregiver) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                // Family, or hired help. Decides who appears under ครอบครัว on the
                                // home screen and nothing else - a nanny can still be a caregiver
                                // above, which is what puts her in handover and chat.
                                Surface(
                                    onClick = {
                                        if (iCanManage) viewModel.setFamily(m.userId, !m.isFamily)
                                    },
                                    enabled = iCanManage,
                                    shape = RoundedCornerShape(100.dp),
                                    color = if (m.isFamily) Primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    border = if (m.isFamily) androidx.compose.foundation.BorderStroke(1.dp, Primary.copy(alpha = 0.3f)) else null
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            if (m.isFamily) Icons.Filled.Diversity3 else Icons.Filled.WorkOutline,
                                            contentDescription = null,
                                            tint = if (m.isFamily) Primary else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            if (m.isFamily) (if (isThai) "คนในครอบครัว" else "Family")
                                            else (if (isThai) "ผู้ช่วยเหลือ" else "Helper"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (m.isFamily) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (m.isFamily) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            state.error?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }

            // Advanced Administration & History Section
            Spacer(Modifier.height(4.dp))
            Text(
                if (isThai) "ประวัติกิจกรรม" else "Activity history",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            AccessAdminBentoCard(
                icon = Icons.Filled.Timeline,
                title = if (isThai) "ประวัติกิจกรรมของลูก" else "Activity log",
                subtitle = if (isThai) "ดูทุกบันทึก พร้อมชื่อผู้บันทึกและแก้ไข" else "Every record for this child, with author and audit info",
                iconTint = Color(0xFF3949AB),
                iconBg = Color(0xFFE8EAF6),
                modifier = Modifier.fillMaxWidth(),
                onClick = { navController.navigate(Screen.ActivityHistory.route) }
            )

            Spacer(Modifier.height(4.dp))

            // Anyone who joined can leave. The owner cannot.
            val myId = state.myUserId
            if (myId != null && state.myRole != "owner") {
                TextButton(onClick = { confirmLeave = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Filled.Logout, null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isThai) "ออกจากการดูแลเด็กคนนี้" else "Leave this child",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(4.dp))
            }

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.VerifiedUser, null, tint = HealthGood, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "SECURED BY NONGRAK AI ENCRYPTION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (isThai) "ข้อมูลสุขภาพของคุณได้รับการปกป้องตามมาตรฐาน HIPAA และ PDPA เราจะไม่แบ่งปันข้อมูลหากไม่ได้รับอนุญาตจากคุณ"
                    else "Your health data is protected under HIPAA and PDPA principles. We never share it without your permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (isThai) "เรียนรู้เพิ่มเติม" else "Learn more",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { navController.navigate(Screen.PrivacyPolicy.route) }
                )
            }
        }
    }
}

/** 3-column top quick action tile for adding person, QR code, and roles. */
@Composable
private fun QuickAccessActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    isPrimary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(108.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPrimary) Primary else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isPrimary) 2.dp else 1.dp),
        border = if (!isPrimary) androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ) else null
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isPrimary) Color.White.copy(alpha = 0.22f) else iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isPrimary) Color.White else iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isPrimary) Color.White else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isPrimary) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    fontSize = 10.sp
                )
            }
        }
    }
}

/** Clean Bento action card for admin actions like Transfer Ownership and Activity History. */
@Composable
private fun AccessAdminBentoCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(98.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    fontSize = 10.5.sp
                )
            }
        }
    }
}
