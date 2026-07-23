@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.child

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
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
import com.lullabyai.app.data.model.DevelopmentMilestone
import com.lullabyai.app.ui.components.AppBottomNav
import com.lullabyai.app.ui.components.BottomTab
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

private fun ageGroup(months: Int?, isThai: Boolean): Pair<Int, String> = when {
    months == null -> 9 to (if (isThai) "อื่นๆ" else "Other")
    months <= 3 -> 0 to (if (isThai) "0-3 เดือน" else "0-3 mo")
    months <= 6 -> 1 to (if (isThai) "4-6 เดือน" else "4-6 mo")
    months <= 12 -> 2 to (if (isThai) "7-12 เดือน" else "7-12 mo")
    months <= 23 -> 3 to (if (isThai) "1 ปี" else "1 yr")
    months <= 35 -> 4 to (if (isThai) "2 ปี" else "2 yr")
    else -> 5 to (if (isThai) "3 ปี" else "3 yr")
}

@Composable
fun DevelopmentHistoryScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val childState by childViewModel.uiState.collectAsState()
    val milestones = childState.milestones
    var query by remember { mutableStateOf("") }
    var skipTarget by remember { mutableStateOf<DevelopmentMilestone?>(null) }

    val filtered = milestones.filter { query.isBlank() || it.milestoneName.contains(query, ignoreCase = true) }
    val groups = filtered.groupBy { ageGroup(it.ageMonthsExpected, s.isThai) }.toSortedMap(compareBy { it.first })
    val totalAchieved = milestones.count { it.isAchieved }
    val total = milestones.size.coerceAtLeast(1)
    val percent = totalAchieved * 100 / total

    // Expanded groups: default-expand the first in-progress group
    val firstInProgress = groups.entries.firstOrNull { e -> e.value.any { !it.isAchieved } && e.value.any { it.isAchieved } }?.key
        ?: groups.keys.firstOrNull()
    var expanded by remember(milestones) { mutableStateOf(setOf(firstInProgress)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "ประวัติพัฒนาการ" else "Development History", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) { Icon(Icons.Filled.Notifications, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            AppBottomNav(
                selected = BottomTab.CHILD,
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Search
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(if (s.isThai) "ค้นหาทักษะหรือกิจกรรม..." else "Search skills or activities...", color = Outline) },
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                    singleLine = true, shape = RoundedCornerShape(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                )
            }
            // Overview
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
                    Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (s.isThai) "ภาพรวมทักษะ" else "Skills overview", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(if (s.isThai) "ลูกทำสำเร็จแล้ว $totalAchieved จาก $total กิจกรรม" else "$totalAchieved of $total activities completed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(shape = RoundedCornerShape(100.dp), color = Color(0xFF4CAF50).copy(0.12f)) {
                                Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Filled.TrendingUp, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                                    Text(if (s.isThai) "ดีเยี่ยม" else "Excellent", style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
                            CircularProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxSize(), color = Primary, trackColor = MaterialTheme.colorScheme.surfaceVariant, strokeWidth = 8.dp)
                            Text("$percent%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            // Age-group accordions
            groups.forEach { (group, items) ->
                item(key = "g${group.first}") {
                    val achieved = items.count { it.isAchieved }
                    val isExpanded = expanded.contains(group)
                    AgeGroupCard(
                        title = group.second,
                        achieved = achieved,
                        total = items.size,
                        expanded = isExpanded,
                        onToggle = { expanded = if (isExpanded) expanded - group else expanded + group },
                        milestones = items,
                        onRecord = { childViewModel.setMilestoneAchieved(it.id, true) },
                        onSkip = { skipTarget = it },
                        onUnskip = { childViewModel.setMilestoneSkipReason(it.id, null) }
                    )
                }
            }
            // Tip card
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Primary)) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (s.isThai) "เคล็ดลับเสริมพัฒนาการ" else "Development tip", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(if (s.isThai) "เล่นจ๊ะเอ๋กับลูกช่วยเสริมความเข้าใจเรื่องความคงอยู่ของวัตถุ และกระชับความสัมพันธ์ทางอารมณ์" else "Playing peekaboo helps your child grasp object permanence and strengthens emotional bonding", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.85f))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Skip reason popup
    skipTarget?.let { target ->
        SkipReasonDialog(
            milestoneName = target.milestoneName,
            onDismiss = { skipTarget = null },
            onConfirm = { reason -> childViewModel.setMilestoneSkipReason(target.id, reason); skipTarget = null }
        )
    }
}

