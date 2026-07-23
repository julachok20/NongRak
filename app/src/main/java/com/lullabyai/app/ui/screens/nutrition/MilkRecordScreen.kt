package com.lullabyai.app.ui.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BabyChangingStation
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

private val QUICK_PICKS_ML = listOf(120, 150, 180, 210)

/** Milk feeding log — entry form (breast/formula, volume, side for breastfeeding) plus the history
 *  table the mockup itself didn't include (it was entry-form only, no history view). */
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
    var unit by remember { mutableStateOf("ml") } // ml | oz — mockup has a unit toggle, was missing entirely before
    var side by remember { mutableStateOf("both") }
    var saving by remember { mutableStateOf(false) }
    val mlPerOz = 29.5735f

    fun reload() { childId?.let { cid -> scope.launch { logs = repo.getMilkLogs(cid) } } }
    LaunchedEffect(childId) { reload() }

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "บันทึกการดื่มนม" else "Milk Record", onBack = onBack) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // Grouped pill container (outer border) holding both type buttons with icons —
                        // was two separate floating pills before, no icons.
                        Surface(
                            shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(Modifier.padding(4.dp)) {
                                listOf(
                                    "breast" to Pair(Icons.Filled.Favorite, if (isThai) "นมแม่" else "Breast milk"),
                                    "formula" to Pair(Icons.Filled.BabyChangingStation, if (isThai) "นมผง" else "Formula")
                                ).forEach { (key, pair) ->
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

                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    if (unit == "ml") "$volumeMl ml" else "%.1f oz".format(volumeMl / mlPerOz),
                                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Primary
                                )
                                Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Row {
                                        listOf("ml" to (if (isThai) "มล." else "ml"), "oz" to (if (isThai) "ออนซ์" else "oz")).forEach { (key, label) ->
                                            val selected = unit == key
                                            Surface(onClick = { unit = key }, shape = RoundedCornerShape(100.dp), color = if (selected) Primary else Color.Transparent) {
                                                Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                            if (unit == "ml") {
                                Slider(value = volumeMl.toFloat(), onValueChange = { volumeMl = it.toInt() }, valueRange = 30f..300f, steps = 26, colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    QUICK_PICKS_ML.forEach { ml ->
                                        Surface(onClick = { volumeMl = ml }, shape = RoundedCornerShape(10.dp), color = if (volumeMl == ml) Primary.copy(0.15f) else MaterialTheme.colorScheme.surfaceVariant) {
                                            Text("${ml}ml", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (volumeMl == ml) Primary else MaterialTheme.colorScheme.onSurfaceVariant)
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

                        if (milkType == "breast") {
                            Text(if (isThai) "ข้าง" else "Side", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    Triple("left", Icons.AutoMirrored.Filled.KeyboardArrowLeft, if (isThai) "ซ้าย" else "Left"),
                                    Triple("right", Icons.AutoMirrored.Filled.KeyboardArrowRight, if (isThai) "ขวา" else "Right"),
                                    Triple("both", Icons.Filled.SwapHoriz, if (isThai) "ทั้งสองข้าง" else "Both")
                                ).forEach { (key, icon, label) ->
                                    val selected = side == key
                                    // Selected = solid Primary fill (matches the mockup's bold
                                    // primary-container treatment), not a light tint.
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

                        Button(
                            onClick = {
                                val cid = childId ?: return@Button
                                saving = true
                                scope.launch {
                                    repo.addMilkLog(cid, milkType, volumeMl, if (milkType == "breast") side else null, null)
                                    saving = false
                                    reload()
                                    nutritionViewModel.load(lang)
                                }
                            },
                            enabled = !saving && childId != null,
                            modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(100.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) { Text(if (isThai) "บันทึกข้อมูล" else "Save", fontWeight = FontWeight.Bold) }
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
}

@Composable
private fun MilkLogRow(log: MilkLog, isThai: Boolean) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.BabyChangingStation, null, tint = Primary)
            Column(Modifier.weight(1f)) {
                Text(
                    (if (log.milkType == "breast") (if (isThai) "นมแม่" else "Breast milk") else (if (isThai) "นมผง" else "Formula")) +
                        (log.side?.let { " • ${if (isThai) (if (it == "left") "ซ้าย" else if (it == "right") "ขวา" else "ทั้งสองข้าง") else it}" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                )
                Text(
                    com.lullabyai.app.util.TimeUtils.localDate(log.loggedAt)?.let { d -> "$d ${com.lullabyai.app.util.TimeUtils.localTime(log.loggedAt) ?: ""}" } ?: "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(shape = RoundedCornerShape(100.dp), color = Primary.copy(0.1f)) {
                Text("${log.volumeMl} ml", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
