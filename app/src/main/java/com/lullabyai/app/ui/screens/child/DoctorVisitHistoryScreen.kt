@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.lullabyai.app.data.model.DoctorVisit
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

private enum class VisitFilter { ALL, THIS_MONTH, LAST_3_MONTHS, CUSTOM, SCHEDULED, COMPLETED }

/** "ประวัติการพบแพทย์" — the doctor-visit hub: search + a month/3-month range filter, a vertical
 *  list of visits (scheduled appointments and completed logs mixed, newest first), and a
 *  speed-dial FAB routing to RecordAppointmentScreen / RecordVisitScreen. */
@Composable
fun DoctorVisitHistoryScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(VisitFilter.ALL) }
    var fabExpanded by remember { mutableStateOf(false) }
    // Custom date-range filter (#3 fidelity fix — presets only covered "this month" / "past 3
    // months"; a parent looking for a visit outside those windows had no way to narrow it down).
    var customStart by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var customEnd by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.child?.id) {
        childViewModel.loadDoctorVisits()
        childViewModel.loadDoctorVisitHistoryInsight(s.isThai)
    }

    val today = remember { java.time.LocalDate.now() }
    val filtered = remember(state.doctorVisits, query, filter, customStart, customEnd) {
        state.doctorVisits.filter { visit ->
            val matchesQuery = query.isBlank() ||
                visit.doctorName?.contains(query, ignoreCase = true) == true ||
                visit.facilityName?.contains(query, ignoreCase = true) == true ||
                visit.diagnosis?.contains(query, ignoreCase = true) == true ||
                visit.reason?.contains(query, ignoreCase = true) == true
            val visitDate = com.lullabyai.app.util.TimeUtils.localDate(visit.visitDatetime)?.let {
                runCatching { java.time.LocalDate.parse(it) }.getOrNull()
            }
            val matchesRange = when (filter) {
                VisitFilter.ALL -> true
                VisitFilter.THIS_MONTH -> visitDate != null && visitDate.month == today.month && visitDate.year == today.year
                VisitFilter.LAST_3_MONTHS -> visitDate != null && !visitDate.isBefore(today.minusMonths(3))
                VisitFilter.CUSTOM -> visitDate != null &&
                    (customStart == null || !visitDate.isBefore(customStart)) &&
                    (customEnd == null || !visitDate.isAfter(customEnd))
                VisitFilter.SCHEDULED -> visit.status == "scheduled"
                VisitFilter.COMPLETED -> visit.status == "completed" || visit.status == "follow_up"
            }
            matchesQuery && matchesRange
        }
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ประวัติการพบแพทย์" else "Doctor Visit History",
                onBack = onBack,
                navController = navController,
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.PdfExport.route) }) {
                        Icon(Icons.Filled.PictureAsPdf, if (s.isThai) "ส่งออก" else "Export", tint = Primary)
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimatedVisibility(visible = fabExpanded, enter = fadeIn(), exit = fadeOut()) {
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SpeedDialOption(
                            label = if (s.isThai) "บันทึกย้อนหลัง" else "Record past visit",
                            icon = Icons.Filled.History
                        ) {
                            fabExpanded = false
                            childViewModel.selectDoctorVisit(null) // clear any stale open-visit id — this is a NEW record
                            navController.navigate(Screen.RecordDoctorVisit.route)
                        }
                        SpeedDialOption(
                            label = if (s.isThai) "นัดหมายล่วงหน้า" else "Schedule appointment",
                            icon = Icons.Filled.CalendarMonth
                        ) { fabExpanded = false; navController.navigate(Screen.RecordAppointment.route) }
                    }
                }
                FloatingActionButton(onClick = { fabExpanded = !fabExpanded }, containerColor = Primary, contentColor = Color.White) {
                    Icon(if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add, null)
                }
            }
        },
        bottomBar = {
            com.lullabyai.app.ui.components.AppBottomNav(
                selected = com.lullabyai.app.ui.components.BottomTab.CHILD,
                onHome = { navController.navigate(Screen.Dashboard.route) { launchSingleTop = true } },
                onChatbot = { navController.navigate(Screen.AiChat.route) { launchSingleTop = true } },
                onChild = { navController.navigate(Screen.ChildSummary.route) { launchSingleTop = true } },
                onStats = { navController.navigate(Screen.StatisticsInsights.route) { launchSingleTop = true } },
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } },
                onConnect = { navController.navigate(Screen.Maintenance.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (s.isThai) "ค้นหาแพทย์หรือสถานพยาบาล..." else "Search doctor or facility...", color = Outline) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                    singleLine = true, shape = RoundedCornerShape(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                )
            }
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChipItem(if (s.isThai) "ทั้งหมด" else "All", filter == VisitFilter.ALL) { filter = VisitFilter.ALL }
                    FilterChipItem(if (s.isThai) "เดือนนี้" else "This month", filter == VisitFilter.THIS_MONTH) { filter = VisitFilter.THIS_MONTH }
                    FilterChipItem(if (s.isThai) "3 เดือนล่าสุด" else "Past 3 months", filter == VisitFilter.LAST_3_MONTHS) { filter = VisitFilter.LAST_3_MONTHS }
                    FilterChipItem(if (s.isThai) "กำหนดเอง" else "Custom range", filter == VisitFilter.CUSTOM) { filter = VisitFilter.CUSTOM }
                    FilterChipItem(if (s.isThai) "นัดหมาย" else "Scheduled", filter == VisitFilter.SCHEDULED) { filter = VisitFilter.SCHEDULED }
                    FilterChipItem(if (s.isThai) "เสร็จสิ้น" else "Completed", filter == VisitFilter.COMPLETED) { filter = VisitFilter.COMPLETED }
                }
            }

            if (filter == VisitFilter.CUSTOM) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { showStartPicker = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp)) {
                            Icon(Icons.Filled.CalendarToday, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text(customStart?.toString() ?: (if (s.isThai) "จากวันที่" else "From date"), style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(onClick = { showEndPicker = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp)) {
                            Icon(Icons.Filled.CalendarToday, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                            Text(customEnd?.toString() ?: (if (s.isThai) "ถึงวันที่" else "To date"), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            item {
                AIInsightCard(
                    text = state.doctorVisitHistoryInsight
                        ?: if (s.isThai) "กำลังวิเคราะห์ประวัติพบแพทย์..." else "Analyzing visit history...",
                    isLoading = state.doctorVisitHistoryInsightLoading || state.doctorVisitHistoryInsight == null
                )
            }

            if (filtered.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.LocalHospital, null, tint = Outline, modifier = Modifier.size(40.dp))
                        Text(if (s.isThai) "ยังไม่มีบันทึกการพบแพทย์" else "No doctor visits yet", color = Outline)
                    }
                }
            } else {
                items(filtered, key = { it.id }) { visit ->
                    VisitCard(visit, s.isThai) {
                        childViewModel.selectDoctorVisit(visit.id)
                        navController.navigate(Screen.VisitDetails.route)
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        val zone = java.time.ZoneId.systemDefault()
        val initialMillis = (customStart ?: today).atStartOfDay(zone).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms -> customStart = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }
                    showStartPicker = false
                }) { Text(if (s.isThai) "ตกลง" else "OK") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    if (showEndPicker) {
        val zone = java.time.ZoneId.systemDefault()
        val initialMillis = (customEnd ?: today).atStartOfDay(zone).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms -> customEnd = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }
                    showEndPicker = false
                }) { Text(if (s.isThai) "ตกลง" else "OK") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun SpeedDialOption(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
            Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
        SmallFloatingActionButton(onClick = onClick, containerColor = MaterialTheme.colorScheme.surface, contentColor = Primary) {
            Icon(icon, null)
        }
    }
}

@Composable
private fun FilterChipItem(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) }, shape = RoundedCornerShape(100.dp),
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(0.12f), selectedLabelColor = Primary)
    )
}

