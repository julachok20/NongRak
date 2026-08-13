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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
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

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(Primary.copy(0.12f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.MedicalInformation, null,
                            tint = Primary, modifier = Modifier.size(26.dp)
                        )
                    }
                    Column {
                        Text(
                            if (isThai) "ข้อมูลสุขภาพของ \"${state.childName.ifBlank { "ลูก" }}\""
                            else "Health data for \"${state.childName.ifBlank { "your child" }}\"",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (isThai) "จัดการผู้ที่มีสิทธิ์เข้าถึงข้อมูลสุขภาพ บันทึกการเจริญเติบโต และข้อมูลทางการแพทย์ทั้งหมด"
                            else "Manage who can reach the health data, growth records and every medical detail",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isThai) "ผู้ที่มีสิทธิ์เข้าถึง" else "People with access",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (isThai) "${state.members.size} รายการ" else "${state.members.size} people",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                state.members.forEach { m ->
                    // Owners and co-owners already hold every permission, so their grid has
                    // nothing to edit — "Owner/Co-Owner can do anything (so user cannot edit
                    // their permission)". The caregiver tick stays live for them: that is not a
                    // permission, it says who actually looks after the child.
                    val permissionsLocked = AccessRoleLabels.isFullAccess(m.role)
                    val (badge, isFull, canLog) = AccessRoleLabels.accessLevel(m, isThai)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = iCanManage && !permissionsLocked) {
                                navController.navigate(Screen.PermissionSettingsDetail.route(m.userId))
                            },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(1.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                AccessAvatar(m.avatarUrl, m.displayName)
                                Column(Modifier.weight(1f)) {
                                    Text(m.displayName, fontWeight = FontWeight.Bold)

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = m.isCaregiver,
                                            onCheckedChange = { viewModel.setCaregiver(m.userId, it) },
                                            enabled = iCanManage,
                                            modifier = Modifier.size(22.dp),
                                            colors = CheckboxDefaults.colors(checkedColor = Primary)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            if (isThai) "บุคคลที่ช่วยดูแลเด็ก" else "Helps care for this child",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Text(
                                        m.email ?: AccessRoleLabels.title(m.role, isThai),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val badgeTint = when {
                                    isFull -> HealthGood
                                    canLog -> Color(0xFFF59E0B)
                                    else -> Primary
                                }
                                Row(
                                    Modifier
                                        .background(badgeTint.copy(0.14f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        when {
                                            isFull -> Icons.Filled.CheckCircle
                                            canLog -> Icons.Filled.EditNote
                                            else -> Icons.Filled.Visibility
                                        },
                                        null, tint = badgeTint, modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeTint
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                // Locked roles show no gear: there is nothing behind it for them.
                                if (!permissionsLocked) {
                                    Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = if (isThai) "ตั้งค่าสิทธิ์" else "Permission settings",
                                        tint = if (iCanManage) Outline else Outline.copy(0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            state.error?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(2.dp))

            // Primary way in — a filled card rather than a FAB, so it sits in the flow of the page
            // beside the two alternatives instead of floating over the member list.
            Card(
                onClick = { navController.navigate(Screen.AddUserAccessActivity.route) },
                enabled = iCanManage,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Primary)
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(44.dp).background(Color.White.copy(0.18f), RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.PersonAdd, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (isThai) "เพิ่มบุคคล" else "Add a person",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        if (isThai) "เชิญผ่านอีเมลหรือชื่อผู้ใช้" else "Invite by email or username",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(0.85f)
                    )
                }
            }

            DashedActionCard(
                icon = Icons.Filled.QrCode2,
                title = if (isThai) "สแกน QR Code" else "Scan QR code",
                subtitle = if (isThai) "ให้บุคคลที่อยู่ใกล้สแกนเพื่อรับสิทธิ์ (หมดอายุ 10 นาที)"
                else "Have someone nearby scan to receive access (expires in 10 minutes)",
                enabled = iCanManage,
                onClick = { navController.navigate(Screen.ShareAccessQr.route) }
            )

            DashedActionCard(
                icon = Icons.Filled.Badge,
                title = if (isThai) "จัดการบทบาท" else "Manage roles",
                subtitle = if (isThai) "สร้างหรือลบบทบาทที่กำหนดเอง" else "Create or remove custom roles",
                enabled = iCanManage,
                onClick = { navController.navigate(Screen.RoleManagement.route) }
            )

            // Distinct from "ประวัติการขอเข้าถึง" inside Add Person: that is the audit trail of who
            // was granted what, this is the child's care record — what everyone actually logged.
            DashedActionCard(
                icon = Icons.Filled.Timeline,
                title = if (isThai) "ประวัติกิจกรรม" else "Activity history",
                subtitle = if (isThai) "ดูทุกบันทึกของลูก พร้อมชื่อผู้บันทึก"
                else "Every record for this child, with who wrote it",
                enabled = true,
                onClick = { navController.navigate(Screen.ActivityHistory.route) }
            )

            Spacer(Modifier.height(8.dp))

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

/** The dashed-outline secondary action from the design — present, but visibly not the main path. */
@Composable
private fun DashedActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val stroke = if (enabled) Primary.copy(0.45f) else Outline.copy(0.35f)
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val r = 20.dp.toPx()
                drawRoundRect(
                    color = stroke,
                    cornerRadius = CornerRadius(r, r),
                    size = Size(size.width, size.height),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(10.dp.toPx(), 7.dp.toPx())
                        )
                    )
                )
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(40.dp).background(Primary.copy(0.10f), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = if (enabled) Primary else Outline, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Primary else Outline
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
