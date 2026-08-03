@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

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
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.HealthRecordRules
import com.lullabyai.app.viewmodel.ChildViewModel

private enum class HealthFilter { RECENT, ALL_MONTHS, BY_TYPE, APPOINTMENTS }

/** "ประวัติสุขภาพ" — Stitch filters/search/export + AI insight + 1-month hide. */
@Composable
fun HealthHistoryScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(HealthFilter.RECENT) }
    var showOlder by remember { mutableStateOf(false) }

    LaunchedEffect(state.healthRecords.size, s.isThai) {
        childViewModel.loadHealthHistoryInsight(s.isThai)
    }

    val filtered = remember(state.healthRecords, query, filter) {
        state.healthRecords.filter { record ->
            val q = query.isBlank() ||
                record.title.contains(query, ignoreCase = true) ||
                record.description?.contains(query, ignoreCase = true) == true ||
                record.additionalSymptoms?.any { it.contains(query, ignoreCase = true) } == true
            val f = when (filter) {
                HealthFilter.RECENT -> !HealthRecordRules.isOlderThanOneMonth(record)
                HealthFilter.ALL_MONTHS -> true
                HealthFilter.BY_TYPE -> record.eventType != "vaccine" && record.eventType != "appointment"
                HealthFilter.APPOINTMENTS -> record.eventType == "vaccine" || record.eventType == "appointment"
            }
            q && f
        }
    }
    val recent = remember(filtered) { filtered.filterNot { HealthRecordRules.isOlderThanOneMonth(it) } }
    val older = remember(filtered) { filtered.filter { HealthRecordRules.isOlderThanOneMonth(it) } }
    val visible = when {
        filter == HealthFilter.RECENT -> filtered
        showOlder || filter == HealthFilter.ALL_MONTHS -> filtered
        else -> recent
    }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "ประวัติสุขภาพ" else "Health History",
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
            ExtendedFloatingActionButton(
                onClick = { navController.navigate(Screen.AddHealthRecord.route) },
                containerColor = Primary, contentColor = Color.White
            ) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp))
                Text(if (s.isThai) "บันทึกใหม่" else "New record", fontWeight = FontWeight.Bold)
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
                onConnect = { navController.navigate(Screen.MyDoctorsHub.route) { launchSingleTop = true } }
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
                    placeholder = { Text(if (s.isThai) "ค้นหาอาการหรือโรค..." else "Search symptoms or condition...", color = Outline) },
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
                    FilterPill(
                        icon = Icons.Filled.FilterList,
                        label = if (s.isThai) "ล่าสุด" else "Recent",
                        selected = filter == HealthFilter.RECENT
                    ) { filter = HealthFilter.RECENT; showOlder = false }
                    FilterPill(
                        label = if (s.isThai) "ทุกเดือน" else "All months",
                        selected = filter == HealthFilter.ALL_MONTHS
                    ) { filter = HealthFilter.ALL_MONTHS; showOlder = true }
                    FilterPill(
                        label = if (s.isThai) "ประเภทอาการ" else "Symptom type",
                        selected = filter == HealthFilter.BY_TYPE
                    ) { filter = HealthFilter.BY_TYPE }
                    FilterPill(
                        label = if (s.isThai) "นัดหมาย" else "Appointments",
                        selected = filter == HealthFilter.APPOINTMENTS
                    ) { filter = HealthFilter.APPOINTMENTS }
                }
            }

            item {
                AIInsightCard(
                    text = state.healthHistoryInsight
                        ?: if (s.isThai) "กำลังวิเคราะห์ประวัติสุขภาพ..." else "Analyzing health history...",
                    isLoading = state.healthHistoryInsightLoading || state.healthHistoryInsight == null
                )
            }

            if (visible.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (filtered.isEmpty()) {
                                if (s.isThai) "ไม่มีบันทึกสุขภาพ" else "No health records"
                            } else {
                                if (s.isThai) "ไม่มีบันทึกใน 1 เดือนล่าสุด" else "No records in the last month"
                            },
                            color = Outline
                        )
                    }
                }
            } else {
                items(visible, key = { it.id }) { record ->
                    val display = HealthRecordRules.displayStatus(
                        record, state.symptomActivityByRecord[record.id]
                    )
                    HealthHistoryCard(record, display, s.isThai) {
                        childViewModel.selectHealthRecord(record.id)
                        if (display == HealthRecordRules.DisplayStatus.MONITORING) {
                            navController.navigate(Screen.SicknessFollowUp.route)
                        } else {
                            navController.navigate(Screen.HealthDetail.route)
                        }
                    }
                }
            }

            if (filter != HealthFilter.RECENT && filter != HealthFilter.ALL_MONTHS && !showOlder && older.isNotEmpty()) {
                item {
                    TextButton(onClick = { showOlder = true }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            if (s.isThai) "ดูเพิ่มเติม (${older.size})" else "Show more (${older.size})",
                            color = Primary, fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Filled.ExpandMore, null, tint = Primary)
                    }
                }
            } else if (showOlder && older.isNotEmpty() && filter != HealthFilter.ALL_MONTHS) {
                item {
                    TextButton(onClick = { showOlder = false }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            if (s.isThai) "ซ่อนรายการเก่า" else "Hide older entries",
                            color = Outline, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(18.dp)) } },
        shape = RoundedCornerShape(100.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Primary,
            selectedLabelColor = Color.White,
            selectedLeadingIconColor = Color.White
        )
    )
}

@Composable
private fun HealthHistoryCard(
    record: HealthRecord,
    display: HealthRecordRules.DisplayStatus,
    isThai: Boolean,
    onOpenDetail: () -> Unit
) {
    val (barColor, badgeBg, badgeFg, badgeText) = when {
        record.eventType == "vaccine" -> Quad(Primary, Primary.copy(0.12f), Primary, if (isThai) "วัคซีน" else "VACCINE")
        display == HealthRecordRules.DisplayStatus.UNUSED ->
            Quad(Outline, Outline.copy(0.12f), Outline, if (isThai) "ไม่ใช้" else "UNUSED")
        display == HealthRecordRules.DisplayStatus.RECOVERED ->
            Quad(Color(0xFF2E7D32), Color(0xFFE8F5E9), Color(0xFF2E7D32), if (isThai) "หายแล้ว" else "RECOVERED")
        display == HealthRecordRules.DisplayStatus.AUTO_RESOLVED ->
            Quad(Color(0xFF558B2F), Color(0xFFF1F8E9), Color(0xFF558B2F), if (isThai) "หายแล้ว (อัตโนมัติ)" else "AUTO-RESOLVED")
        else -> Quad(Color(0xFFED6C02), Color(0xFFFFF3E0), Color(0xFFED6C02), if (isThai) "กำลังติดตาม" else "MONITORING")
    }
    val edited = HealthRecordRules.isEdited(record)
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(barColor))
            Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        com.lullabyai.app.util.TimeUtils.localDate(record.recordedAt) ?: "",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (edited) {
                            Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                                Text(
                                    if (isThai) "แก้ไขแล้ว" else "EDITED",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Primary
                                )
                            }
                        }
                        Surface(shape = RoundedCornerShape(100.dp), color = badgeBg) {
                            Text(badgeText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badgeFg)
                        }
                    }
                }
                Text(record.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                record.description?.takeIf { it.isNotBlank() }?.let {
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
}

private data class Quad(val a: Color, val b: Color, val c: Color, val d: String)