@Composable
private fun AgeGroupCard(
    title: String, achieved: Int, total: Int, expanded: Boolean,
    onToggle: () -> Unit, milestones: List<DevelopmentMilestone>,
    onRecord: (DevelopmentMilestone) -> Unit, onSkip: (DevelopmentMilestone) -> Unit,
    onUnskip: (DevelopmentMilestone) -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val (statusText, statusColor) = when {
        achieved >= total && total > 0 -> ((if (s.isThai) "เสร็จสิ้น " else "Done ") + "$achieved/$total") to Color(0xFF2E7D32)
        achieved > 0 -> ((if (s.isThai) "กำลังดำเนินการ " else "In progress ") + "$achieved/$total") to Primary
        else -> ((if (s.isThai) "รอดำเนินการ " else "Pending ") + "$achieved/$total") to Outline
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp),
        border = if (expanded) androidx.compose.foundation.BorderStroke(1.5.dp, Primary.copy(0.4f)) else null
    ) {
        Column {
            Surface(onClick = onToggle, color = Color.Transparent) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.ChildCare, null, tint = Primary, modifier = Modifier.size(24.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(statusText, style = MaterialTheme.typography.labelSmall, color = statusColor)
                    }
                    Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Outline)
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    milestones.forEach { m -> MilestoneHistoryRow(m, onRecord = { onRecord(m) }, onSkip = { onSkip(m) }, onUnskip = { onUnskip(m) }) }
                }
            }
        }
    }
}

@Composable
private fun MilestoneHistoryRow(m: DevelopmentMilestone, onRecord: () -> Unit, onSkip: () -> Unit, onUnskip: () -> Unit) {
    val isSkipped = m.skipReason != null && !m.isAchieved
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = if (isSkipped) MaterialTheme.colorScheme.surfaceVariant.copy(0.5f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.3f))) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                if (m.isAchieved) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                null, tint = if (m.isAchieved) Primary else Outline, modifier = Modifier.size(22.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(m.milestoneName, style = MaterialTheme.typography.bodyMedium, color = if (isSkipped) Outline else MaterialTheme.colorScheme.onSurface)
                Text(catLabel(m.category, com.lullabyai.app.ui.localization.LocalStrings.current.isThai), style = MaterialTheme.typography.labelSmall, color = Outline)
                if (isSkipped) Text((if (com.lullabyai.app.ui.localization.LocalStrings.current.isThai) "ข้าม: " else "Skipped: ") + m.skipReason, style = MaterialTheme.typography.labelSmall, color = Color(0xFFFF9800))
            }
            if (!m.isAchieved) {
                val isThai = com.lullabyai.app.ui.localization.LocalStrings.current.isThai
                if (isSkipped) {
                    // skipped → allow undo (kids develop at different rates)
                    OutlinedButton(onClick = onUnskip, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(100.dp)) {
                        Text(if (isThai) "ยกเลิกการข้าม" else "Undo skip", style = MaterialTheme.typography.labelSmall, color = Primary)
                    }
                } else {
                    OutlinedButton(onClick = onSkip, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), shape = RoundedCornerShape(100.dp)) {
                        Text(if (isThai) "ข้าม" else "Skip", style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                }
                Button(onClick = onRecord, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp), shape = RoundedCornerShape(100.dp), colors = ButtonDefaults.buttonColors(containerColor = Primary)) {
                    Text(if (isThai) "บันทึก" else "Mark done", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SkipReasonDialog(milestoneName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    var selected by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (s.isThai) "ระบุเหตุผลที่ข้าม" else "Reason for skipping", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(milestoneName, style = MaterialTheme.typography.bodySmall, color = Outline)
                skipReasons(s.isThai).forEach { reason ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).selectable(selected = selected == reason, onClick = { selected = reason }).padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(selected = selected == reason, onClick = { selected = reason }, colors = RadioButtonDefaults.colors(selectedColor = Primary))
                        Text(reason, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { if (selected.isNotBlank()) onConfirm(selected) }, enabled = selected.isNotBlank()) { Text(if (s.isThai) "ยืนยัน" else "Confirm", color = Primary, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (s.isThai) "ยกเลิก" else "Cancel") } }
    )
}
