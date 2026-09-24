@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.FoodLog
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.DayNutrition
import com.lullabyai.app.viewmodel.NutritionViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Log Food — Stitch "ไทม์ไลน์อาหารวันนี้". One day of food_logs as a timeline: energy and the four
 * macros against the Thai DRI, a search box and meal filters, then a card per dish. A dish
 * photographed at logging time shows that photo; one typed in shows its meal symbol instead.
 */
@Composable
fun EditFoodEntriesScreen(vm: NutritionViewModel, lang: String, navController: NavController, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    LaunchedEffect(Unit) { if (ui.child == null) vm.load(lang) }
    LaunchedEffect(ui.foods, ui.todayMilk, lang) { vm.loadDayNutrition(lang) }

    var editing by remember { mutableStateOf<FoodLog?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var slot by remember { mutableStateOf("all") }

    val totals = ui.dayTotals
    val months = vm.ageMonths()
    val intake = vm.dayIntakeKcal() ?: ui.foods.sumOf { it.calories ?: 0 }
    val target = ui.dri?.energyKcal ?: ui.targetKcal
    val share = if (target > 0) intake.toFloat() / target else 0f

    val shown = ui.foods.filter { f ->
        (slot == "all" || foodSlotKey(f) == slot) &&
            (search.isBlank() || f.itemName.contains(search.trim(), ignoreCase = true))
    }
    val lastUpdate = ui.foods.mapNotNull { com.lullabyai.app.util.TimeUtils.localTime(it.createdAt) }.maxOrNull()

    Scaffold(
        topBar = {
            com.lullabyai.app.ui.components.LullabyTopBar(
                title = if (isThai) "รายการอาหารวันนี้" else "Today's food",
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
                onConnect = { navController.navigate(Screen.MyDoctorsHub.route) { launchSingleTop = true } }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                val day = runCatching { LocalDate.parse(ui.logDate) }.getOrDefault(LocalDate.now())
                DayNavigator(
                    label = (if (day == LocalDate.now()) (if (isThai) "วันนี้, " else "Today, ") else "") + formatLogDateHeader(ui.logDate, isThai),
                    canGoForward = day.isBefore(LocalDate.now()),
                    onPrev = { vm.setLogDate(day.minusDays(1).toString(), lang) },
                    onNext = { vm.setLogDate(day.plusDays(1).toString(), lang) },
                    onPick = { showDatePicker = true }
                )
            }

            item {
                DayEnergyCard(
                    intake = intake, target = target, share = share, totals = totals, months = months,
                    targetMacros = ui.targetMacros, isThai = isThai,
                    breastMilkOnly = ui.dri?.breastMilkCovers == true,
                    loading = ui.dayTotalsLoading && totals == null
                )
            }

            item {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                    trailingIcon = {
                        if (search.isNotBlank()) IconButton(onClick = { search = "" }) {
                            Icon(Icons.Filled.Close, if (isThai) "ล้างคำค้น" else "Clear", tint = Outline, modifier = Modifier.size(18.dp))
                        }
                    },
                    placeholder = { Text(if (isThai) "ค้นหาเมนูอาหารหรือวัตถุดิบ..." else "Search a dish or ingredient...", color = Outline) },
                    singleLine = true, shape = RoundedCornerShape(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                )
            }

            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "all" to (if (isThai) "ทั้งหมด" else "All"),
                        "breakfast" to mealLabel("breakfast", isThai),
                        "snack_morning" to mealLabel("snack_morning", isThai),
                        "lunch" to mealLabel("lunch", isThai),
                        "snack_afternoon" to mealLabel("snack_afternoon", isThai),
                        "dinner" to mealLabel("dinner", isThai)
                    ).forEach { (key, label) ->
                        val selected = slot == key
                        Surface(
                            onClick = { slot = key }, shape = RoundedCornerShape(100.dp),
                            color = if (selected) Primary else MaterialTheme.colorScheme.surface,
                            border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                label, Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                            )
                        }
                    }
                }
            }

            item {
                Surface(shape = RoundedCornerShape(18.dp), color = Primary.copy(0.07f)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Primary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.SmartToy, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (isThai) "วิเคราะห์จาก AI" else "AI reading", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Primary)
                                Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface) {
                                    Text("NongRak Log", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(vm.loggingInsightText(isThai), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (isThai) "ไทม์ไลน์อาหารวันนี้ (${shown.size} รายการ)" else "Timeline (${shown.size} items)",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                    if (lastUpdate != null) Text(
                        if (isThai) "อัปเดตล่าสุด $lastUpdate น." else "Last update $lastUpdate",
                        style = MaterialTheme.typography.labelSmall, color = Outline
                    )
                }
            }

            if (shown.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (ui.foods.isEmpty()) (if (isThai) "ยังไม่มีรายการอาหารของวันนี้" else "No food logged for this day")
                            else (if (isThai) "ไม่พบรายการที่ตรงกับตัวกรอง" else "Nothing matches this filter"),
                            color = Outline, style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(shown, key = { it.id }) { f ->
                    TimelineCard(
                        food = f, vm = vm, isThai = isThai,
                        portion = totals?.portions?.firstOrNull { it.logId == f.id },
                        targets = mapOf(
                            "protein" to ui.targetMacros.second.toDouble(),
                            "fibre" to (DayNutrition.fibreTargetG(months) ?: 0.0),
                            "calcium" to ui.targetCalciumMg.toDouble(),
                            "iron" to ui.targetIronMg.toDouble(),
                            "vitaminA" to (ui.dri?.values?.get("vitaminA") ?: 0.0),
                            "vitaminC" to (ui.dri?.values?.get("vitaminC") ?: 0.0),
                            "zinc" to (ui.dri?.values?.get("zinc") ?: 0.0)
                        ),
                        allergies = ui.child?.allergies,
                        onEdit = { editing = f }
                    )
                }
            }

            item {
                Button(
                    onClick = { navController.navigate(Screen.CalorieCalculator.route) },
                    modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(100.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.AddCircleOutline, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "บันทึกมื้ออาหารใหม่" else "Log a new meal", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    editing?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.itemName) }
        var kcalText by remember(target.id) { mutableStateOf(target.calories?.toString() ?: "") }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (isThai) "แก้ไขรายการอาหาร" else "Edit food", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name, onValueChange = { name = it }, singleLine = true,
                        label = { Text(if (isThai) "ชื่ออาหาร" else "Food name") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = kcalText, onValueChange = { kcalText = it.filter { c -> c.isDigit() } }, singleLine = true,
                        label = { Text("kcal") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) vm.editFood(target.id, name)
                    kcalText.toIntOrNull()?.let { vm.editFoodCalories(target.id, it) }
                    editing = null
                }) { Text(if (isThai) "บันทึก" else "Save", color = Primary, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                Row {
                    if (ui.isChildOwner) {
                        TextButton(onClick = { vm.deleteFood(target.id); editing = null }) {
                            Text(if (isThai) "ลบ" else "Delete", color = Error)
                        }
                    }
                    TextButton(onClick = { editing = null }) { Text(if (isThai) "ยกเลิก" else "Cancel") }
                }
            }
        )
    }

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val current = runCatching { LocalDate.parse(ui.logDate) }.getOrDefault(LocalDate.now())
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = current.atStartOfDay(zone).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        vm.setLogDate(Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString(), lang)
                    }
                    showDatePicker = false
                }) { Text(if (isThai) "ตกลง" else "OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun DayNavigator(label: String, canGoForward: Boolean, onPrev: () -> Unit, onNext: () -> Unit, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "previous day", tint = Primary) }
        Surface(onClick = onPick, modifier = Modifier.weight(1f), shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CalendarToday, null, tint = Primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Icon(Icons.Filled.ArrowDropDown, null, tint = Outline)
            }
        }
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "next day", tint = if (canGoForward) Primary else Outline)
        }
    }
}

