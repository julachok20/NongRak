package com.lullabyai.app.ui.screens.growth

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.GrowthRecord
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel

@Composable
fun GrowthMetricsScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController,
    onBack: () -> Unit,
    onNavigateToNotifications: () -> Unit = {}
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val childState by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = childState.child
    val growthRecords = childState.growthRecords
    var showAddDialog by remember { mutableStateOf(false) }
    // No permanent dismissal by design — reappears every time this screen loads until weight/height
    // are actually filled in, since those two fields are exactly what the Growth AI insight reads.
    var showMissingInfo by remember { mutableStateOf(true) }
    val missingFields = remember(child) { com.lullabyai.app.ui.components.missingChildInfo(child, hasAllergyRecords = true, isThai = s.isThai) }

    LaunchedEffect(child) {
        child?.let { chatViewModel.loadGrowthInsight(it, s.isThai) }
    }

    Scaffold(
        topBar = {
            LullabyTopBar(title = "Child Health", onBack = onBack, actions = {
                IconButton(onClick = onNavigateToNotifications) {
                    Icon(Icons.Filled.Notifications, null)
                }
            })
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Weight card
            item {
                GrowthCard(
                    title = "WEIGHT",
                    value = child?.weightKg?.toString() ?: "-",
                    unit = "kg",
                    trend = "+0.4kg",
                    trendPositive = true,
                    barLabel = "Normal growth curve",
                    statusLabel = "Healthy",
                    records = growthRecords.take(6),
                    isWeight = true
                )
            }

            // Height card
            item {
                GrowthCard(
                    title = "HEIGHT",
                    value = child?.heightCm?.toString() ?: "-",
                    unit = "cm",
                    trend = "",
                    trendPositive = true,
                    barLabel = "Normal growth curve",
                    statusLabel = "Healthy",
                    records = growthRecords.take(6),
                    isWeight = false
                )
            }

            // Recent logs
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent Logs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }

            if (growthRecords.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("ยังไม่มีบันทึกการเจริญเติบโต", color = Outline)
                        Button(onClick = { showAddDialog = true }, shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                            Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("เริ่มบันทึกข้อมูล", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                items(growthRecords.take(5)) { record ->
                    GrowthLogRow(record = record)
                }
            }

            item {
                TextButton(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                    Text("View All History", color = Primary)
                }
            }

            // AI Insight
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(20.dp))
                            Text("AI Growth Insight", style = MaterialTheme.typography.labelLarge, color = Primary, fontWeight = FontWeight.Bold)
                        }
                        if (chatState.growthInsight != null) {
                            Text(chatState.growthInsight!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                        } else {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Primary)
                        }
                        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(100.dp)) {
                            Text("See Nutrition Advice", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    // showAddDialog existed as dead state (declared, never read) before this fix — the empty
    // state's CTA had nowhere to route to, so this dialog is the actual missing add-record flow.
    if (showAddDialog) {
        var weightInput by remember { mutableStateOf("") }
        var heightInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("บันทึกการเจริญเติบโต") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = weightInput, onValueChange = { weightInput = it },
                        label = { Text("น้ำหนัก (kg)") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = heightInput, onValueChange = { heightInput = it },
                        label = { Text("ส่วนสูง (cm)") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                val w = weightInput.toDoubleOrNull()
                val h = heightInput.toDoubleOrNull()
                TextButton(
                    enabled = w != null || h != null,
                    onClick = {
                        childViewModel.addGrowthRecord(
                            GrowthRecord(
                                weightKg = w, heightCm = h,
                                recordedAt = java.time.Instant.now().toString()
                            )
                        )
                        showAddDialog = false
                    }
                ) { Text("บันทึก") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("ยกเลิก") } }
        )
    }

    if (showMissingInfo) {
        com.lullabyai.app.ui.components.MissingChildInfoPrompt(
            missingFields = missingFields, isThai = s.isThai,
            onFillIn = { showMissingInfo = false; showAddDialog = true },
            onSkip = { showMissingInfo = false }
        )
    }
}

@Composable
private fun GrowthCard(
    title: String, value: String, unit: String, trend: String, trendPositive: Boolean,
    barLabel: String, statusLabel: String, records: List<GrowthRecord>, isWeight: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = Outline, fontWeight = FontWeight.Bold)
                if (trend.isNotBlank()) {
                    Surface(shape = RoundedCornerShape(100.dp), color = if (trendPositive) Color(0xFF4CAF50).copy(0.1f) else Error.copy(0.1f)) {
                        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (trendPositive) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown, null, tint = if (trendPositive) Color(0xFF4CAF50) else Error, modifier = Modifier.size(12.dp))
                            Text(" $trend", style = MaterialTheme.typography.labelSmall, color = if (trendPositive) Color(0xFF4CAF50) else Error)
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(unit, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }

            // Mini bar chart
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.height(50.dp).fillMaxWidth()
            ) {
                val maxVal = if (isWeight) (records.mapNotNull { it.weightKg }.maxOrNull() ?: 15.0) else (records.mapNotNull { it.heightCm }.maxOrNull() ?: 90.0)
                records.takeLast(6).forEach { record ->
                    val rawVal = if (isWeight) record.weightKg else record.heightCm
                    val progress = ((rawVal ?: 0.0) / maxVal).toFloat().coerceIn(0.1f, 1f)
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(progress).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(Primary.copy(alpha = 0.5f + 0.5f * progress))
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(barLabel, style = MaterialTheme.typography.labelSmall, color = Outline)
                Surface(shape = RoundedCornerShape(100.dp), color = Color(0xFF4CAF50).copy(0.1f)) {
                    Text(statusLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32))
                }
            }
        }
    }
}

@Composable
private fun GrowthLogRow(record: GrowthRecord) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val date = com.lullabyai.app.util.TimeUtils.localDate(record.recordedAt) ?: ""
                val parts = date.split("-")
                if (parts.size >= 2) {
                    Text(parts[2].take(2), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Primary)
                    Text(when(parts[1]) { "01" -> "ม.ค." "02" -> "ก.พ." "03" -> "มี.ค." "04" -> "เม.ย." "05" -> "พ.ค." "06" -> "มิ.ย." "07" -> "ก.ค." "08" -> "ส.ค." "09" -> "ก.ย." "10" -> "ต.ค." "11" -> "พ.ย." else -> "ธ.ค." }, style = MaterialTheme.typography.labelSmall, color = Outline)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(record.recordType, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text("${record.weightKg ?: "-"} กก • ${record.heightCm ?: "-"} ซม.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Outline)
        }
    }
}
