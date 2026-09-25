@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.MilkLog
import com.lullabyai.app.data.repository.NutritionRepository
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.HydrationGuide
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.NutritionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Water = Color(0xFF0E9AA7)

private val MILK_QUICK_ML = listOf(60, 90, 120, 150, 180, 210)
private val WATER_QUICK_ML = listOf(15, 30, 50, 100)

/**
 * Milk and water recorder — Stitch "สรุปการรับของเหลววันนี้". Two tabs over one form, and the form
 * shows only the parts the parent's choice actually needs: a feed at the breast asks for minutes per
 * side, anything in a bottle asks for millilitres and temperature, water asks how it was given.
 *
 * The day's targets come from [HydrationGuide] rather than the round numbers in the mockup: milk
 * from the child's own DRI energy, water from the age rule the card also states in words. Under six
 * months the water tab is closed and says why, instead of setting a goal a baby should not meet.
 */
@Composable
fun MilkRecordScreen(childViewModel: ChildViewModel, nutritionViewModel: NutritionViewModel, lang: String, navController: NavController, onBack: () -> Unit) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val childState by childViewModel.uiState.collectAsState()
    val ui by nutritionViewModel.ui.collectAsState()
    val child = childState.child
    val childId = child?.id
    val repo = remember { NutritionRepository() }
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }

    var logs by remember { mutableStateOf<List<MilkLog>>(emptyList()) }
    fun reload() { childId?.let { cid -> scope.launch { logs = repo.getMilkLogs(cid) } } }
    LaunchedEffect(childId) { reload(); nutritionViewModel.load(lang) }

    val months = nutritionViewModel.ageMonths(child)
    val milkTarget = HydrationGuide.milkTargetMl(months, ui.dri?.energyKcal ?: ui.targetKcal.takeIf { it > 0 })
    val waterTarget = HydrationGuide.waterTargetMl(months)
    val waterNotAdvised = HydrationGuide.waterNotAdvised(months)

    var tab by remember { mutableStateOf("milk") }
    var milkType by remember { mutableStateOf("breast") }
    var subtype by remember { mutableStateOf("direct") }
    // Starts at zero like the water amount does: the parent counts up from nothing, and can take
    // the minutes back down when the timer ran on after the baby had already stopped.
    var leftMin by remember { mutableStateOf(0) }
    var rightMin by remember { mutableStateOf(0) }
    var timerRunning by remember { mutableStateOf<String?>(null) }   // "left" | "right" | null
    var volumeMl by remember { mutableStateOf(150) }
    var unitOz by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf("warm") }
    var waterKind by remember { mutableStateOf("boiled") }
    var waterMl by remember { mutableStateOf(30) }
    var method by remember { mutableStateOf("cup") }
    var reaction by remember { mutableStateOf("finished") }
    var notes by remember { mutableStateOf("") }
    var feedDate by remember { mutableStateOf(LocalDate.now()) }
    var feedTime by remember { mutableStateOf(LocalTime.now().withSecond(0).withNano(0)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val isWaterTab = tab == "water"
    val isDirect = milkType == "breast" && subtype == "direct"

    // The running side ticks up a minute at a time, so a parent can start it and put the phone down.
    LaunchedEffect(timerRunning) {
        while (timerRunning != null) {
            delay(60_000)
            if (timerRunning == "left") leftMin = (leftMin + 1).coerceAtMost(120)
            if (timerRunning == "right") rightMin = (rightMin + 1).coerceAtMost(120)
        }
    }
    LaunchedEffect(months) { if (waterNotAdvised && tab == "water") tab = "milk" }
    LaunchedEffect(isWaterTab) { reaction = if (isWaterTab) "easy" else "finished" }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "บันทึกการดื่มนม / น้ำ" else "Milk and water",
                onBack = onBack,
                navController = navController
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
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
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                HydrationCard(
                    milkMl = ui.todayMilkMl, milkTarget = milkTarget,
                    waterMl = ui.todayWaterMl, waterTarget = waterTarget,
                    waterNotAdvised = waterNotAdvised, isThai = isThai
                )
            }

            item {
                Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                    Row(Modifier.padding(5.dp)) {
                        TabPill(Modifier.weight(1f), Icons.Filled.BabyChangingStation, if (isThai) "บันทึกการดื่มนม" else "Milk", tab == "milk", Primary) { tab = "milk" }
                        TabPill(
                            Modifier.weight(1f), Icons.Filled.WaterDrop, if (isThai) "บันทึกการดื่มน้ำ" else "Water",
                            isWaterTab, Water, enabled = !waterNotAdvised
                        ) { tab = "water" }
                    }
                }
            }

            if (waterNotAdvised) {
                item {
                    NoteStrip(
                        Icons.Filled.Info,
                        if (isThai) "อายุต่ำกว่า 6 เดือน ยังไม่ต้องให้น้ำเปล่า นมแม่หรือนมผงให้น้ำเพียงพอแล้ว แท็บน้ำจึงยังปิดอยู่"
                        else "Under six months no plain water is needed — milk provides all of it — so the water tab stays closed.",
                        Outline
                    )
                }
            }

            if (!isWaterTab) {
                item { StepHeader(if (isThai) "เลือกประเภทน้ำนม" else "Which milk", if (isThai) "ขั้นตอนที่ 1" else "Step 1", Primary) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoiceCard(
                                Modifier.weight(1f), Icons.Filled.Favorite, if (isThai) "นมแม่เข้าเต้า" else "At the breast",
                                if (isThai) "ดูดจากเต้าโดยตรง" else "Direct", milkType == "breast" && subtype == "direct", Primary
                            ) { milkType = "breast"; subtype = "direct" }
                            ChoiceCard(
                                Modifier.weight(1f), Icons.Filled.LocalDrink, if (isThai) "นมแม่สต็อก / ปั๊ม" else "Pumped milk",
                                if (isThai) "นมแม่ใส่ขวด" else "In a bottle", milkType == "breast" && subtype == "pumped", Primary
                            ) { milkType = "breast"; subtype = "pumped" }
                        }
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ChoiceCard(
                                Modifier.weight(1f), Icons.Filled.BabyChangingStation, if (isThai) "นมผง" else "Formula",
                                if (isThai) "ชงตามสัดส่วน" else "Made up to the scoop", milkType == "formula", Primary
                            ) { milkType = "formula"; subtype = "" }
                            ChoiceCard(
                                Modifier.weight(1f), Icons.Filled.Coffee, if (isThai) "นม UHT / นมเสริม" else "UHT milk",
                                if (isThai) "สำหรับเด็ก 1 ขวบขึ้นไป" else "One year and older", milkType == "uht", Primary,
                                enabled = months >= 12
                            ) { milkType = "uht"; subtype = "" }
                        }
                        if (months < 12) {
                            Text(
                                if (isThai) "นมกล่องยังไม่เหมาะกับเด็กต่ำกว่า 1 ขวบ จึงยังเลือกไม่ได้" else "Cow's milk is not suitable under one year, so it cannot be picked yet",
                                style = MaterialTheme.typography.labelSmall, color = Outline
                            )
                        }
                    }
                }

                // Shown only for a feed at the breast — minutes, not millilitres.
                if (isDirect) {
                    item {
                        BreastTimerCard(
                            leftMin = leftMin, rightMin = rightMin, running = timerRunning,
                            onLeft = { leftMin = (leftMin + it).coerceIn(0, 120) },
                            onRight = { rightMin = (rightMin + it).coerceIn(0, 120) },
                            onToggle = { side -> timerRunning = if (timerRunning == side) null else side },
                            isThai = isThai
                        )
                    }
                } else {
                    // Shown for anything that arrives in a bottle.
                    item {
                        VolumeCard(
                            title = if (isThai) "ปริมาณน้ำนมที่ดื่ม" else "How much milk",
                            ml = volumeMl, onMl = { volumeMl = it.coerceIn(10, 400) },
                            quick = MILK_QUICK_ML, unitOz = unitOz, onUnit = { unitOz = it },
                            accent = Primary, isThai = isThai, goalText = null
                        )
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (isThai) "อุณหภูมินม" else "Milk temperature", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "warm" to (if (isThai) "อุ่นพอดี (37°C)" else "Warm (37°C)"),
                                    "room" to (if (isThai) "อุณหภูมิห้อง" else "Room temp"),
                                    "cold" to (if (isThai) "เย็น" else "Cold")
                                ).forEach { pair -> PillChoice(pair.second, temperature == pair.first, Primary) { temperature = pair.first } }
                            }
                        }
                    }
                }
            } else {
                item { StepHeader(if (isThai) "เลือกประเภทน้ำ" else "Which water", if (isThai) "ขั้นตอนที่ 1" else "Step 1", Water) }
                item {
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ChoiceCard(
                            Modifier.weight(1f), Icons.Filled.WaterDrop, if (isThai) "น้ำต้มสุกสะอาด" else "Boiled water",
                            if (isThai) "อุณหภูมิห้อง" else "Room temp", waterKind == "boiled", Water
                        ) { waterKind = "boiled" }
                        ChoiceCard(
                            Modifier.weight(1f), Icons.Filled.LocalDrink, if (isThai) "น้ำแร่เด็ก" else "Baby mineral water",
                            if (isThai) "ค่าโซเดียมต่ำ" else "Low sodium", waterKind == "mineral", Water
                        ) { waterKind = "mineral" }
                        ChoiceCard(
                            Modifier.weight(1f), Icons.Filled.Thermostat, if (isThai) "น้ำอุ่นสะอาด" else "Warm water",
                            if (isThai) "ช่วยขับลม" else "Settles wind", waterKind == "warm", Water
                        ) { waterKind = "warm" }
                    }
                }
                item {
                    VolumeCard(
                        title = if (isThai) "ปริมาณน้ำที่ดื่ม" else "How much water",
                        ml = waterMl, onMl = { waterMl = it.coerceIn(5, 300) },
                        quick = WATER_QUICK_ML, unitOz = false, onUnit = null,
                        accent = Water, isThai = isThai,
                        goalText = if (waterTarget > 0) (if (isThai) "เป้าหมาย $waterTarget มล./วัน" else "Goal $waterTarget ml/day") else null,
                        quickLabels = if (isThai) listOf("จิบนิดหน่อย", "หลังอาหาร", "แก้วหัดดื่ม", "แก้วใหญ่")
                        else listOf("a sip", "after a meal", "training cup", "big cup")
                    )
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (isThai) "วิธีให้ดื่มน้ำ" else "How it was given", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(
                                "cup" to (if (isThai) "แก้วหัดดื่ม" else "Training cup"),
                                "straw" to (if (isThai) "หลอดดูด" else "Straw"),
                                "spoon" to (if (isThai) "ช้อนป้อน" else "Spoon"),
                                "bottle" to (if (isThai) "ขวดนม" else "Bottle")
                            ).forEach { pair -> PillChoice(pair.second, method == pair.first, Water) { method = pair.first } }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FieldBox(
                        Modifier.weight(1f), Icons.Filled.Schedule, if (isThai) "เวลาที่ดื่ม" else "Time",
                        feedTime.format(DateTimeFormatter.ofPattern("HH:mm")) + (if (isThai) " น." else ""),
                        if (feedDate == LocalDate.now() && feedTime.isAfter(LocalTime.now().minusMinutes(10))) (if (isThai) "เมื่อสักครู่" else "just now") else null
                    ) { showTimePicker = true }
                    FieldBox(
                        Modifier.weight(1f), Icons.Filled.CalendarToday, if (isThai) "วันที่" else "Date",
                        if (feedDate == LocalDate.now()) (if (isThai) "วันนี้" else "Today") else feedDate.toString(), null
                    ) { showDatePicker = true }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (isWaterTab) (if (isThai) "ปฏิกิริยาการดื่มน้ำ" else "How the water went")
                        else (if (isThai) "ปฏิกิริยาหลังดื่มนม" else "How the feed went"),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val options = if (isWaterTab) listOf(
                        Triple("easy", if (isThai) "ดื่มง่าย สดชื่น" else "Drank easily", Icons.Filled.SentimentVerySatisfied),
                        Triple("little", if (isThai) "ดื่มได้นิดหน่อย" else "Only a little", Icons.Filled.SentimentNeutral),
                        Triple("refused", if (isThai) "ปฏิเสธ ไม่ยอมดื่ม" else "Refused", Icons.Filled.SentimentDissatisfied)
                    ) else listOf(
                        Triple("finished", if (isThai) "ดื่มหมดเกลี้ยง" else "Finished it", Icons.Filled.SentimentVerySatisfied),
                        Triple("left_some", if (isThai) "เหลือเล็กน้อย" else "Left a little", Icons.Filled.SentimentNeutral),
                        Triple("spit_up", if (isThai) "แหวะนม / สำลัก" else "Spat up", Icons.Filled.SentimentDissatisfied)
                    )
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { opt ->
                            ChoiceCard(
                                Modifier.weight(1f), opt.third, opt.second, null, reaction == opt.first,
                                if (isWaterTab) Water else Primary
                            ) { reaction = opt.first }
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = notes, onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp),
                    label = { Text(if (isThai) "บันทึกเพิ่มเติม (ไม่บังคับ)" else "Note (optional)") },
                    placeholder = {
                        Text(
                            if (isThai) "เช่น เข้าเต้าดีมาก ไม่หลับคาเต้า ดื่มน้ำตาม 2 อึก" else "e.g. fed well, stayed awake, took two sips of water after",
                            color = Outline
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = if (isWaterTab) Water else Primary)
                )
            }

            item { AdviceStrip(isWaterTab, isThai) }

            item {
                Button(
                    onClick = {
                        val cid = childId ?: return@Button
                        saving = true
                        val loggedAt = LocalDateTime.of(feedDate, feedTime).atZone(ZoneId.systemDefault()).toInstant().toString()
                        scope.launch {
                            val result = when {
                                isWaterTab -> repo.addMilkLog(
                                    cid, MilkLog.WATER, waterMl, null, notes.takeIf { it.isNotBlank() }, loggedAt,
                                    subtype = waterKind, reaction = reaction, method = method
                                )
                                isDirect -> repo.addMilkLog(
                                    cid, "breast", 0,
                                    side = when {
                                        leftMin > 0 && rightMin > 0 -> "both"
                                        leftMin > 0 -> "left"
                                        rightMin > 0 -> "right"
                                        else -> null
                                    },
                                    notes = notes.takeIf { it.isNotBlank() }, loggedAt = loggedAt,
                                    durationMin = leftMin + rightMin,
                                    subtype = "direct", leftMin = leftMin, rightMin = rightMin, reaction = reaction
                                )
                                else -> repo.addMilkLog(
                                    cid, milkType, volumeMl, null, notes.takeIf { it.isNotBlank() }, loggedAt,
                                    subtype = subtype.takeIf { it.isNotBlank() },
                                    temperature = temperature, reaction = reaction
                                )
                            }
                            result.onSuccess {
                                notes = ""
                                timerRunning = null
                                reload()
                                nutritionViewModel.load(lang)
                                snackbars.showSnackbar(if (isThai) "บันทึกเรียบร้อยแล้ว" else "Saved")
                            }.onFailure { e ->
                                snackbars.showSnackbar(
                                    (if (isThai) "บันทึกไม่สำเร็จ: " else "Could not save: ") + (e.message ?: e::class.simpleName.orEmpty())
                                )
                            }
                            saving = false
                        }
                    },
                    enabled = !saving && childId != null,
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isWaterTab) Water else Primary)
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    else Icon(Icons.Filled.Save, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isWaterTab) (if (isThai) "บันทึกข้อมูลการดื่มน้ำ" else "Save this drink")
                        else (if (isThai) "บันทึกข้อมูลการดื่มนม" else "Save this feed"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item { Text(if (isThai) "ประวัติวันนี้" else "Today", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            val today = LocalDate.now().toString()
            val todayLogs = logs.filter { com.lullabyai.app.util.TimeUtils.localDate(it.loggedAt) == today }
            if (todayLogs.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                        Text(if (isThai) "ยังไม่มีบันทึกวันนี้" else "Nothing logged today", color = Outline)
                    }
                }
            } else {
                items(todayLogs, key = { it.id }) { log -> LogRow(log, isThai) }
            }
        }
    }

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = feedDate.atStartOfDay(zone).toInstant().toEpochMilli())
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
private fun HydrationCard(
    milkMl: Int, milkTarget: Int?, waterMl: Int, waterTarget: Int,
    waterNotAdvised: Boolean, isThai: Boolean
) {
    val milkBalance = HydrationGuide.balance(milkMl, milkTarget)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.WaterDrop, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text(if (isThai) "สรุปการรับของเหลววันนี้" else "Today's hydration", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                val badgeText: String
                val badgeColor: Color
                when (milkBalance) {
                    HydrationGuide.Balance.GOOD -> { badgeText = if (isThai) "สมดุลดีเยี่ยม" else "Well balanced"; badgeColor = FoodGood }
                    HydrationGuide.Balance.OVER -> { badgeText = if (isThai) "มากกว่าปกติ" else "More than usual"; badgeColor = FoodWatch }
                    HydrationGuide.Balance.LOW -> { badgeText = if (isThai) "ยังน้อยกว่าปกติ" else "Below usual"; badgeColor = FoodWatch }
                    HydrationGuide.Balance.NONE_YET -> { badgeText = if (isThai) "ยังไม่มีบันทึก" else "Nothing yet"; badgeColor = Outline }
                    HydrationGuide.Balance.UNKNOWN -> { badgeText = if (isThai) "ไม่มีเกณฑ์สำหรับวัยนี้" else "No guide at this age"; badgeColor = Outline }
                }
                Surface(shape = RoundedCornerShape(100.dp), color = badgeColor.copy(0.16f)) {
                    Text(badgeText, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badgeColor, maxLines = 1)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HydrationCell(
                    Modifier.weight(1f), Icons.Filled.BabyChangingStation, if (isThai) "ปริมาณนม" else "Milk",
                    milkMl, milkTarget, Primary,
                    footer = (if (isThai) "~ %.0f ออนซ์" else "~%.0f oz").format(HydrationGuide.toOunces(milkMl)),
                    isThai = isThai
                )
                HydrationCell(
                    Modifier.weight(1f), Icons.Filled.WaterDrop, if (isThai) "น้ำสะอาด" else "Water",
                    waterMl, waterTarget.takeIf { it > 0 }, Water,
                    footer = if (waterNotAdvised) (if (isThai) "ยังไม่ต้องให้น้ำในวัยนี้" else "Not needed at this age")
                    else (if (isThai) "น้ำต้มสุกสะอาดหลังมื้อ" else "boiled water after meals"),
                    isThai = isThai
                )
            }
        }
    }
}