@Composable
private fun VisitCard(visit: DoctorVisit, isThai: Boolean, onOpenDetail: () -> Unit) {
    val (badgeBg, badgeFg, badgeText) = when (visit.status) {
        "completed" -> Triple(Color(0xFFE8F5E9), Color(0xFF2E7D32), if (isThai) "เสร็จสิ้น" else "COMPLETED")
        "follow_up" -> Triple(Color(0xFFFFF3E0), Color(0xFFED6C02), if (isThai) "ติดตามผล" else "FOLLOW-UP")
        else -> Triple(Primary.copy(0.12f), Primary, if (isThai) "นัดหมาย" else "APPOINTMENT")
    }
    val dateText = com.lullabyai.app.util.TimeUtils.localDate(visit.visitDatetime) ?: ""
    val timeText = com.lullabyai.app.util.TimeUtils.localTime(visit.visitDatetime)
    val description = visit.diagnosis?.takeIf { it.isNotBlank() } ?: visit.reason?.takeIf { it.isNotBlank() }

    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(dateText + (timeText?.let { " • $it" } ?: ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(shape = RoundedCornerShape(100.dp), color = badgeBg) {
                    Text(badgeText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badgeFg)
                }
            }
            Text(
                visit.doctorName?.takeIf { it.isNotBlank() } ?: (if (isThai) "ไม่ระบุแพทย์" else "Doctor not specified"),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
            )
            visit.facilityName?.takeIf { it.isNotBlank() }?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.LocalHospital, null, tint = Outline, modifier = Modifier.size(14.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Outline)
                }
            }
            description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onOpenDetail) {
                    Text(if (isThai) "ดูรายละเอียด" else "View details", color = Primary, fontWeight = FontWeight.Bold)
                    Icon(Icons.Filled.ChevronRight, null, tint = Primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
