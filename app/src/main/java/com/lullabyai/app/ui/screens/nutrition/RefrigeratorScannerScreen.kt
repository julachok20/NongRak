@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.nutrition

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.Allergy
import com.lullabyai.app.data.model.FoodTrial
import com.lullabyai.app.data.model.FridgeIngredient
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.PantrySafety
import com.lullabyai.app.viewmodel.NutritionViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

private data class PantryFilter(val key: String, val labelTh: String, val labelEn: String, val categories: Set<String>)

private val PANTRY_FILTERS = listOf(
    PantryFilter("all", "ทั้งหมด", "All", emptySet()),
    PantryFilter("produce", "ผัก & ผลไม้", "Produce", setOf("vegetable", "fruit")),
    PantryFilter("protein", "โปรตีน & เนื้อสัตว์", "Protein", setOf("meat", "fish", "egg", "dairy")),
    PantryFilter("grain", "ข้าว & แป้ง", "Grains", setOf("grain")),
    PantryFilter("other", "อื่นๆ", "Other", setOf("general"))
)

/**
 * Ingredients — Stitch "คลังวัตถุดิบลูกน้อย". The pantry belongs to the HOUSEHOLD: every child of
 * this family shares one fridge, and so does everyone who cares for them. The allergy side is the
 * opposite — a food trial belongs to the one child on screen, because a sibling's result says
 * nothing about this one.
 */
