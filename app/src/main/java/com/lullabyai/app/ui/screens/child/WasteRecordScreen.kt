package com.lullabyai.app.ui.screens.child

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.lullabyai.app.data.model.WasteRecord
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import java.time.LocalDate

private val CONSISTENCY = listOf(
    "soft_normal" to ("นิ่ม/ปกติ" to "Soft/Normal"), "hard_constipated" to ("แข็ง/ท้องผูก" to "Hard/Constipated"),
    "loose_diarrhea" to ("เหลว/ท้องเสีย" to "Loose/Diarrhea"), "mucus_blood" to ("มีมูกเลือด" to "Mucus/Blood")
)
// Hex values matched to the mockup's actual swatches (yellow/grey were off in the first pass).
private val COLORS = listOf(
    "yellow" to (Color(0xFFFFEB3B) to "เหลือง"), "brown" to (Color(0xFF795548) to "น้ำตาล"),
    "green" to (Color(0xFF4CAF50) to "เขียว"), "red_orange" to (Color(0xFFFF5722) to "แดง/ส้ม"), "grey_white" to (Color(0xFFBDBDBD) to "เทา/ขาว")
)
private val AMOUNTS = listOf("low" to ("น้อย" to "Low"), "medium" to ("ปานกลาง" to "Medium"), "high" to ("มาก" to "High"))

private fun consistencyLabel(key: String?, isThai: Boolean) = CONSISTENCY.find { it.first == key }?.second?.let { if (isThai) it.first else it.second } ?: "-"
private fun colorLabel(key: String?, isThai: Boolean) = COLORS.find { it.first == key }?.second?.second ?: key ?: "-"
private fun colorSwatch(key: String?) = COLORS.find { it.first == key }?.second?.first ?: Outline
private fun amountLabel(key: String, isThai: Boolean) = AMOUNTS.find { it.first == key }?.second?.let { if (isThai) it.first else it.second } ?: key

