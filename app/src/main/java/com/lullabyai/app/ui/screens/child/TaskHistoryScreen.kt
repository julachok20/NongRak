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
import com.lullabyai.app.data.model.AiTask
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

@Composable
fun TaskHistoryScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val childState by childViewModel.uiState.collectAsState()
    val active = childState.aiTasks
    var history by remember { mutableStateOf<List<AiTask>>(emptyList()) }

    LaunchedEffect(Unit) {
        childState.child?.let { childViewModel.loadTasks(it) }
        childViewModel.loadTaskHistory { history = it }
    }

    val completedCount = history.size + active.count { it.isCompleted }
    val total = (history.size + active.size).coerceAtLeast(1)
    val progress = completedCount.toFloat() / total

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("กิจกรรมทั้งหมด", style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = { ChildBottomNav(navController = navController, currentTab = "child") },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // Overall progress card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = PrimaryContainer)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("พัฒนาการโดยรวม", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "ลูกน้อยของคุณทำกิจกรรมสำเร็จไปแล้ว $completedCount จาก $total รายการ",
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.9f)
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(100.dp)),
                            color = Color.White,
                            trackColor = Color.White.copy(0.3f)
                        )
                    }
                }
            }

            // Current activities
            item { Text("กิจกรรมปัจจุบัน", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(active) { task ->
                CurrentActivityRow(
                    text = task.taskText,
                    completed = task.isCompleted,
                    onToggle = { childViewModel.toggleTask(task.id, task.isCompleted) },
                    onNotAvailable = { childViewModel.notAvailableTask(task.id) }
                )
            }

            // Completed activities
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("กิจกรรมที่สำเร็จแล้ว", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    FilterPill("ประเภท: ทั้งหมด")
                    Spacer(Modifier.width(6.dp))
                    FilterPill("อายุ: ทั้งหมด")
                }
            }

            if (history.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text("ยังไม่มีกิจกรรมที่สำเร็จ", color = Outline, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                items(history) { task -> CompletedActivityRow(task) }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun FilterPill(text: String) {
    Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Filled.ExpandMore, null, tint = Outline, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun CurrentActivityRow(text: String, completed: Boolean, onToggle: () -> Unit, onNotAvailable: () -> Unit) {
    val parts = text.split("|").map { it.trim() }
    val title = parts.getOrNull(0) ?: text
    val goal = parts.getOrNull(1)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Primary.copy(0.1f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Filled.Star, null, tint = Primary, modifier = Modifier.size(22.dp)) }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                if (goal != null) Text("เป้าหมาย: $goal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(
                onClick = onNotAvailable,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
            ) { Icon(Icons.Filled.NotInterested, "ไม่พร้อมทำ", tint = Outline, modifier = Modifier.size(20.dp)) }
            IconButton(
                onClick = onToggle,
                modifier = Modifier.size(40.dp).clip(CircleShape).background(if (completed) Primary else MaterialTheme.colorScheme.surfaceVariant)
            ) { Icon(Icons.Filled.Check, "เสร็จ", tint = if (completed) Color.White else Outline, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun CompletedActivityRow(task: AiTask) {
    val parts = task.taskText.split("|").map { it.trim() }
    val title = parts.getOrNull(0) ?: task.taskText
    val goal = parts.getOrNull(1)
    val date = com.lullabyai.app.util.TimeUtils.localDate(task.completedAt) ?: ""
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(Color(0xFF4CAF50)))
            Column(modifier = Modifier.padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(date, style = MaterialTheme.typography.labelSmall, color = Outline)
                }
                if (goal != null) Text(goal, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
