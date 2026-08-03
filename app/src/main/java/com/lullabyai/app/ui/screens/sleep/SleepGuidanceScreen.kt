@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.sleep

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.sleep.SleepWakeWindows
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel

/**
 * Stitch "Sleep Schedule Creation (Interactive)" — MAIN's คำแนะนำ tile.
 *
 * Despite the Stitch name, the screen itself is the guidance page (`คำแนะนำการนอน`): AI environment
 * tips, the wake-window table, and the bedtime routine. The "(Interactive)" part is the
 * สร้างตารางการนอน sheet at the bottom of the design, which creates a schedule inline rather than
 * navigating away — so it is implemented here as a modal sheet.
 */
@Composable
fun SleepGuidanceScreen(
    childViewModel: ChildViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    var showCreateSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (s.isThai) "คำแนะนำการนอน" else "Sleep Guidance",
                onBack = onBack,
                navController = navController
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
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // Stitch hero copy
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (s.isThai) "เตรียมลูกน้อยให้พร้อมสู่นิทรา" else "Get your baby ready for sleep",
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                )
                Text(
                    if (s.isThai) "เทคนิคและคำแนะนำจาก AI เพื่อการพักผ่อนที่มีคุณภาพ"
                    else "AI techniques and tips for quality rest",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // "คำแนะนำพิเศษจาก AI" — environment tips
            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text(
                            if (s.isThai) "คำแนะนำพิเศษจาก AI" else "Special AI guidance",
                            style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold
                        )
                    }
                    TipRow(
                        Icons.Filled.Thermostat,
                        if (s.isThai) "อุณหภูมิที่เหมาะสม" else "Ideal temperature",
                        "24-26°C"
                    )
                    TipRow(
                        Icons.Filled.LightMode,
                        if (s.isThai) "การปรับแสง" else "Lighting",
                        if (s.isThai) "หรี่ไฟลง 30 นาทีก่อนนอน" else "Dim lights 30 minutes before bed"
                    )
                }
            }

            // "หน้าต่างแห่งการตื่น (Wake Windows)"
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (s.isThai) "หน้าต่างแห่งการตื่น (Wake Windows)" else "Wake Windows",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
                Text(
                    if (s.isThai) "ระยะเวลาที่ลูกน้อยควรตื่นอยู่ระหว่างการงีบหลับ เพื่อป้องกันไม่ให้เหนื่อยจนเกินไป"
                    else "How long your baby should stay awake between naps, to avoid overtiredness",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Card(
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(Modifier.padding(4.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                if (s.isThai) "อายุ" else "Age",
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (s.isThai) "เวลาตื่นที่แนะนำ" else "Recommended wake time",
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        SleepWakeWindows.ALL.forEachIndexed { i, w ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(if (s.isThai) w.ageRangeTh else w.ageRangeEn, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(if (s.isThai) w.windowTh else w.windowEn, style = MaterialTheme.typography.bodyMedium, color = Primary, fontWeight = FontWeight.Bold)
                            }
                            if (i < SleepWakeWindows.ALL.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                        }
                    }
                }
            }

            // "กิจวัตรการนอน (Bedtime Routine)" — numbered steps, each with the design's icon
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (s.isThai) "กิจวัตรการนอน (Bedtime Routine)" else "Bedtime Routine",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
                RoutineStep(1, Icons.Filled.Bathtub, if (s.isThai) "ผ่อนคลายร่างกาย" else "Calm the body", if (s.isThai) "อาบน้ำอุ่นหรือเช็ดตัว" else "Warm bath or a gentle wipe-down")
                RoutineStep(2, Icons.Filled.MenuBook, if (s.isThai) "กระตุ้นพัฒนาการ" else "Wind-down activity", if (s.isThai) "อ่านหนังสือนิทานร่วมกัน" else "Read a story together")
                RoutineStep(3, Icons.Filled.MusicNote, if (s.isThai) "กล่อมเข้านอน" else "Settle to sleep", if (s.isThai) "เปิดเพลงคลอเบาๆ หรือร้องเพลง" else "Soft music or singing")
            }

            Button(
                onClick = { showCreateSheet = true },
                modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Filled.CalendarMonth, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (s.isThai) "สร้างตารางการนอน" else "Create sleep schedule",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showCreateSheet) {
        CreateScheduleSheet(
            isThai = s.isThai,
            saving = state.sleepSaving,
            onDismiss = { showCreateSheet = false },
            onSave = { sleepType, start, end, days ->
                val label = if (sleepType == "nap") {
                    if (s.isThai) "นอนกลางวัน" else "Nap"
                } else {
                    if (s.isThai) "เวลานอนหลัก" else "Main bedtime"
                }
                childViewModel.addSleepSchedule(label, sleepType, start, end, days) { showCreateSheet = false }
            }
        )
    }
}