@Composable
private fun DayEnergyCard(
    intake: Int, target: Int, share: Float, totals: DayNutrition.Totals?, months: Int,
    targetMacros: Triple<Int, Int, Int>, isThai: Boolean, breastMilkOnly: Boolean, loading: Boolean
) {
    val (targetCarb, targetProtein, targetFat) = targetMacros
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Text(if (isThai) "พลังงานที่ได้รับวันนี้" else "Energy today", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (target > 0) {
                    val ok = share in 0.7f..1.2f
                    Surface(shape = RoundedCornerShape(100.dp), color = (if (ok) FoodGood else FoodWatch).copy(0.15f)) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Info, null, tint = if (ok) FoodGood else FoodWatch, modifier = Modifier.size(14.dp))
                            Text(
                                when {
                                    ok -> if (isThai) "โภชนาการสมดุลดี" else "Well balanced"
                                    share < 0.7f -> if (isThai) "ยังได้รับน้อย" else "Below target"
                                    else -> if (isThai) "เกินเกณฑ์" else "Above target"
                                },
                                style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (ok) FoodGood else FoodWatch
                            )
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text("%,d".format(intake), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Primary)
                Text(" / %,d kcal".format(target), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LinearProgressIndicator(
                progress = { share.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(100.dp)),
                color = Primary, trackColor = Primary.copy(0.15f)
            )
            when {
                breastMilkOnly -> Text(
                    if (isThai) "0–5 เดือน: นมแม่ให้สารอาหารครบตามเกณฑ์" else "0–5 months: milk covers the reference intakes",
                    style = MaterialTheme.typography.labelSmall, color = Outline
                )
                loading -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Primary)
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(if (isThai) "โปรตีน" else "Protein", "protein", targetProtein.toDouble()),
                        Triple(if (isThai) "คาร์บ" else "Carbs", "carb", targetCarb.toDouble()),
                        Triple(if (isThai) "ไขมันดี" else "Fat", "fat", targetFat.toDouble()),
                        Triple(if (isThai) "ใยอาหาร" else "Fibre", "fibre", DayNutrition.fibreTargetG(months) ?: 0.0)
                    ).forEach { (label, key, t) ->
                        val amount = totals?.amount?.get(key)
                        Surface(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = Primary.copy(0.06f)) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                Text(
                                    amount?.let { if (it < 10) "%.1fg".format(it) else "%.0fg".format(it) } ?: "—",
                                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                                )
                                LinearProgressIndicator(
                                    progress = { if (t > 0 && amount != null) (amount / t).toFloat().coerceIn(0f, 1f) else 0f },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(100.dp)),
                                    color = Primary, trackColor = Primary.copy(0.15f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineCard(
    food: FoodLog, vm: NutritionViewModel, isThai: Boolean,
    portion: DayNutrition.Portion?, targets: Map<String, Double>, allergies: String?,
    onEdit: () -> Unit
) {
    var photoUrl by remember(food.id, food.photoPath) { mutableStateOf<String?>(null) }
    LaunchedEffect(food.photoPath) { photoUrl = food.photoPath?.let { vm.foodPhotoUrl(it) } }

    val amounts = portion?.let { p -> targets.keys.mapNotNull { k -> p.amountOf(k)?.let { k to it } }.toMap() }.orEmpty()
    val strong = DayNutrition.strongPoints(amounts, targets).mapNotNull { nutrientChipLabel(it, isThai) }.take(2)
    val allergen = allergyHit(food.itemName, allergies)
    val time = com.lullabyai.app.util.TimeUtils.localTime(food.createdAt) ?: ""

    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(mealIcon(food.mealType), null, tint = Primary, modifier = Modifier.size(18.dp))
                Text(displayMealLabel(food, isThai), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary)
                if (time.isNotBlank()) Text(if (isThai) "$time น." else time, style = MaterialTheme.typography.labelSmall, color = Outline)
                Spacer(Modifier.weight(1f))
                Text("${food.calories ?: "-"} kcal", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.5f))
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // A photo only when the parent actually took one; otherwise the meal's own symbol,
                // never a stand-in picture of somebody else's food.
                val link = photoUrl
                if (link != null) {
                    AsyncImage(
                        model = link, contentDescription = food.itemName, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(Primary.copy(0.08f))
                    )
                } else {
                    Box(
                        Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)).background(Primary.copy(0.08f)),
                        contentAlignment = Alignment.Center
                    ) { Icon(mealIcon(food.mealType), null, tint = Primary.copy(0.7f), modifier = Modifier.size(32.dp)) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(food.itemName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    eatenLabel(food.eatenPct, isThai)?.let { TagChip(it, FoodGood) }
                    if (strong.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        strong.forEach { TagChip(it, Primary) }
                    }
                    if (allergen != null) TagChip((if (isThai) "ระวังแพ้: " else "Allergen: ") + allergen, Error)
                }
            }
            Surface(onClick = onEdit, color = Primary.copy(0.05f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        portion?.matchedName?.let { (if (isThai) "เทียบกับตาราง: " else "Matched to: ") + it }
                            ?: (if (isThai) "ประเมินโดย AI" else "AI estimate"),
                        style = MaterialTheme.typography.labelSmall, color = Outline,
                        modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(if (isThai) "ดูรายละเอียด / แก้ไข" else "Details / edit", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary)
                    Icon(Icons.Filled.ChevronRight, null, tint = Primary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = color.copy(0.12f)) {
        Text(
            text, Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1
        )
    }
}
