@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.sleep

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddSleepTimeScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }

    LaunchedEffect(state.child?.id, state.sleepLogs.size) { state.child?.let { chatViewModel.loadSleepInsight(it, state.sleepLogs, s.isThai) } }

    var sleepType by remember { mutableStateOf("main") }
    var startTime by remember { mutableStateOf("20:00") }
    var endTime by remember { mutableStateOf("06:00") }

    val durationMinutes = remember(startTime, endTime) {
        fun toMin(t: String): Int? {
            val p = t.split(":")
            if (p.size != 2) return null
            val h = p[0].toIntOrNull() ?: return null
            val m = p[1].toIntOrNull() ?: return null
            return h * 60 + m
        }
        val s0 = toMin(startTime); val e0 = toMin(endTime)
        if (s0 != null && e0 != null) { val diff = e0 - s0; if (diff <= 0) diff + 24 * 60 else diff } else null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (s.isThai) "เพิ่มเวลาการนอน" else "Add Sleep Time", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Primary) } },
                actions = {
                    TextButton(
                        onClick = {
                            val startIso = "${today}T$startTime:00"
                            // Naps/night sleep that end the next morning cross midnight — advance the end date.
                            val endDate = if (endTime < startTime) {
                                val cal = Calendar.getInstance().apply {
                                    time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(today)!!
                                    add(Calendar.DAY_OF_MONTH, 1)
                                }
                                SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                            } else today
                            val endIso = "${endDate}T$endTime:00"
                            childViewModel.addManualSleepLog(sleepType, startIso, endIso)
                            onBack()
                        },
                        enabled = !state.sleepSaving
                    ) {
                        if (state.sleepSaving) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Primary)
                        else Text(if (s.isThai) "บันทึก" else "Save", color = Primary, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            Text(if (s.isThai) "ประเภทการนอน" else "Sleep Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SleepTypeButton(Icons.Filled.Bedtime, if (s.isThai) "เวลานอนหลัก" else "Main bedtime", sleepType == "main", Modifier.weight(1f)) { sleepType = "main" }
                SleepTypeButton(Icons.Filled.WbSunny, if (s.isThai) "นอนกลางวัน" else "Nap", sleepType == "nap", Modifier.weight(1f)) { sleepType = "nap" }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField(if (s.isThai) "เวลาเริ่ม" else "Start", startTime, Modifier.weight(1f)) { startTime = it }
                TimeField(if (s.isThai) "เวลาจบ" else "End", endTime, Modifier.weight(1f)) { endTime = it }
            }

            if (durationMinutes != null) {
                DurationRing(durationMinutes, s.isThai)
            }

            if (chatState.sleepInsight != null) {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
                    Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text(chatState.sleepInsight!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Circular visualization of the entered duration, capped visually at 16h (covers realistic
 *  nightly-sleep + nap durations) — purely computed from the two time fields, no backend. */
@Composable
private fun DurationRing(durationMinutes: Int, isThai: Boolean) {
    val maxMinutes = 16 * 60
    val fraction = (durationMinutes / maxMinutes.toFloat()).coerceIn(0f, 1f)
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(180.dp)) {
            val stroke = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round)
            drawArc(color = Color(0xFFE0E3E5), startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke, size = Size(size.width - stroke.width, size.height - stroke.width), topLeft = androidx.compose.ui.geometry.Offset(stroke.width / 2, stroke.width / 2))
            drawArc(color = Primary, startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, style = stroke, size = Size(size.width - stroke.width, size.height - stroke.width), topLeft = androidx.compose.ui.geometry.Offset(stroke.width / 2, stroke.width / 2))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isThai) "ระยะเวลาทั้งหมด" else "Total duration", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${durationMinutes / 60}${if (isThai) " ชม. " else "h "}${durationMinutes % 60}${if (isThai) " นาที" else "m"}",
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Primary
            )
        }
    }
}

@Composable
private fun SleepTypeButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = if (selected) Primary else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(vertical = 16.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, tint = if (selected) Color.White else Outline, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TimeField(label: String, value: String, modifier: Modifier = Modifier, onChange: (String) -> Unit) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("HH:mm", color = Outline) }, singleLine = true, shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
        )
    }
}