/**
 * Stitch's สร้างตารางการนอน sheet: bedtime, wake time, weekday repeat, บันทึกตาราง.
 *
 * Writes the same `sleep_schedules` row as [AddSleepTimeScreen] — this is the design's shortcut
 * from the guidance page, not a second kind of record.
 */
@Composable
private fun CreateScheduleSheet(
    isThai: Boolean,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (sleepType: String, start: String, end: String, days: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sleepType by remember { mutableStateOf("main") }
    var start by remember { mutableStateOf("20:00") }
    var end by remember { mutableStateOf("06:00") }
    var days by remember { mutableStateOf(DAY_CODES_GUIDANCE.toSet()) }
    val valid = minutesBetween(start, end) != null && days.isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (isThai) "สร้างตารางการนอน" else "Create sleep schedule",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null) }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    "main" to (if (isThai) "เวลานอนหลัก" else "Main bedtime"),
                    "nap" to (if (isThai) "นอนกลางวัน" else "Nap")
                ).forEach { (key, lbl) ->
                    val sel = sleepType == key
                    Surface(
                        onClick = { sleepType = key }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                        color = if (sel) Primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            lbl, modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                            color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = start, onValueChange = { start = it }, modifier = Modifier.weight(1f),
                    label = { Text(if (isThai) "เวลานอน" else "Bedtime") }, placeholder = { Text("HH:mm") },
                    singleLine = true, shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = end, onValueChange = { end = it }, modifier = Modifier.weight(1f),
                    label = { Text(if (isThai) "เวลาตื่น" else "Wake up") }, placeholder = { Text("HH:mm") },
                    singleLine = true, shape = RoundedCornerShape(12.dp)
                )
            }

            Text(
                if (isThai) "ทำซ้ำทุกวัน" else "Repeat daily",
                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                val labels = if (isThai) listOf("จ", "อ", "พ", "พฤ", "ศ", "ส", "อา") else listOf("M", "T", "W", "T", "F", "S", "S")
                DAY_CODES_GUIDANCE.forEachIndexed { i, code ->
                    val on = code in days
                    Surface(
                        onClick = { days = if (on) days - code else days + code },
                        modifier = Modifier.weight(1f).height(42.dp).clip(CircleShape), shape = CircleShape,
                        color = if (on) Primary else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                labels[i], style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val encoded = if (days.size == DAY_CODES_GUIDANCE.size) "ALL"
                    else DAY_CODES_GUIDANCE.filter { it in days }.joinToString(",")
                    onSave(sleepType, start, end, encoded)
                },
                enabled = valid && !saving,
                modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                if (saving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else Text(
                    if (isThai) "บันทึกตาราง" else "Save schedule",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White
                )
            }
        }
    }
}

private val DAY_CODES_GUIDANCE = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

@Composable
private fun TipRow(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RoutineStep(number: Int, icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
            Text("$number", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.size(38.dp).clip(CircleShape).background(SecondaryFixed.copy(0.55f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Primary, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
