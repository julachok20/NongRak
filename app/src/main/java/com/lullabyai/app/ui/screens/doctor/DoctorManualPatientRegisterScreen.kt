@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.doctorconnect.thaiDateTimeLabel
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DoctorManualRegisterViewModel
import com.lullabyai.app.viewmodel.PendingBookingRow

/** Doctor-side manual registration — Stitch ref `39.register`. Pick a pending booking, assign HN,
 *  approve, or open full intake details. */
@Composable
fun DoctorManualPatientRegisterScreen(navController: NavController) {
    val s = LocalStrings.current
    val vm: DoctorManualRegisterViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    LifecycleResumeEffect(Unit) {
        vm.load()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ลงทะเบียนผู้ป่วย" else "Register patient",
                onBack = { navController.popBackStack() },
                navController = navController,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 88.dp)
        ) {
            item {
                Text(
                    if (s.isThai) "เลือกคำขอจอง/ลงทะเบียนที่รอดำเนินการ แล้วบันทึก HN เพื่อรับผู้ป่วยเข้าระบบ"
                    else "Select a pending booking, assign HN, and approve registration.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Outline
                )
            }
            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Primary)
                    }
                }
            } else if (state.pendingRows.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Filled.PersonOff, null, tint = Outline, modifier = Modifier.size(40.dp))
                        Text(
                            if (s.isThai) "ยังไม่มีคำขอที่รอลงทะเบียน" else "No pending registration requests",
                            color = Outline
                        )
                        TextButton(onClick = { navController.navigate(Screen.DoctorPatientRegistrationApproval.route) }) {
                            Text(if (s.isThai) "ดูคำขอทั้งหมด" else "View all requests")
                        }
                    }
                }
            } else {
                items(state.pendingRows, key = { it.appointment.id }) { row ->
                    ManualRegisterCard(
                        row = row,
                        isThai = s.isThai,
                        selected = false,
                        onSelect = {
                            navController.navigate(Screen.DoctorPatientRegistrationDetail.route(row.appointment.id))
                        },
                        onOpenDetail = {
                            navController.navigate(Screen.DoctorPatientRegistrationDetail.route(row.appointment.id))
                        }
                    )
                }
            }
            item {
                Text(
                    if (s.isThai) "แตะรายการเพื่อเปิดหน้าลงทะเบียนตามดีไซน์ 39 — ซิงค์ข้อมูลเด็ก วัคซีน และผู้ปกครอง"
                    else "Tap a request to open the full registration form (Stitch 39) with synced child, vaccines, and guardian info.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Outline,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ManualRegisterCard(
    row: PendingBookingRow,
    isThai: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpenDetail: () -> Unit
) {
    Card(
        onClick = onSelect,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Primary.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface
        ),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Primary) else null
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(row.patientName ?: (if (isThai) "ไม่ระบุชื่อ" else "Unnamed"), fontWeight = FontWeight.Bold)
                    row.birthdate?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Outline)
                    }
                    row.guardianName?.let {
                        Text(
                            (if (isThai) "ผู้ปกครอง: " else "Guardian: ") + it,
                            style = MaterialTheme.typography.bodySmall,
                            color = Outline
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(100.dp), color = HealthAttention.copy(alpha = 0.12f)) {
                    Text(
                        if (isThai) "รออนุมัติ" else "Pending",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = HealthAttention,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Text(
                thaiDateTimeLabel(row.appointment.scheduledAt, isThai)
                    ?: (if (isThai) "รอกำหนดเวลานัด" else "Awaiting schedule"),
                style = MaterialTheme.typography.labelMedium,
                color = Primary
            )
            row.intakeNotes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(
                    notes.lines().take(4).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Outline,
                    maxLines = 4
                )
            }
            TextButton(onClick = onOpenDetail) {
                Text(if (isThai) "ดูรายละเอียดเต็ม" else "Full details")
            }
        }
    }
}
