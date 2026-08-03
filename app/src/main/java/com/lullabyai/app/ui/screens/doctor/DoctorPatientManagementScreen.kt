@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.doctor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.DoctorPatientManagementViewModel

/** Secondary doctor-side navigation hub — a quick-access grid (Schedule / Patient Info /
 *  Registration Approval / Manage Slots) plus a small daily-stats strip. Distinct from
 *  DoctorPatientDirectoryScreen (that one is the searchable patient list with HN/status per
 *  child) — this screen is purely a launchpad, matching the Stitch "Patient Main Page
 *  (หน้าหลักผู้ปกครอง)" design, which despite its Thai title is doctor-side per the confirmed
 *  correction (its quick links are ทั้งหมด doctor actions: นัดหมาย, ข้อมูลผู้ป่วย, ลงทะเบียน,
 *  จัดการวันและเวลา). */
@Composable
fun DoctorPatientManagementScreen(navController: NavController) {
    val s = LocalStrings.current
    val vm: DoctorPatientManagementViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ผู้ป่วย" else "Patients",
                navController = navController,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickAccessTile(Modifier.weight(1f), Icons.Filled.CalendarToday, if (s.isThai) "นัดหมาย" else "Appointments") {
                    navController.navigate(Screen.DoctorPatientSchedule.route)
                }
                QuickAccessTile(Modifier.weight(1f), Icons.Filled.ChildCare, if (s.isThai) "ข้อมูลผู้ป่วย" else "Patient Info") {
                    navController.navigate(Screen.DoctorPatientDirectory.route)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Every add-patient entry point lands on จัดการการลงทะเบียน; the manual form is
                // reached from there via เพิ่มผู้ป่วยเอง.
                QuickAccessTile(Modifier.weight(1f), Icons.Filled.PersonAdd, if (s.isThai) "ลงทะเบียน" else "Register") {
                    navController.navigate(Screen.DoctorPatientRegistrationApproval.route)
                }
                QuickAccessTile(Modifier.weight(1f), Icons.Filled.CalendarMonth, if (s.isThai) "จัดการวันและเวลา" else "Time & Dates") {
                    navController.navigate(Screen.DoctorTimeAppointmentHub.route)
                }
            }
            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(if (s.isThai) "สถิติผู้ป่วยประจำวัน" else "Daily Statistics", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(Modifier.weight(1f), Icons.Filled.Group, if (s.isThai) "จำนวนผู้ป่วยวันนี้" else "Patients today", "${state.todayCount}", if (s.isThai) "ราย" else "")
                        StatTile(Modifier.weight(1f), Icons.Filled.PendingActions, if (s.isThai) "รอดำเนินการ" else "Pending", "${state.pendingCount}", if (s.isThai) "ราย" else "")
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    if (s.isThai) "เราดูแลด้วยหัวใจ" else "We care with heart",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary
                )
                Text(
                    if (s.isThai) "ความปลอดภัยของลูกคือสิ่งสำคัญที่สุด" else "Your child's safety comes first",
                    style = MaterialTheme.typography.labelSmall, color = Outline
                )
            }
        }
    }
}

@Composable
private fun QuickAccessTile(modifier: Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(28.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatTile(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    unit: String
) {
    Column(modifier.background(SurfaceContainerLow, RoundedCornerShape(12.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = Outline)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Primary)
            if (unit.isNotBlank()) Text(unit, style = MaterialTheme.typography.labelSmall, color = Outline, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}
