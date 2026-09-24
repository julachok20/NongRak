@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.MilkLog
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.NutritionViewModel
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val QUICK_PICKS_ML = listOf(120, 150, 180, 210)
private val QUICK_PICKS_WATER_ML = listOf(30, 60, 90, 120)
private val QUICK_PICKS_MIN = listOf(5, 10, 15, 20)

/** Milk feeding log — Stitch milk_record: type, volume, side, time/date override, notes + history. */
@Composable
fun MilkRecordScreen(childViewModel: ChildViewModel, nutritionViewModel: NutritionViewModel, lang: String, navController: NavController, onBack: () -> Unit) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val childState by childViewModel.uiState.collectAsState()
    val childId = childState.child?.id
    val repo = remember { NutritionRepository() }
    val scope = rememberCoroutineScope()

    var logs by remember { mutableStateOf<List<MilkLog>>(emptyList()) }
    var milkType by remember { mutableStateOf("breast") }
    var volumeMl by remember { mutableStateOf(150) }
    var unit by remember { mutableStateOf("ml") }
    var side by remember { mutableStateOf("both") }
    // A feed at the breast has no measurable volume, so it is logged in minutes instead.
    var direct by remember { mutableStateOf(true) }
    var minutes by remember { mutableStateOf(15) }
    var saveError by remember { mutableStateOf<String?>(null) }
    val byMinutes = milkType == "breast" && direct
    var notes by remember { mutableStateOf("") }
    var feedDate by remember { mutableStateOf(LocalDate.now()) }
    var feedTime by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val mlPerOz = 29.5735f

    fun reload() { childId?.let { cid -> scope.launch { logs = repo.getMilkLogs(cid) } } }
    LaunchedEffect(childId) { reload() }

    // Water is offered from 6 months: before that breast milk or formula covers a baby's fluid needs.
    val canLogWater = nutritionViewModel.ageMonths(childState.child) >= 6
    val isWater = milkType == MilkLog.WATER
    val last = logs.firstOrNull { !it.isWater }
    val lastSummary = last?.let { log ->
        val t = com.lullabyai.app.util.TimeUtils.localTime(log.loggedAt) ?: ""
        val type = if (log.milkType == "breast") (if (isThai) "นมแม่" else "Breast milk") else (if (isThai) "นมผง" else "Formula")
        if (isThai) "มื้อล่าสุด: $t น. ($type)" else "Last feed: $t ($type)"
    } ?: if (isThai) "ยังไม่มีมื้อล่าสุด" else "No feeds logged yet"

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "บันทึกการดื่มนม" else "Milk Record", onBack = onBack, navController = navController) },
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
            verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Primary.copy(0.12f))
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.Bottom) {
                        Text(if (isThai) "ติดตามมื้ออาหารวันนี้" else "Track today's feeds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
                        Text(lastSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(if (isThai) "ประเภท" else "Type", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(
                            shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(Modifier.padding(4.dp)) {
                                listOf(
                                    "breast" to Pair(Icons.Filled.Favorite, if (isThai) "นมแม่" else "Breast milk"),
                                    "formula" to Pair(Icons.Filled.BabyChangingStation, if (isThai) "นมผง" else "Formula")
                                ).plus(if (canLogWater) listOf(MilkLog.WATER to Pair(Icons.Filled.WaterDrop, if (isThai) "น้ำต้มสุก" else "Water")) else emptyList())
                                .forEach { (key, pair) ->
                                    val (icon, label) = pair
                                    val selected = milkType == key
                                    Surface(
                                        onClick = { milkType = key }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp),
                                        color = if (selected) Primary else Color.Transparent
                                    ) {
                                        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                            Icon(icon, null, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        if (milkType == "breast") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(true to (if (isThai) "เข้าเต้า" else "At the breast"), false to (if (isThai) "ปั๊มใส่ขวด" else "Pumped, bottle")).forEach { (key, label) ->
                                    FilterChip(
                                        selected = direct == key, onClick = { direct = key },
                                        label = { Text(label, fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(0.15f), selectedLabelColor = Primary)
                                    )
                                }
                            }
                        }

                        if (byMinutes) {
                            Text(if (isThai) "ระยะเวลาที่ดูดนม" else "Feeding time", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$minutes", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Primary)
                                Text(if (isThai) "นาที" else "min", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Slider(value = minutes.toFloat(), onValueChange = { minutes = it.toInt() }, valueRange = 1f..60f, colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    QUICK_PICKS_MIN.forEach { m ->
                                        Surface(onClick = { minutes = m }, shape = RoundedCornerShape(10.dp), color = if (minutes == m) Primary.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant) {
                                            Text("$m ${if (isThai) "นาที" else "min"}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (minutes == m) Primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isWater) (if (isThai) "ปริมาณน้ำ" else "Water") else (if (isThai) "ปริมาณน้ำนม" else "Volume"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Row {
                                    listOf("ml" to (if (isThai) "มล." else "ml"), "oz" to (if (isThai) "ออนซ์" else "oz")).forEach { (key, label) ->
                                        val selected = unit == key
                                        Surface(onClick = { unit = key }, shape = RoundedCornerShape(6.dp), color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent) {
                                            Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (selected) Primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (unit == "ml") "$volumeMl" else "%.1f".format(volumeMl / mlPerOz),
                                style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = Primary
                            )
                            Text(if (unit == "ml") (if (isThai) "มล." else "ml") else (if (isThai) "ออนซ์" else "oz"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (unit == "ml") {
                                Slider(value = volumeMl.toFloat(), onValueChange = { volumeMl = it.toInt() }, valueRange = 30f..300f, steps = 26, colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    (if (isWater) QUICK_PICKS_WATER_ML else QUICK_PICKS_ML).forEach { ml ->
                                        Surface(onClick = { volumeMl = ml }, shape = RoundedCornerShape(10.dp), color = if (volumeMl == ml) Primary.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant) {
                                            Text("${ml} ${if (isThai) "มล." else "ml"}", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (volumeMl == ml) Primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            } else {
                                Slider(
                                    value = volumeMl / mlPerOz, onValueChange = { volumeMl = (it * mlPerOz).toInt() },
                                    valueRange = 1f..10f, steps = 17, colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(4, 5, 6, 7).forEach { oz ->
                                        val ozMl = (oz * mlPerOz).toInt()
                                        Surface(onClick = { volumeMl = ozMl }, shape = RoundedCornerShape(10.dp), color = if (volumeMl == ozMl) Primary.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant) {
                                            Text("${oz}oz", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (volumeMl == ozMl) Primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        }

                        if (byMinutes) {
                            Text(if (isThai) "ข้างที่ดื่ม (สำหรับนมแม่)" else "Side (breastfeeding)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    Triple("left", Icons.AutoMirrored.Filled.KeyboardArrowLeft, if (isThai) "ข้างซ้าย" else "Left"),
                                    Triple("right", Icons.AutoMirrored.Filled.KeyboardArrowRight, if (isThai) "ข้างขวา" else "Right"),
                                    Triple("both", Icons.Filled.SwapHoriz, if (isThai) "ทั้งสองข้าง" else "Both")
                                ).forEach { (key, icon, label) ->
                                    val selected = side == key
                                    Surface(
                                        onClick = { side = key }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                                        color = if (selected) Primary else MaterialTheme.colorScheme.surface,
                                        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(Modifier.padding(vertical = 12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(icon, null, tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                            Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                onClick = { showTimePicker = true }, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(if (isThai) "เวลา" else "Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(feedTime.format(DateTimeFormatter.ofPattern("HH:mm")) + if (isThai) " น." else "", style = MaterialTheme.typography.bodyMedium)
                                        Icon(Icons.Filled.Schedule, null, tint = Primary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Surface(
                                onClick = { showDatePicker = true }, modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(if (isThai) "วันที่" else "Date", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            if (feedDate == LocalDate.now()) (if (isThai) "วันนี้" else "Today") else feedDate.toString(),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Icon(Icons.Filled.CalendarToday, null, tint = Primary, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        Text(if (isThai) "บันทึกเพิ่มเติม" else "Notes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = notes, onValueChange = { notes = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                            placeholder = { Text(if (isThai) "ตัวอย่าง: น้องดื่มได้เยอะเป็นพิเศษ..." else "e.g. drank well, fell asleep mid-feed...", color = Outline) },
                            trailingIcon = { Icon(Icons.Filled.EditNote, null, tint = Outline) },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary)
                        )

                        Button(
                            onClick = {
                                val cid = childId ?: return@Button
                                saving = true
                                saveError = null
                                val loggedAt = LocalDateTime.of(feedDate, feedTime)
                                    .atZone(ZoneId.systemDefault()).toInstant().toString()
                                scope.launch {
                                    repo.addMilkLog(
                                        cid, milkType, if (byMinutes) 0 else volumeMl,
                                        if (byMinutes) side else null,
                                        notes.takeIf { it.isNotBlank() },
                                        loggedAt,
                                        durationMin = if (byMinutes) minutes else null
                                    ).onSuccess {
                                        notes = ""
                                        reload()
                                        nutritionViewModel.load(lang)
                                    }.onFailure { e ->
                                        saveError = (if (isThai) "บันทึกไม่สำเร็จ: " else "Could not save: ") + (e.message ?: e::class.simpleName)
                                    }
                                    saving = false
                                }
                            },
                            enabled = !saving && childId != null,
                            modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) { Text(if (isThai) "บันทึกข้อมูล" else "Save", fontWeight = FontWeight.Bold) }
                        saveError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            item { Text(if (isThai) "ประวัติการดื่มนม" else "Feeding history", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }

            if (logs.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { Text(if (isThai) "ยังไม่มีบันทึก" else "No records yet", color = Outline) } }
            } else {
                items(logs, key = { it.id }) { log -> MilkLogRow(log, isThai) }
            }
        }
    }

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val initialMillis = feedDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms -> feedDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate() }
                    showDatePicker = false
                }) { Text(if (isThai) "ตกลง" else "OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
        ) { DatePicker(state = pickerState) }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(initialHour = feedTime.hour, initialMinute = feedTime.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    feedTime = LocalTime.of(pickerState.hour, pickerState.minute)
                    showTimePicker = false
                }) { Text(if (isThai) "ตกลง" else "OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text(if (isThai) "ยกเลิก" else "Cancel") } },
            text = { TimePicker(state = pickerState) }
        )
    }
}

@Composable
private fun MilkLogRow(log: MilkLog, isThai: Boolean) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (log.isWater) Icons.Filled.WaterDrop else Icons.Filled.BabyChangingStation, null, tint = Primary)
            Column(Modifier.weight(1f)) {
                Text(
                    (when {
                        log.isWater -> if (isThai) "น้ำต้มสุก" else "Water"
                        log.milkType == "breast" -> if (isThai) "นมแม่" else "Breast milk"
                        else -> if (isThai) "นมผง" else "Formula"
                    }) +
                        (log.side?.let { " • ${if (isThai) (if (it == "left") "ซ้าย" else if (it == "right") "ขวา" else "ทั้งสองข้าง") else it}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                )
                Text(
                    com.lullabyai.app.util.TimeUtils.localDate(log.loggedAt)?.let { d -> "$d ${com.lullabyai.app.util.TimeUtils.localTime(log.loggedAt) ?: ""}" } ?: "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!log.notes.isNullOrBlank()) {
                    val noteText = log.notes.orEmpty()
                    Text(noteText, style = MaterialTheme.typography.labelSmall, color = Outline, maxLines = 2)
                }
            }
            Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                Text(log.durationMin?.let { "$it ${if (isThai) "นาที" else "min"}" } ?: "${log.volumeMl} ml", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