@Composable
fun WasteRecordScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController,
    onBack: () -> Unit
) {
    val s = com.lullabyai.app.ui.localization.LocalStrings.current
    val isThai = s.isThai
    val childState by childViewModel.uiState.collectAsState()
    val chatState by chatViewModel.uiState.collectAsState()
    val child = childState.child
    LaunchedEffect(Unit) { childViewModel.loadWasteRecords() }

    val recentWaste = remember(childState.wasteRecords) { childState.wasteRecords.take(12) }
    LaunchedEffect(child?.id, recentWaste) {
        child?.let { c ->
            if (recentWaste.isNotEmpty()) {
                val summary = recentWaste.joinToString("\n") { r ->
                    val type = if (r.wasteType == "urine") "urine" else "stool"
                    val cons = r.consistency ?: "-"
                    val col = r.color ?: "-"
                    val amt = r.amount
                    val whenStr = TimeUtils.localDate(r.recordedAt) ?: ""
                    "$whenStr · $type · consistency=$cons · color=$col · amount=$amt"
                }
                chatViewModel.loadWasteInsight(c, summary, isThai)
            }
        }
    }

    var wasteType by remember { mutableStateOf("stool") }
    var consistency by remember { mutableStateOf<String?>(null) }
    var color by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("medium") }
    val logsToday = childState.wasteRecords.count {
        TimeUtils.localDate(it.recordedAt) == LocalDate.now().toString()
    }

    Scaffold(
        topBar = {
            LullabyTopBar(title = if (isThai) "บันทึกการขับถ่าย" else "Waste Record", onBack = onBack, actions = {
                TextButton(onClick = {
                    childViewModel.addWasteRecord(wasteType, consistency, color, amount, null)
                    consistency = null; color = null; amount = "medium"
                }) { Text(if (isThai) "บันทึก" else "Save", color = Primary, fontWeight = FontWeight.Bold) }
            })
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(if (isThai) "บันทึกด่วน" else "Quick log", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

                        // Type — icon cards (2-col grid), matches the mockup's card treatment more
                        // closely than a plain text toggle.
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(
                                "urine" to Triple(Icons.Filled.WaterDrop, if (isThai) "ปัสสาวะ" else "Urine", Color(0xFF2196F3)),
                                "stool" to Triple(Icons.Filled.Circle, if (isThai) "อุจจาระ" else "Stool", Color(0xFF795548))
                            ).forEach { (key, triple) ->
                                val (icon, label, tint) = triple
                                val selected = wasteType == key
                                TypeCard(icon, label, tint, selected, Modifier.weight(1f)) { wasteType = key; if (key == "urine") consistency = null }
                            }
                        }

                        if (wasteType == "stool") {
                            Text(if (isThai) "ลักษณะ" else "Consistency", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            CONSISTENCY.chunked(2).forEach { rowItems ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                                    rowItems.forEach { (key, labels) ->
                                        val selected = consistency == key
                                        OutlinedChip(if (isThai) labels.first else labels.second, selected, Modifier.weight(1f)) { consistency = key }
                                    }
                                }
                            }
                        }

                        Text(if (isThai) "สี" else "Color", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            COLORS.forEach { (key, pair) ->
                                val selected = color == key
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(
                                        Modifier.size(40.dp).clip(CircleShape).background(pair.first)
                                            .then(if (selected) Modifier.border(2.dp, Primary, CircleShape).padding(3.dp) else Modifier)
                                            .clickable { color = key }
                                    )
                                    Text(pair.second, style = MaterialTheme.typography.labelSmall, color = if (selected) Primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }

                        Text(if (isThai) "ปริมาณ" else "Amount", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AMOUNTS.forEach { (key, labels) ->
                                val selected = amount == key
                                Surface(
                                    onClick = { amount = key }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                                    color = if (selected) Primary.copy(0.15f) else MaterialTheme.colorScheme.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Primary else MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(if (isThai) labels.first else labels.second, modifier = Modifier.padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (selected) Primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            item {
                AIInsightCard(
                    text = chatState.wasteInsight.orEmpty(),
                    isLoading = recentWaste.isNotEmpty() && chatState.wasteInsight == null,
                    title = if (isThai) "คำแนะนำจาก NongRak AI" else "NongRak AI Insight",
                    emptyMessage = when {
                        recentWaste.isEmpty() ->
                            if (isThai) "บันทึกการขับถ่ายสัก 2–3 ครั้ง เพื่อให้ AI วิเคราะห์แนวโน้มและจุดที่ควรเฝ้าระวัง"
                            else "Log a few elimination entries so AI can spot trends and what to watch for"
                        logsToday > 0 ->
                            if (isThai) "วันนี้บันทึกแล้ว $logsToday ครั้ง — กำลังวิเคราะห์…"
                            else "$logsToday logs today — analyzing…"
                        else -> null
                    }
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isThai) "ประวัติ" else "History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }

            if (childState.wasteRecords.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { Text(if (isThai) "ยังไม่มีบันทึก" else "No records yet", color = Outline) } }
            } else {
                items(childState.wasteRecords, key = { it.id }) { r -> WasteRecordRow(r, isThai) }
            }
        }
    }
}

@Composable
private fun TypeCard(icon: ImageVector, label: String, tint: Color, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = if (selected) tint.copy(0.14f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) tint else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(vertical = 14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(tint.copy(0.15f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (selected) tint else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun OutlinedChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(100.dp),
        color = if (selected) Primary.copy(0.14f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.labelSmall, color = if (selected) Primary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun WasteRecordRow(r: WasteRecord, isThai: Boolean) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(colorSwatch(r.color).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(if (r.wasteType == "urine") Icons.Filled.WaterDrop else Icons.Filled.Circle, null, tint = colorSwatch(r.color), modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (r.wasteType == "urine") (if (isThai) "ปัสสาวะ" else "Urine") else (if (isThai) "อุจจาระ" else "Stool"),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold
                )
                Text(
                    com.lullabyai.app.util.TimeUtils.localDate(r.recordedAt)?.let { d ->
                        val t = com.lullabyai.app.util.TimeUtils.localTime(r.recordedAt)
                        val day = if (d == java.time.LocalDate.now().toString()) (if (isThai) "วันนี้" else "Today") else d
                        if (t != null) (if (isThai) "$t น. • $day" else "$t • $day") else day
                    } ?: "",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Stool: consistency+color is the meaningful signal. Urine has no consistency and color
            // is optional, so fall back to amount instead of risking a bare "-" badge.
            Surface(shape = RoundedCornerShape(100.dp), color = colorSwatch(r.color).copy(alpha = 0.15f)) {
                Text(
                    if (r.wasteType == "stool") (r.consistency?.let { "${consistencyLabel(it, isThai)} • " } ?: "") + colorLabel(r.color, isThai)
                    else amountLabel(r.amount, isThai),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