@Composable
private fun HydrationCell(
    modifier: Modifier, icon: ImageVector, label: String, amount: Int, target: Int?, accent: Color,
    footer: String, isThai: Boolean
) {
    val share = if (target != null && target > 0) amount.toFloat() / target else null
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(15.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Text(share?.let { "${(it * 100).toInt()}%" } ?: "—", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = accent)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$amount", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    target?.let { if (isThai) " / $it มล." else " / $it ml" } ?: (if (isThai) " มล." else " ml"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { (share ?: 0f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100.dp)),
                color = accent, trackColor = accent.copy(0.18f)
            )
            Text(footer, style = MaterialTheme.typography.labelSmall, color = Outline, maxLines = 2)
        }
    }
}

@Composable
private fun TabPill(
    modifier: Modifier, icon: ImageVector, label: String, selected: Boolean, accent: Color,
    enabled: Boolean = true, onClick: () -> Unit
) {
    Surface(
        onClick = onClick, modifier = modifier, enabled = enabled, shape = RoundedCornerShape(100.dp),
        color = if (selected) accent else Color.Transparent
    ) {
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(
                icon, null, modifier = Modifier.size(17.dp),
                tint = if (selected) Color.White else if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else Outline
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1,
                color = if (selected) Color.White else if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else Outline
            )
        }
    }
}

