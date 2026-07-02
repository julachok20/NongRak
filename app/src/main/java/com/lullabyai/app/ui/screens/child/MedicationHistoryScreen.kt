@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.MedicationDose
import com.lullabyai.app.data.model.MedicationSchedule
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun MedicationHistoryScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val schedules = state.medicationSchedules.associateBy { it.id }
    val doses = state.medicationDoses

    LaunchedEffect(state.child?.id) { childViewModel.loadMedications() }

    val today = remember { LocalDate.now() }
    val todayDoses = remember(doses) {
        doses.filter { TimeUtils.parseInstant(it.scheduledAt)?.atZone(ZoneId.systemDefault())?.toLocalDate() == today }
            .sortedBy { it.scheduledAt }
    }
    val takenToday = todayDoses.count { it.status == "taken" }
    val nextDose = doses.filter { it.status == "pending" }
        .mapNotNull { d -> TimeUtils.parseInstant(d.scheduledAt)?.let { d to it } }
        .filter { it.second.isAfter(Instant.now()) }.minByOrNull { it.second }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "ประวัติการใช้ยา" else "Medication History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Summary bento
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryTile(if (s.isThai) "ทานยาแล้ววันนี้" else "Taken today", "$takenToday/${todayDoses.size}", Modifier.weight(1f))
                    SummaryTile(
                        if (s.isThai) "ยาครั้งถัดไป" else "Next dose",
                        nextDose?.first?.scheduledAt?.let { TimeUtils.localTime(it) } ?: "-",
                        Modifier.weight(1f)
                    )
                }
            }

            item {
                Button(
                    onClick = { navController.navigate(Screen.AddMedicineSchedule.route) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (s.isThai) "เพิ่มตารางการทานยา" else "Add medication schedule", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            item {
                Text(if (s.isThai) "ประวัติยา (วันนี้)" else "Today's history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }

            if (todayDoses.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { Text(if (s.isThai) "ยังไม่มีตารางการทานยาวันนี้" else "No doses scheduled today", color = Outline) } }
            } else {
                items(todayDoses, key = { it.id }) { dose ->
                    DoseRow(
                        dose = dose,
                        medicineName = schedules[dose.scheduleId]?.medicineName ?: "",
                        dosageText = schedules[dose.scheduleId]?.let { "${it.dosage ?: ""} ${it.dosageUnit}".trim() } ?: "",
                        onTaken = { childViewModel.markDoseTaken(dose.id) },
                        onSkip = { childViewModel.skipDose(dose.id) }
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, color = Primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DoseRow(dose: MedicationDose, medicineName: String, dosageText: String, onTaken: () -> Unit, onSkip: () -> Unit) {
    val s = LocalStrings.current
    val time = TimeUtils.localTime(dose.scheduledAt) ?: ""
    val (badgeColor, badgeText) = when (dose.status) {
        "taken" -> Color(0xFF4CAF50) to (if (s.isThai) "ทานแล้ว" else "Completed")
        "skipped" -> Color(0xFF9E9E9E) to (if (s.isThai) "ข้าม" else "Skipped")
        else -> Primary to (if (s.isThai) "กำลังดำเนินการ" else "In progress")
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Medication, null, tint = Primary, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(medicineName, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("$time น. • $dosageText", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Surface(shape = RoundedCornerShape(100.dp), color = badgeColor.copy(0.12f)) {
                    Text(badgeText, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = badgeColor, fontWeight = FontWeight.Bold)
                }
            }
            if (dose.status == "pending") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSkip, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        Text(if (s.isThai) "ข้าม" else "Skip", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(onClick = onTaken, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp), contentPadding = PaddingValues(vertical = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) {
                        Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        Text(if (s.isThai) "ทานแล้ว" else "Taken", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}
