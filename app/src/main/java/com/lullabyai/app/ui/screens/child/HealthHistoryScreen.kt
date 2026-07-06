@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.HealthRecord
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

/** "ประวัติสุขภาพ" — a dedicated full timeline of health records (ChildHealthScreen only ever
 *  shows a short "recent" slice inline). Reuses ChildViewModel.uiState.healthRecords, already
 *  ordered recorded_at DESC, created_at DESC — no new data loading needed. */
@Composable
fun HealthHistoryScreen(childViewModel: ChildViewModel, navController: NavController, onBack: () -> Unit) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    var query by remember { mutableStateOf("") }

    val filtered = remember(state.healthRecords, query) {
        if (query.isBlank()) state.healthRecords
        else state.healthRecords.filter { it.title.contains(query, ignoreCase = true) || it.description?.contains(query, ignoreCase = true) == true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "ประวัติสุขภาพ" else "Health History", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
                onSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }
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

            if (filtered.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) { Text(if (s.isThai) "ไม่มีบันทึกสุขภาพ" else "No health records", color = Outline) } }
            } else {
                items(filtered, key = { it.id }) { record ->
                    HealthHistoryCard(record, s.isThai) {
                        childViewModel.selectHealthRecord(record.id)
                        navController.navigate(Screen.HealthDetail.route)
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthHistoryCard(record: HealthRecord, isThai: Boolean, onOpenDetail: () -> Unit) {
    val (barColor, badgeBg, badgeFg, badgeText) = when {
        record.eventType == "vaccine" -> Quad(Primary, Primary.copy(0.12f), Primary, if (isThai) "วัคซีน" else "VACCINE")
        record.status == "recovered" -> Quad(Color(0xFF2E7D32), Color(0xFFE8F5E9), Color(0xFF2E7D32), if (isThai) "หายแล้ว" else "RECOVERED")
        else -> Quad(Color(0xFFED6C02), Color(0xFFFFF3E0), Color(0xFFED6C02), if (isThai) "กำลังติดตาม" else "MONITORING")
    }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(barColor))
            Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        com.lullabyai.app.util.TimeUtils.localDate(record.recordedAt) ?: "",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(shape = RoundedCornerShape(100.dp), color = badgeBg) {
                        Text(badgeText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badgeFg)
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