@Composable
private fun StepHeader(title: String, step: String, accent: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Category, null, tint = accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(step, style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ChoiceCard(
    modifier: Modifier, icon: ImageVector, title: String, subtitle: String?, selected: Boolean,
    accent: Color, enabled: Boolean = true, onClick: () -> Unit
) {
    Surface(
        onClick = onClick, modifier = modifier.fillMaxHeight(), enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accent else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = if (selected) Color.White else if (enabled) accent else Outline)
            Text(
                title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold,
                color = if (selected) Color.White else if (enabled) MaterialTheme.colorScheme.onSurface else Outline
            )
            if (subtitle != null) {
                Text(
                    subtitle, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = if (selected) Color.White.copy(0.85f) else Outline
                )
            }
        }
    }
}

@Composable
private fun PillChoice(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(100.dp),
        color = if (selected) accent else MaterialTheme.colorScheme.surface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            label, Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BreastTimerCard(
    leftMin: Int, rightMin: Int, running: String?,
    onLeft: (Int) -> Unit, onRight: (Int) -> Unit, onToggle: (String) -> Unit, isThai: Boolean
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Timer, null, tint = Primary, modifier = Modifier.size(18.dp))
                Text(if (isThai) "จับเวลาการเข้าเต้า" else "Feed timer", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SideTimer(Modifier.weight(1f), Icons.AutoMirrored.Filled.KeyboardArrowLeft, if (isThai) "เต้าข้างซ้าย" else "Left", leftMin, running == "left", isThai, onLeft) { onToggle("left") }
                SideTimer(Modifier.weight(1f), Icons.AutoMirrored.Filled.KeyboardArrowRight, if (isThai) "เต้าข้างขวา" else "Right", rightMin, running == "right", isThai, onRight) { onToggle("right") }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(if (isThai) "รวมเวลาเข้าเต้ามื้อนี้" else "Total this feed", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val total = leftMin + rightMin
                val even = leftMin > 0 && rightMin > 0 && kotlin.math.abs(leftMin - rightMin) <= 3
                Text(
                    (if (isThai) "$total นาที" else "$total min") +
                        (if (even) (if (isThai) " (สมดุลสองข้าง)" else " (both sides even)") else ""),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Primary
                )
            }
        }
    }
}

