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
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.HealthRecordRules
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.LocalDate

/** "ติดตามอาการป่วย" — the tracker for one sickness episode (a health_records row with
 *  status="monitoring"), showing "Day N" + the accumulated day-by-day symptom-log history. */
@Composable
fun SicknessFollowUpScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val record = state.healthRecords.firstOrNull { it.id == state.openHealthRecordId }
    var showOlderLogs by remember { mutableStateOf(false) }

    LaunchedEffect(record?.id) {
        record?.let {
            childViewModel.loadSymptomLogs(it.id)
            childViewModel.loadSicknessFollowUpInsight(s.isThai)
        }
    }

    val startDate = record?.recordedAt ?: record?.createdAt
    val dayNumber = com.lullabyai.app.util.TimeUtils.parseInstant(startDate)?.let {
        java.time.temporal.ChronoUnit.DAYS.between(it.atZone(java.time.ZoneId.systemDefault()).toLocalDate(), java.time.LocalDate.now()) + 1
    } ?: 1L
    val display = record?.let {
        HealthRecordRules.displayStatus(it, state.symptomActivityByRecord[it.id])
    }
    val cutoff = remember { LocalDate.now().minusMonths(1) }
    val recentLogs = remember(state.symptomLogs, cutoff) {
        state.symptomLogs.filter { log ->
            val d = com.lullabyai.app.util.TimeUtils.localDate(log.logDate)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            d == null || !d.isBefore(cutoff)
        }
    }
    val olderLogs = remember(state.symptomLogs, cutoff) {
        state.symptomLogs.filter { log ->
            val d = com.lullabyai.app.util.TimeUtils.localDate(log.logDate)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            d != null && d.isBefore(cutoff)
        }
    }
    val visibleLogs = if (showOlderLogs) state.symptomLogs else recentLogs

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ติดตามอาการป่วย" else "Sickness Follow-up",
                onBack = onBack,
                navController = navController
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

            if (display == HealthRecordRules.DisplayStatus.AUTO_RESOLVED) {
                item {
                    Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFF558B2F).copy(0.1f), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (s.isThai) "ไม่มีบันทึกอาการต่อเนื่อง 7 วัน — ระบบถือว่าหายแล้ว (ไม่ลบข้อมูล)"
                            else "No active log for 7 days — assumed resolved (data is kept)",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF558B2F),
                            fontWeight = FontWeight.SemiBold
                        )
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

            item {
                OutlinedButton(
                    onClick = {
                        record?.let { childViewModel.selectHealthRecord(it.id); navController.navigate(Screen.LogSicknessSymptoms.route) }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(100.dp)
                ) {
                    Icon(Icons.Filled.EditNote, null, tint = Primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (s.isThai) "แก้ไขรายการบันทึก" else "Edit current log", color = Primary, fontWeight = FontWeight.Bold)
                }
            }

            item {
                AIInsightCard(
                    text = state.sicknessFollowUpInsight
                        ?: if (s.isThai) "กำลังวิเคราะห์อาการ..." else "Analyzing symptoms...",
                    isLoading = state.sicknessFollowUpInsightLoading || state.sicknessFollowUpInsight == null
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (s.isThai) "ประวัติการบันทึก" else "Log history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { navController.navigate(Screen.MedicationHistory.route) }) {
                        Text(if (s.isThai) "ตารางยา" else "Meds", color = Primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            if (visibleLogs.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (state.symptomLogs.isEmpty()) {
                                if (s.isThai) "ยังไม่มีบันทึกวันนี้" else "No day logs yet"
                            } else {
                                if (s.isThai) "ไม่มีบันทึกใน 1 เดือนล่าสุด" else "No logs in the last month"
                            },
                            color = Outline
                        )
                    }
                }
            } else {
                items(visibleLogs, key = { it.id }) { log -> SymptomLogCard(log, s.isThai) }
            }

            if (!showOlderLogs && olderLogs.isNotEmpty()) {
                item {
                    TextButton(onClick = { showOlderLogs = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            if (s.isThai) "ดูเพิ่มเติม (${olderLogs.size})" else "Show more (${olderLogs.size})",
                            color = Primary, fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Filled.ExpandMore, null, tint = Primary)
                    }
                }
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (log.temperature != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Thermostat, null, tint = Outline, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${log.temperature}°C", style = MaterialTheme.typography.labelSmall, color = Outline)
                        }
                    }
                    log.painLevel?.takeIf { it > 0 }?.let { pain ->
                        Text(
                            if (isThai) "ปวด $pain/10" else "Pain $pain/10",
                            style = MaterialTheme.typography.labelSmall, color = Color(0xFFED6C02), fontWeight = FontWeight.Bold
                        )
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
