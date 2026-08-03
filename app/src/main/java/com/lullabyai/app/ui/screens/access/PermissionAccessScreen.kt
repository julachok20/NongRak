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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.AccessPermissionViewModel
import com.lullabyai.app.viewmodel.AccessRoleLabels

/**
 * Stitch: "Permission Access (การจัดการสิทธิ์เข้าถึง)" — member list + add/QR entry points.
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

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "จัดการสิทธิ์เข้าถึง" else "Permission Access",
                onBack = onBack,
                navController = navController
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Screen.AddUserAccessActivity.route) },
                containerColor = Primary,
                contentColor = Color.White,
                icon = { Icon(Icons.Filled.PersonAdd, null) },
                text = { Text(if (isThai) "เพิ่มบุคคล" else "Add person", fontWeight = FontWeight.Bold) }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.HealthAndSafety, null, tint = Primary, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isThai) "ข้อมูลสุขภาพของ \"${state.childName.ifBlank { "ลูก" }}\""
                            else "Health data for \"${state.childName.ifBlank { "child" }}\"",
                            fontWeight = FontWeight.Bold,
                            color = Primary
                        )
                        Text(
                            if (isThai) "จัดการผู้ที่มีสิทธิ์เข้าถึงและบันทึกข้อมูล"
                            else "Manage who can view and record this child's data",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                if (isThai) "ผู้มีสิทธิ์เข้าถึง ${state.members.size} รายการ"
                else "${state.members.size} people with access",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            if (state.loading) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                state.members.forEach { m ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (m.role != "owner") {
                                    navController.navigate(Screen.PermissionSettingsDetail.route(m.userId))
                                }
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            AccessAvatar(m.avatarUrl, m.displayName)
                            Column(Modifier.weight(1f)) {
                                Text(m.displayName, fontWeight = FontWeight.Bold)
                                Text(
                                    AccessRoleLabels.title(m.role, isThai),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Primary
                                )
                                m.email?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            when (m.role) {
                                "owner" -> Icon(Icons.Filled.CheckCircle, null, tint = HealthGood)
                                else -> Icon(Icons.Filled.Settings, null, tint = Outline)
                            }
                        }
                    }
                }
            }

            state.error?.let {
                Text(it, color = Error, style = MaterialTheme.typography.bodySmall)
            }

            Card(
                onClick = { navController.navigate(Screen.RoleManagement.route) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(0.10f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Badge, null, tint = Primary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (isThai) "จัดการบทบาท" else "Manage roles", fontWeight = FontWeight.Bold)
                        Text(
                            if (isThai) "สร้างชุดสิทธิ์ของตัวเอง แล้วกำหนดให้ผู้ใช้"
                            else "Create your own permission sets and assign them",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                }
            }

            Card(
                onClick = { navController.navigate(Screen.ShareAccessQr.route) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Tertiary.copy(0.12f))
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.QrCode2, null, tint = Tertiary, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isThai) "แชร์ QR Code" else "Share QR Code",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isThai) "ให้ผู้ที่อยู่ใกล้สแกนเพื่อรับสิทธิ์ (หมดอายุ 10 นาที)"
                            else "Have someone nearby scan to receive access (10-min TTL)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Outline)
                }
            }

            Card(
                onClick = { navController.navigate(Screen.AddUserAccessActivity.route) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceContainerLow)
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.History, null, tint = Primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            if (isThai) "ประวัติการขอเข้าถึง" else "Access activity",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isThai) "ดูคำขอและการเปลี่ยนแปลงสิทธิ์ล่าสุด"
                            else "See recent requests and permission changes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Primary.copy(0.06f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.VerifiedUser, null, tint = Primary)
                Text(
                    if (isThai) "Secured by NongRak AI Encryption — ข้อมูลสุขภาพของลูกถูกเข้ารหัสตามมาตรฐาน HIPAA / PDPA"
                    else "Secured by NongRak AI Encryption — child health data protected under HIPAA / PDPA principles",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
