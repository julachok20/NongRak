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
import com.lullabyai.app.data.model.MedicationSchedule
import com.lullabyai.app.data.repository.MedicationRepository
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private enum class MedFilter { ALL, ACTIVE, COMPLETED }

/** "ประวัติการใช้ยาทั้งหมด" — every medication schedule ever created, grouped by month, distinct
 *  from MedicationHistoryScreen (today's doses only). Uses MedicationRepository.getSchedules(),
 *  which already returns full history — no repository changes needed. */
@Composable
fun FullMedicationHistoryScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val childId = childViewModel.uiState.collectAsState().value.child?.id
    var schedules by remember { mutableStateOf<List<MedicationSchedule>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf(MedFilter.ALL) }
    var query by remember { mutableStateOf("") }

    val state by childViewModel.uiState.collectAsState()
    LaunchedEffect(childId) {
        val id = childId ?: return@LaunchedEffect
        loading = true
        schedules = MedicationRepository().getSchedules(id).getOrDefault(emptyList())
        loading = false
        childViewModel.loadMedicationHistoryInsight(s.isThai)
    }

    val today = remember { LocalDate.now() }
    fun isActive(sch: MedicationSchedule): Boolean {
        val end = sch.endDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val numDoses = sch.numDoses
        return if (sch.scheduleType == "date_range") end == null || !end.isBefore(today)
        else (numDoses == null || sch.dosesTaken < numDoses)
    }

    val filtered = remember(schedules, filter, query) {
        schedules.filter { sch ->
            (query.isBlank() || sch.medicineName.contains(query, ignoreCase = true)) &&
                when (filter) {
                    MedFilter.ALL -> true
                    MedFilter.ACTIVE -> isActive(sch)
                    MedFilter.COMPLETED -> !isActive(sch)
                }
        }
    }
    val byMonth = remember(filtered) {
        filtered.groupBy { sch ->
            runCatching { LocalDate.parse(sch.startDate ?: sch.createdAt?.take(10)) }.getOrNull()
                ?.let { it.year to it.monthValue } ?: (0 to 0)
        }.toSortedMap(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ประวัติการใช้ยาทั้งหมด" else "Full Medication History",
                onBack = onBack,
                navController = navController,
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.PdfExport.route) }) {
                        Icon(Icons.Filled.PictureAsPdf, if (s.isThai) "ส่งออก" else "Export", tint = Primary)
                    }
                }
            )
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
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (s.isThai) "ค้นหาชื่อยา..." else "Search medicine...", color = Outline) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                    singleLine = true, shape = RoundedCornerShape(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val labels = listOf(
                        MedFilter.ALL to (if (s.isThai) "ทั้งหมด" else "All"),
                        MedFilter.ACTIVE to (if (s.isThai) "กำลังทาน" else "Active"),
                        MedFilter.COMPLETED to (if (s.isThai) "จบรายการแล้ว" else "Completed")
                    )
                    labels.forEach { (f, label) ->
                        val selected = f == filter
                        Surface(onClick = { filter = f }, shape = RoundedCornerShape(100.dp), color = if (selected) Primary else MaterialTheme.colorScheme.surfaceVariant) {
                            Text(label, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                AIInsightCard(
                    text = state.medicationHistoryInsight
                        ?: if (s.isThai) "กำลังวิเคราะห์ประวัติยา..." else "Analyzing medication history...",
                    isLoading = state.medicationHistoryInsightLoading || state.medicationHistoryInsight == null
                )
            }

            if (loading) {
                item { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Primary, modifier = Modifier.size(28.dp), strokeWidth = 2.dp) } }
            } else if (byMonth.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { Text(if (s.isThai) "ไม่มีประวัติการใช้ยา" else "No medication history", color = Outline) } }
            } else {
                byMonth.forEach { (ym, monthSchedules) ->
                    item {
                        val (year, month) = ym
                        val label = if (year == 0) (if (s.isThai) "ไม่ทราบวันที่" else "Unknown date")
                        else {
                            val locale = if (s.isThai) Locale.forLanguageTag("th") else Locale.ENGLISH
                            val monthName = java.time.Month.of(month).getDisplayName(TextStyle.FULL, locale)
                            "$monthName $year"
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            HorizontalDivider(Modifier.weight(1f))
                            Text(label, style = MaterialTheme.typography.labelMedium, color = Outline, fontWeight = FontWeight.Bold)
                            HorizontalDivider(Modifier.weight(1f))
                        }
                    }
                    items(monthSchedules, key = { it.id }) { sch ->
                        val active = isActive(sch)
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Box(Modifier.size(44.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Filled.Medication, null, tint = Primary, modifier = Modifier.size(22.dp))
                                    }
                                    Column(Modifier.weight(1f)) {
                                        Text(sch.medicineName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                        Text(
                                            (sch.startDate ?: "-") + (sch.endDate?.let { " – $it" } ?: ""),
                                            style = MaterialTheme.typography.labelSmall, color = Outline
                                        )
                                    }
                                    Surface(shape = RoundedCornerShape(6.dp), color = if (active) Primary.copy(0.12f) else MaterialTheme.colorScheme.surfaceVariant) {
                                        Text(
                                            if (active) (if (s.isThai) "กำลังทาน" else "ACTIVE") else (if (s.isThai) "จบรายการแล้ว" else "DONE"),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                            color = if (active) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider()
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Filled.Science, null, tint = Outline, modifier = Modifier.size(16.dp))
                                        Text("${sch.dosage ?: "-"} ${sch.dosageUnit}", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Icon(Icons.Filled.Schedule, null, tint = Outline, modifier = Modifier.size(16.dp))
                                        Text(sch.frequency ?: "-", style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                sch.notes?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = Outline)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}