@Composable
private fun SideTimer(
    modifier: Modifier, icon: ImageVector, label: String, minutes: Int, running: Boolean,
    isThai: Boolean, onAdd: (Int) -> Unit, onToggle: () -> Unit
) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = if (running) Primary.copy(0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(0.35f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(15.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$minutes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Primary)
                Text(if (isThai) " นาที" else " min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(-5, 5, 10).forEach { step ->
                    val canPress = step > 0 || minutes > 0
                    Surface(
                        onClick = { onAdd(step) }, enabled = canPress, shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            (if (step < 0) "-" else "+") + kotlin.math.abs(step) + (if (isThai) "น." else "m"),
                            Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                            color = if (canPress) Primary else Outline
                        )
                    }
                }
            }
            Surface(onClick = onToggle, shape = RoundedCornerShape(100.dp), color = if (running) Primary else Primary.copy(0.12f)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        if (running) Icons.Filled.Stop else Icons.Filled.PlayArrow, null,
                        tint = if (running) Color.White else Primary, modifier = Modifier.size(14.dp)
                    )
                    Text(
                        if (running) (if (isThai) "หยุด" else "Stop") else (if (isThai) "เริ่มจับเวลา" else "Start"),
                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                        color = if (running) Color.White else Primary, maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun VolumeCard(
    title: String, ml: Int, onMl: (Int) -> Unit, quick: List<Int>, unitOz: Boolean,
    onUnit: ((Boolean) -> Unit)?, accent: Color, isThai: Boolean, goalText: String?,
    quickLabels: List<String>? = null
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.LocalDrink, null, tint = accent, modifier = Modifier.size(18.dp))
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (onUnit != null) {
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)) {
                        Row(Modifier.padding(3.dp)) {
                            listOf(false to (if (isThai) "มล." else "ml"), true to (if (isThai) "ออนซ์" else "oz")).forEach { pair ->
                                Surface(
                                    onClick = { onUnit(pair.first) }, shape = RoundedCornerShape(8.dp),
                                    color = if (unitOz == pair.first) MaterialTheme.colorScheme.surface else Color.Transparent
                                ) {
                                    Text(
                                        pair.second, Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                                        color = if (unitOz == pair.first) accent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                } else if (goalText != null) {
                    Surface(shape = RoundedCornerShape(100.dp), color = accent.copy(0.12f)) {
                        Text(goalText, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = accent, maxLines = 1)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                Text(
                    if (unitOz) "%.1f".format(HydrationGuide.toOunces(ml)) else "$ml",
                    style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = accent
                )
                Text(
                    if (unitOz) (if (isThai) " ออนซ์" else " oz") else (if (isThai) " มล." else " ml"),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!unitOz) {
                Text(
                    (if (isThai) "(~%.1f ออนซ์)" else "(~%.1f oz)").format(HydrationGuide.toOunces(ml)),
                    Modifier.fillMaxWidth(), style = MaterialTheme.typography.labelSmall, color = Outline,
                    textAlign = TextAlign.Center
                )
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                quick.forEachIndexed { i, value ->
                    val selected = ml == value
                    Surface(
                        onClick = { onMl(value) }, shape = RoundedCornerShape(12.dp),
                        color = if (selected) accent else MaterialTheme.colorScheme.surfaceVariant.copy(0.4f)
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (quickLabels != null) (if (isThai) "+$value มล." else "+$value ml") else "$value",
                                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            quickLabels?.getOrNull(i)?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = if (selected) Color.White.copy(0.85f) else Outline)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldBox(modifier: Modifier, icon: ImageVector, label: String, value: String, hint: String?, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(14.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                if (hint != null) {
                    Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                        Text(hint, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Primary, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteStrip(icon: ImageVector, text: String, tint: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = tint.copy(0.09f)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdviceStrip(isWater: Boolean, isThai: Boolean) {
    val lines = if (isWater) listOf(
        if (isThai) "ทารกต่ำกว่า 6 เดือน: ไม่จำเป็นต้องดื่มน้ำเสริม นมแม่และนมชงมีน้ำเพียงพอแล้ว"
        else "Under six months: no extra water is needed; milk provides enough.",
        if (isThai) "ทารก 6 เดือนขึ้นไป: เริ่มจิบน้ำต้มสุกสะอาดหลังมื้ออาหารวันละ 2-4 ออนซ์ เพื่อฝึกการกลืนและล้างคราบนม"
        else "From six months: sips of boiled water after meals, about 2-4 oz a day, to practise drinking and rinse milk away."
    ) else listOf(
        if (isThai) "ทารกแรกเกิด - 6 เดือน: ได้รับน้ำเพียงพอจากนมแม่อย่างเดียว ไม่ต้องให้น้ำเปล่าเสริม"
        else "Newborn to six months: milk alone provides all the fluid needed.",
        if (isThai) "ทารก 6 เดือนขึ้นไป: เริ่มจิบน้ำต้มสุกสะอาดหลังมื้ออาหารเสริม เพื่อล้างคราบนมและช่วยป้องกันท้องผูก"
        else "From six months: sips of boiled water after solids, to rinse milk away and help avoid constipation."
    )
    Surface(shape = RoundedCornerShape(16.dp), color = Primary.copy(0.06f)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.HealthAndSafety, null, tint = Primary, modifier = Modifier.size(18.dp))
                Text(
                    if (isWater) (if (isThai) "คำแนะนำกุมารแพทย์: การดื่มน้ำของทารก" else "Paediatric advice: water for babies")
                    else (if (isThai) "คำแนะนำทางการแพทย์: สมดุลน้ำในทารก" else "Paediatric advice: fluid balance"),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Primary
                )
            }
            lines.forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(top = 6.dp).size(5.dp).clip(CircleShape).background(Primary))
                    Text(line, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: MilkLog, isThai: Boolean) {
    val accent = if (log.isWater) Water else Primary
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (log.isWater) Icons.Filled.WaterDrop else Icons.Filled.BabyChangingStation, null, tint = accent)
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        log.isWater -> if (isThai) "น้ำต้มสุก" else "Water"
                        log.milkType == "breast" && log.subtype == "direct" -> if (isThai) "นมแม่เข้าเต้า" else "At the breast"
                        log.milkType == "breast" -> if (isThai) "นมแม่สต็อก" else "Pumped milk"
                        log.milkType == "uht" -> if (isThai) "นม UHT" else "UHT milk"
                        else -> if (isThai) "นมผง" else "Formula"
                    },
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                )
                Text(
                    listOfNotNull(
                        com.lullabyai.app.util.TimeUtils.localTime(log.loggedAt)?.let { if (isThai) "$it น." else it },
                        log.durationMin?.takeIf { it > 0 }?.let { total ->
                            (if (isThai) "$total นาที" else "$total min") +
                                (log.leftMin?.let { l -> log.rightMin?.let { r -> " (L $l / R $r)" } } ?: "")
                        },
                        log.volumeMl.takeIf { it > 0 }?.let { if (isThai) "$it มล." else "$it ml" }
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall, color = Outline
                )
                log.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Outline, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            log.reaction?.let { r ->
                Icon(
                    when (r) {
                        "finished", "easy" -> Icons.Filled.SentimentVerySatisfied
                        "left_some", "little" -> Icons.Filled.SentimentNeutral
                        else -> Icons.Filled.SentimentDissatisfied
                    },
                    null, tint = accent, modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
