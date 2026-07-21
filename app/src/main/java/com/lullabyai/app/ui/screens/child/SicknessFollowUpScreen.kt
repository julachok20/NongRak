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
import com.lullabyai.app.data.model.SymptomLog
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

/** "ติดตามอาการป่วย" — the tracker for one sickness episode (a health_records row with
 *  status="monitoring"), showing "Day N" + the accumulated day-by-day symptom-log history. */
@Composable
fun SicknessFollowUpScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val record = state.healthRecords.firstOrNull { it.id == state.openHealthRecordId }

    LaunchedEffect(record?.id) { record?.let { childViewModel.loadSymptomLogs(it.id) } }

    val startDate = record?.recordedAt ?: record?.createdAt
    val dayNumber = com.lullabyai.app.util.TimeUtils.parseInstant(startDate)?.let {
        java.time.temporal.ChronoUnit.DAYS.between(it.atZone(java.time.ZoneId.systemDefault()).toLocalDate(), java.time.LocalDate.now()) + 1
    } ?: 1L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "ติดตามอาการป่วย" else "Sickness Follow-up", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
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
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(record?.title ?: "", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            (if (s.isThai) "เริ่มป่วย: " else "Started: ") + (com.lullabyai.app.util.TimeUtils.localDate(startDate) ?: "-"),
                            style = MaterialTheme.typography.bodySmall, color = Outline
                        )
                        Spacer(Modifier.height(10.dp))
                        Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                            Text(
                                if (s.isThai) "วันที่ $dayNumber ของการติดตาม" else "Day $dayNumber of tracking",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            record?.let { childViewModel.selectHealthRecord(it.id); navController.navigate(Screen.LogSicknessSymptoms.route) }
                        },
                        modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(100.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (s.isThai) "บันทึกอาการวันนี้" else "Log today", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { record?.let { childViewModel.toggleHealthRecordStatus(it.id); onBack() } },
                        modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(100.dp)
                    ) {
                        Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (s.isThai) "หายแล้ว" else "Mark recovered", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                }
            }

            item { Text(if (s.isThai) "ประวัติการบันทึก" else "Log history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }

            if (state.symptomLogs.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { Text(if (s.isThai) "ยังไม่มีบันทึกวันนี้" else "No day logs yet", color = Outline) } }
            } else {
                items(state.symptomLogs, key = { it.id }) { log -> SymptomLogCard(log, s.isThai) }
            }
        }
    }
}

@Composable
private fun SymptomLogCard(log: SymptomLog, isThai: Boolean) {
    val activeSymptoms = log.symptoms.filterValues { it != "none" }
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(com.lullabyai.app.util.TimeUtils.localDate(log.logDate) ?: log.logDate, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (log.temperature != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Thermostat, null, tint = Outline, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${log.temperature}°C", style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                }
            }
            if (activeSymptoms.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    activeSymptoms.entries.take(4).forEach { (k, v) ->
                        Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)) {
                            Text("${symptomLabel(k, isThai)}: ${severityLabel(v, isThai)}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            } else {
                Text(if (isThai) "ไม่มีอาการวันนี้" else "No symptoms today", style = MaterialTheme.typography.bodySmall, color = Outline)
            }
            log.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

private fun symptomLabel(key: String, isThai: Boolean) = when (key) {
    "fever" -> if (isThai) "ตัวร้อน" else "Fever"
    "cough" -> if (isThai) "ไอ" else "Cough"
    "runny_nose" -> if (isThai) "มีน้ำมูก" else "Runny nose"
    "rash" -> if (isThai) "ผื่น" else "Rash"
    "lethargic" -> if (isThai) "ซึม" else "Lethargic"
    "vomiting" -> if (isThai) "อาเจียน" else "Vomiting"
    else -> if (isThai) "ท้องเสีย" else "Diarrhea"
}
private fun severityLabel(key: String, isThai: Boolean) = when (key) {
    "mild" -> if (isThai) "เล็กน้อย" else "Mild"
    "moderate" -> if (isThai) "ปานกลาง" else "Moderate"
    "severe" -> if (isThai) "รุนแรง" else "Severe"
    else -> key
}