@Composable
fun RefrigeratorScannerScreen(
    vm: NutritionViewModel,
    lang: String,
    navController: NavController,
    onBack: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    val child = ui.child

    LaunchedEffect(child?.id) { vm.loadFridgeIngredients() }

    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FridgeIngredient?>(null) }
    var deciding by remember { mutableStateOf<Pair<FoodTrial, String>?>(null) }

    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = pendingPhoto
        pendingPhoto = null
        if (ok && uri != null) {
            val bytes = com.lullabyai.app.util.ImageUtils.downscaleToJpeg(context, uri)
            if (bytes == null) {
                scope.launch { snackbars.showSnackbar(if (isThai) "อ่านไฟล์รูปไม่สำเร็จ" else "Could not read the photo file") }
            } else {
                vm.scanFridgePhoto(bytes, lang) { found ->
                    scope.launch {
                        snackbars.showSnackbar(
                            if (found.isEmpty()) (if (isThai) "ไม่พบวัตถุดิบในรูป ลองถ่ายให้เห็นของในตู้ชัดขึ้น" else "No ingredients spotted — try a clearer shot")
                            else (if (isThai) "เพิ่มวัตถุดิบจากรูป ${found.size} รายการ" else "Added ${found.size} ingredients from the photo")
                        )
                    }
                }
            }
        }
    }
    fun launchCamera() {
        val file = File(context.cacheDir, "fridge_${System.currentTimeMillis()}.jpg")
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        pendingPhoto = uri
        cameraLauncher.launch(uri)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else scope.launch { snackbars.showSnackbar(if (isThai) "ต้องอนุญาตให้ใช้กล้องก่อน" else "Camera permission is needed") }
    }
    val openCamera = {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) launchCamera()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    val today = LocalDate.now()
    val items = ui.fridgeIngredients
    fun trialOf(name: String): FoodTrial? =
        ui.foodTrials.firstOrNull { it.foodName.trim().equals(name.trim(), ignoreCase = true) }
    fun allergyOf(name: String): Allergy? =
        ui.allergies.firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }

    val urgent = items.filter { PantrySafety.needsCookingSoon(it.expiresOn, today) }
    val shown = items.filter { item ->
        val f = PANTRY_FILTERS.first { it.key == filter }
        (f.categories.isEmpty() || item.category in f.categories) &&
            (search.isBlank() || item.ingredientName.contains(search.trim(), ignoreCase = true))
    }
    val testedCount = ui.foodTrials.count { it.result == "safe" }

    Scaffold(
        topBar = {
            LullabyTopBar(
                title = if (isThai) "คลังวัตถุดิบลูกน้อย" else "Baby pantry",
                onBack = onBack,
                navController = navController
            )
        },
        snackbarHost = { SnackbarHost(snackbars) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Surface(shape = RoundedCornerShape(18.dp), color = Primary.copy(0.07f)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Primary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (isThai) "คำแนะนำจาก NongRak AI" else "NongRak AI", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Primary)
                                Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface) {
                                    Text(if (isThai) "อัจฉริยะ" else "Smart", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(pantryAdvice(urgent, isThai), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCell(Modifier.weight(1f), Icons.Filled.Inventory2, "${items.size}", if (isThai) "วัตถุดิบทั้งหมด" else "In the pantry", Primary)
                    StatCell(Modifier.weight(1f), Icons.Filled.Schedule, "${urgent.size}", if (isThai) "ใกล้หมดอายุ" else "Use soon", if (urgent.isEmpty()) FoodGood else FoodWatch)
                    StatCell(Modifier.weight(1f), Icons.Filled.Verified, "$testedCount", if (isThai) "ทดสอบแล้ว" else "Tested safe", FoodGood)
                }
            }

            item {
                // The list is the family's, not this child's — a carer needs to know whose fridge
                // they are looking at before they add to it.
                Text(
                    if (isThai) "คลังนี้ใช้ร่วมกันกับทุกคนที่ดูแลลูกในบ้านนี้ ส่วนผลทดสอบการแพ้เป็นของ${child?.name ?: "น้อง"}คนเดียว"
                    else "This pantry is shared with everyone caring for this family's children; allergy results belong to ${child?.name ?: "this child"} alone",
                    style = MaterialTheme.typography.labelSmall, color = Outline
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { adding = true },
                        modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        Icon(Icons.Filled.AddCircleOutline, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(if (isThai) "เพิ่มวัตถุดิบ" else "Add", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = openCamera,
                        modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(14.dp),
                        enabled = !ui.fridgeScanning
                    ) {
                        if (ui.fridgeScanning) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                        else Icon(Icons.Filled.CameraAlt, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isThai) "สแกนตู้เย็น AI" else "Scan fridge", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = search, onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.Search, null, tint = Outline) },
                    placeholder = { Text(if (isThai) "ค้นหาชื่อวัตถุดิบ..." else "Search an ingredient...", color = Outline) },
                    singleLine = true, shape = RoundedCornerShape(100.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant)
                )
            }

            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PANTRY_FILTERS.forEach { f ->
                        val count = if (f.categories.isEmpty()) items.size else items.count { it.category in f.categories }
                        val selected = filter == f.key
                        Surface(
                            onClick = { filter = f.key }, shape = RoundedCornerShape(100.dp),
                            color = if (selected) Primary else MaterialTheme.colorScheme.surface,
                            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                "${if (isThai) f.labelTh else f.labelEn} ($count)",
                                Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                            )
                        }
                    }
                }
            }

            if (urgent.isNotEmpty()) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(Error))
                        Text(
                            if (isThai) "ใกล้หมดอายุ ต้องรีบปรุง (${urgent.size})" else "Use soon (${urgent.size})",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(if (isThai) "ภายใน 3 วัน" else "within 3 days", style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                }
                items(urgent, key = { "urgent-${it.id}" }) { item ->
                    PantryCard(
                        item = item, trial = trialOf(item.ingredientName), allergy = allergyOf(item.ingredientName),
                        isThai = isThai, today = today, urgentStripe = true,
                        onEdit = { editing = item },
                        onCook = { navController.navigate(Screen.RecommendedFood.route) },
                        onStartTrial = { vm.startFoodTrial(item.ingredientName) },
                        onDecide = { t, r -> deciding = t to r },
                        onRemove = { vm.removeFridgeIngredient(item.id, lang) }
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isThai) "คลังวัตถุดิบทั้งหมด" else "Everything in the pantry", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${shown.size}", style = MaterialTheme.typography.labelMedium, color = Outline)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(FoodGood, if (isThai) "ปลอดภัย" else "Safe")
                    LegendDot(Primary, if (isThai) "กำลังทดสอบ" else "Testing")
                    LegendDot(Outline, if (isThai) "ยังไม่ทดสอบ" else "Untested")
                    LegendDot(Error, if (isThai) "พบอาการแพ้" else "Reacted")
                }
            }

            if (shown.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (items.isEmpty()) (if (isThai) "ยังไม่มีวัตถุดิบในคลัง" else "The pantry is empty")
                            else (if (isThai) "ไม่พบรายการที่ตรงกับตัวกรอง" else "Nothing matches this filter"),
                            color = Outline, style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(shown, key = { it.id }) { item ->
                    PantryCard(
                        item = item, trial = trialOf(item.ingredientName), allergy = allergyOf(item.ingredientName),
                        isThai = isThai, today = today, urgentStripe = false,
                        onEdit = { editing = item },
                        onCook = { navController.navigate(Screen.RecommendedFood.route) },
                        onStartTrial = { vm.startFoodTrial(item.ingredientName) },
                        onDecide = { t, r -> deciding = t to r },
                        onRemove = { vm.removeFridgeIngredient(item.id, lang) }
                    )
                }
            }

            item { SafetyGuideCard(isThai) }
        }
    }

    if (adding) {
        IngredientDialog(
            isThai = isThai, existing = null,
            onDismiss = { adding = false },
            onSave = { name, qty, category, expires, storage, note ->
                vm.addFridgeIngredient(
                    name = name, quantity = qty, category = category, lang = lang,
                    expiresOn = expires, storage = storage, storageNote = null, note = note
                )
                adding = false
            }
        )
    }

    editing?.let { target ->
        IngredientDialog(
            isThai = isThai, existing = target,
            onDismiss = { editing = null },
            onSave = { name, qty, _, expires, storage, note ->
                vm.editFridgeIngredient(
                    target.id, name = name, quantity = qty, expiresOn = expires,
                    storage = storage, note = note, lang = lang
                )
                editing = null
            }
        )
    }

    deciding?.let { pair ->
        val trial = pair.first
        val result = pair.second
        var reaction by remember(trial.id, result) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { deciding = null },
            title = {
                Text(
                    if (result == "safe") (if (isThai) "บันทึกว่าปลอดภัย" else "Mark as safe")
                    else (if (isThai) "บันทึกว่าพบอาการแพ้" else "Mark as a reaction"),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (result == "safe")
                            (if (isThai) "${trial.foodName} ผ่านการเฝ้าดู ${PantrySafety.WATCH_DAYS} วันโดยไม่มีอาการใช่หรือไม่" else "${trial.foodName} went ${PantrySafety.WATCH_DAYS} days with no reaction?")
                        else (if (isThai) "บันทึกนี้จะเพิ่ม ${trial.foodName} เข้าประวัติแพ้ของ${child?.name ?: "น้อง"}ด้วย" else "This also adds ${trial.foodName} to ${child?.name ?: "the child"}'s allergy list"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (result == "allergic") {
                        OutlinedTextField(
                            value = reaction, onValueChange = { reaction = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(if (isThai) "อาการที่พบ" else "What happened") },
                            placeholder = { Text(if (isThai) "เช่น ผื่นแดงรอบปาก" else "e.g. rash around the mouth", color = Outline) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.decideFoodTrial(trial.id, trial.foodName, result, reaction.takeIf { it.isNotBlank() })
                    deciding = null
                }) { Text(if (isThai) "ยืนยัน" else "Confirm", color = if (result == "safe") FoodGood else Error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deciding = null }) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
        )
    }
}

private fun pantryAdvice(urgent: List<FridgeIngredient>, isThai: Boolean): String = when {
    urgent.isEmpty() ->
        if (isThai) "ยังไม่มีของใกล้หมดอายุ ใส่วันหมดอายุตอนบันทึกวัตถุดิบ แล้วระบบจะเตือนให้เองค่ะ"
        else "Nothing is near its date. Add an expiry when you log an ingredient and this card will warn you."
    else -> {
        val names = urgent.take(2).joinToString(", ") { it.ingredientName }
        if (isThai) "$names ใกล้หมดอายุแล้ว ลองใช้ทำมื้อถัดไปก่อนของอื่นค่ะ"
        else "$names should be cooked first — they are closest to their date."
    }
}

@Composable
private fun StatCell(modifier: Modifier, icon: ImageVector, value: String, label: String, tint: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun PantryCard(
    item: FridgeIngredient,
    trial: FoodTrial?,
    allergy: Allergy?,
    isThai: Boolean,
    today: LocalDate,
    urgentStripe: Boolean,
    onEdit: () -> Unit,
    onCook: () -> Unit,
    onStartTrial: () -> Unit,
    onDecide: (FoodTrial, String) -> Unit,
    onRemove: () -> Unit
) {
    // A recorded allergy outranks any trial: this child already reacted to it.
    val state = if (allergy != null) PantrySafety.TrialState.ALLERGIC
    else PantrySafety.trialState(trial?.result, trial?.startedOn, today)
    val badge: String
    val badgeColor: Color
    when (state) {
        PantrySafety.TrialState.SAFE -> { badge = if (isThai) "ปลอดภัย" else "Safe"; badgeColor = FoodGood }
        PantrySafety.TrialState.ALLERGIC -> { badge = if (isThai) "มีประวัติแพ้" else "Reacted"; badgeColor = Error }
        PantrySafety.TrialState.WATCHING -> {
            badge = PantrySafety.watchLabel(trial?.result, trial?.startedOn, isThai, today)
                ?: (if (isThai) "กำลังทดสอบ" else "Testing")
            badgeColor = Primary
        }
        PantrySafety.TrialState.DUE_TO_DECIDE -> { badge = if (isThai) "ครบกำหนด สรุปผลได้" else "Ready to judge"; badgeColor = Primary }
        PantrySafety.TrialState.UNTESTED -> { badge = if (isThai) "ยังไม่ทดสอบ" else "Untested"; badgeColor = Outline }
    }
    val days = PantrySafety.daysLeft(item.expiresOn, today)
    val freshness = PantrySafety.freshness(item.expiresOn, today)

    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            if (urgentStripe) {
                Box(Modifier.width(5.dp).fillMaxHeight().background(if (freshness == PantrySafety.Freshness.EXPIRED) Error else FoodWatch))
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.ingredientName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Surface(shape = RoundedCornerShape(100.dp), color = badgeColor.copy(0.14f)) {
                        Text(badge, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = badgeColor, maxLines = 1)
                    }
                }
                Text(
                    listOfNotNull(item.quantity, item.storage?.let { storageLabel(it, isThai) }, item.storageNote)
                        .joinToString(" • ").ifBlank { if (isThai) "ไม่ได้ระบุปริมาณ" else "No amount given" },
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.CalendarToday, null, tint = Outline, modifier = Modifier.size(13.dp))
                    Text(
                        when {
                            days == null -> if (isThai) "ยังไม่ได้ใส่วันหมดอายุ" else "No expiry date yet"
                            days < 0 -> if (isThai) "หมดอายุแล้ว ${-days} วัน" else "${-days} days past its date"
                            days == 0L -> if (isThai) "หมดอายุวันนี้" else "Expires today"
                            else -> if (isThai) "เหลืออีก $days วัน" else "$days days left"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (days == null || freshness == PantrySafety.Freshness.FINE) Outline else FoodWatch,
                        fontWeight = if (days != null && days <= 1) FontWeight.Bold else FontWeight.Normal
                    )
                }
                if (allergy != null) {
                    Surface(shape = RoundedCornerShape(10.dp), color = Error.copy(0.08f)) {
                        Text(
                            (if (isThai) "ห้ามนำมาปรุงให้เด็กคนนี้" else "Do not cook this for this child") +
                                (allergy.symptoms?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                            Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall, color = Error, fontWeight = FontWeight.Bold
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                        Text(if (isThai) "แก้ไข" else "Edit", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    when (state) {
                        PantrySafety.TrialState.ALLERGIC -> OutlinedButton(
                            onClick = onRemove, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Error), contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.DeleteOutline, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                            Text(if (isThai) "คัดแยกทิ้ง" else "Remove", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                        PantrySafety.TrialState.UNTESTED -> Button(
                            onClick = onStartTrial, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary), contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.Science, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                            Text(if (isThai) "เริ่มทดสอบ" else "Start test", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                        PantrySafety.TrialState.WATCHING, PantrySafety.TrialState.DUE_TO_DECIDE ->
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { trial?.let { onDecide(it, "safe") } }, modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.buttonColors(containerColor = FoodGood),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) { Text(if (isThai) "ปกติดี" else "Fine", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                                OutlinedButton(
                                    onClick = { trial?.let { onDecide(it, "allergic") } }, modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Error),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) { Text(if (isThai) "มีอาการ" else "Reacted", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                            }
                        PantrySafety.TrialState.SAFE -> Button(
                            onClick = onCook, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary), contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.Restaurant, null, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp))
                            Text(if (isThai) "ใช้ทำอาหาร" else "Cook with it", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

private fun storageLabel(storage: String, isThai: Boolean): String = when (storage) {
    "fridge" -> if (isThai) "ตู้เย็น 2-4°C" else "Fridge 2-4°C"
    "freezer" -> if (isThai) "ช่องแช่แข็ง -18°C" else "Freezer -18°C"
    "pantry" -> if (isThai) "ตู้เก็บแห้ง อุณหภูมิห้อง" else "Dry store, room temp"
    else -> storage
}

@Composable
private fun SafetyGuideCard(isThai: Boolean) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Shield, null, tint = Primary, modifier = Modifier.size(20.dp))
                Text(if (isThai) "คู่มือความปลอดภัยการจัดเก็บอาหารลูกน้อย" else "Storing a baby's food safely", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            listOf(
                (if (isThai) "กฎการทดสอบ 3-4 วัน" else "The 3-4 day wait rule") to
                    (if (isThai) "เริ่มอาหารใหม่ทีละอย่าง แล้วเว้น 3-4 วันก่อนเริ่มอย่างถัดไป โดยไม่ผสมของใหม่ชิ้นอื่น เพื่อให้รู้ว่าผื่นหรือการขับถ่ายที่ผิดปกติมาจากอะไร"
                     else "Start one new food at a time and wait 3-4 days before the next, without mixing in anything else new, so a rash or an upset stomach can be traced to one food."),
                (if (isThai) "อุณหภูมิที่ปลอดภัย" else "Safe temperatures") to
                    (if (isThai) "อาหารบดและนมสดควรเก็บต่ำกว่า 4°C และใช้ภายใน 24-48 ชั่วโมง หากต้องเก็บนานกว่านั้น ควรแบ่งเป็นส่วนแล้วแช่แข็งที่ -18°C ทันที"
                     else "Purées and fresh milk keep below 4°C for 24-48 hours. To keep them longer, portion them and freeze at -18°C straight away.")
            ).forEachIndexed { i, pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Primary.copy(0.15f)), contentAlignment = Alignment.Center) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Primary)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(pair.first, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(pair.second, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Add or edit one ingredient. Expiry is optional: unknown stays unknown rather than becoming today. */
@Composable
private fun IngredientDialog(
    isThai: Boolean,
    existing: FridgeIngredient?,
    onDismiss: () -> Unit,
    onSave: (name: String, quantity: String?, category: String, expiresOn: String?, storage: String?, note: String?) -> Unit
) {
    var name by remember { mutableStateOf(existing?.ingredientName ?: "") }
    var quantity by remember { mutableStateOf(existing?.quantity ?: "") }
    var category by remember { mutableStateOf(existing?.category ?: "general") }
    var storage by remember { mutableStateOf(existing?.storage ?: "fridge") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var expires by remember { mutableStateOf(existing?.expiresOn) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existing == null) (if (isThai) "เพิ่มวัตถุดิบ" else "Add an ingredient")
                else (if (isThai) "แก้ไขวัตถุดิบ" else "Edit ingredient"),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text(if (isThai) "ชื่อวัตถุดิบ" else "Name") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = quantity, onValueChange = { quantity = it }, singleLine = true,
                    label = { Text(if (isThai) "ปริมาณ" else "Amount") },
                    placeholder = { Text(if (isThai) "เช่น 150 กรัม, 4 ฟอง" else "e.g. 150 g, 4 eggs", color = Outline) },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                Text(if (isThai) "เก็บไว้ที่" else "Stored in", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("fridge", "freezer", "pantry").forEach { key ->
                        FilterChip(
                            selected = storage == key, onClick = { storage = key },
                            label = { Text(storageLabel(key, isThai), maxLines = 1) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(0.15f), selectedLabelColor = Primary)
                        )
                    }
                }
                if (existing == null) {
                    Text(if (isThai) "หมวด" else "Group", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "vegetable" to (if (isThai) "ผัก" else "Veg"),
                            "fruit" to (if (isThai) "ผลไม้" else "Fruit"),
                            "meat" to (if (isThai) "เนื้อสัตว์" else "Meat"),
                            "dairy" to (if (isThai) "นม" else "Dairy"),
                            "grain" to (if (isThai) "ข้าว/แป้ง" else "Grain"),
                            "general" to (if (isThai) "อื่นๆ" else "Other")
                        ).forEach { pair ->
                            FilterChip(
                                selected = category == pair.first, onClick = { category = pair.first },
                                label = { Text(pair.second, maxLines = 1) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Primary.copy(0.15f), selectedLabelColor = Primary)
                            )
                        }
                    }
                }
                Surface(onClick = { showPicker = true }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.4f), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CalendarToday, null, tint = Primary, modifier = Modifier.size(16.dp))
                        Text(
                            expires ?: (if (isThai) "เลือกวันหมดอายุ (ไม่บังคับ)" else "Pick an expiry date (optional)"),
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f)
                        )
                        if (expires != null) {
                            IconButton(onClick = { expires = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Filled.Close, if (isThai) "ล้างวันที่" else "Clear", tint = Outline, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it },
                    label = { Text(if (isThai) "บันทึกเพิ่มเติม" else "Note") },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), quantity.takeIf { it.isNotBlank() }, category, expires, storage, note.takeIf { it.isNotBlank() }) }
            ) { Text(if (isThai) "บันทึก" else "Save", color = Primary, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
    )

    if (showPicker) {
        val zone = java.time.ZoneId.systemDefault()
        val start = runCatching { LocalDate.parse(expires) }.getOrNull() ?: LocalDate.now().plusDays(3)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay(zone).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        expires = java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().toString()
                    }
                    showPicker = false
                }) { Text(if (isThai) "ตกลง" else "OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}
