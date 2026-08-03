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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.viewmodel.DoctorTimeHubViewModel

/** Must match [Screen.DoctorTimeAppointmentHub]. */
const val DOCTOR_TIME_APPOINTMENT_HUB_ROUTE = "doctor_time_appointment_hub"

/**
 * Stitch "Time & Appointment Management Hub (จัดการเวลาและนัดหมาย)"
 * (project 10774270498029237432, screen 0aa4fac4b97f4ab2a6579b38b0ed6b6f).
 *
 * Reached from the Patient Main Page tile "จัดการวันและเวลา". Two entries — special dates/hours
 * and bookable slots — plus a read-only status strip.
 */
@Composable
fun DoctorTimeAppointmentHubScreen(navController: NavController) {
    val s = LocalStrings.current
    val isThai = s.isThai
    val vm: DoctorTimeHubViewModel = viewModel()
    val state by vm.uiState.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "จัดการเวลาและนัดหมาย" else "Time & Appointment Management",
                onBack = { navController.popBackStack() },
                navController = navController,
                isDoctor = true
            )
        },
        bottomBar = { DoctorBottomNav(navController, DoctorTab.PATIENTS) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // --- Hero ---
            Box(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(Primary, Primary.copy(alpha = 0.75f))))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Clinic Management",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        if (isThai) "เพิ่มประสิทธิภาพการจัดการคลินิก" else "Streamline how your clinic runs",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            EntryCard(
                icon = Icons.Filled.Schedule,
                title = if (isThai) "จัดการเวลาทำงาน" else "Manage Working Hours",
                description = if (isThai)
                    "ตั้งค่าเวลาเปิด-ปิด และวันหยุดของสถานพยาบาล เพื่อควบคุมตารางงานทั้งหมด"
                else
                    "Set opening hours and clinic holidays to control your whole schedule",
                actionLabel = if (isThai) "ไปที่การตั้งค่า" else "Go to settings",
                onClick = { navController.navigate(Screen.DoctorSpecialDates.route) }
            )

            EntryCard(
                icon = Icons.Filled.EditCalendar,
                title = if (isThai) "ตั้งค่าช่วงเวลานัดหมาย" else "Appointment Slot Settings",
                description = if (isThai)
                    "จัดการช่วงเวลาที่เปิดให้คนไข้จองนัดหมาย รวมถึงการเว้นระยะห่างของแต่ละเคส"
                else
                    "Manage the slots patients can book, including spacing between cases",
                actionLabel = if (isThai) "ไปที่การตั้งค่า" else "Go to settings",
                onClick = { navController.navigate(Screen.DoctorManageSlots.route) }
            )

            // --- System summary ---
            Text(
                if (isThai) "สรุปสถานะปัจจุบัน (System Summary)" else "System Summary",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Outline
            )
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    SummaryRow(
                        dotColor = if (state.isOpenToday) HealthGood else Outline,
                        label = if (isThai) "สถานะคลินิก" else "Clinic status",
                        value = if (state.isOpenToday)
                            (if (isThai) "กำลังเปิดทำการ" else "Open now") + (state.todayHours?.let { " ($it)" } ?: "")
                        else (if (isThai) "ปิดทำการ" else "Closed today")
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SummaryRow(
                        icon = Icons.Filled.EventAvailable,
                        label = if (isThai) "นัดหมายวันนี้" else "Appointments today",
                        value = if (isThai) "${state.appointmentsToday} รายการ" else "${state.appointmentsToday}"
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    SummaryRow(
                        icon = Icons.Filled.Update,
                        label = if (isThai) "คิวที่ใกล้ที่สุด" else "Next in queue",
                        value = state.nextQueueTime?.let { if (isThai) "$it น." else it }
                            ?: (if (isThai) "ไม่มีคิวเหลือวันนี้" else "None left today")
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EntryCard(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(28.dp))
            }
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary)
                Icon(Icons.Filled.ChevronRight, null, tint = Primary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    icon: ImageVector? = null,
    dotColor: Color? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when {
            dotColor != null -> Box(
                Modifier.padding(top = 6.dp).size(10.dp).clip(CircleShape).background(dotColor)
            )
            icon != null -> Icon(icon, null, tint = Primary, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.42f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Primary,
            modifier = Modifier.weight(0.58f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End
        )
    }
}
