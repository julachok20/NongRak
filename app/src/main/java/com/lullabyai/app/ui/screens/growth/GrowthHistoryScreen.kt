package com.lullabyai.app.ui.screens.growth

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.GrowthRecord
import com.lullabyai.app.ui.components.AIInsightCard
import com.lullabyai.app.ui.components.LullabyTopBar
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.GrowthReference
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel

/** Age in months from birthdate to a given ISO date/instant — same simple year/month arithmetic as
 *  ChildViewModel.getAgeMonths, but for an arbitrary past record date rather than "now". */
fun ageMonthsAt(birthdate: String, recordedAt: String?): Int = try {
    val bParts = birthdate.split("-")
    val bYear = bParts[0].toInt(); val bMonth = bParts[1].toInt()
    val rDate = (recordedAt ?: "").take(10).split("-")
    if (rDate.size < 2) 0 else {
        val rYear = rDate[0].toInt(); val rMonth = rDate[1].toInt()
        ((rYear - bYear) * 12 + (rMonth - bMonth)).coerceAtLeast(0)
    }
} catch (e: Exception) { 0 }

/**
 * Combined weight/height history — rebuilt to match 24.Statics2/growth_history exactly: a profile
 * header, a latest-weight + latest-height bento pair (each with its own trend chip), then a
 * chronological history list where each record shows BOTH weight and height side by side with a
 * status chip and colored left border, plus an inline tip box on flagged (non-normal) records.
 * No toggle, no band chart on this screen anymore — those moved to the dedicated WHO weight/height
 * detail screens (WhoGrowthDetailScreens.kt), which is what GrowthBandChart below still serves.
 */
@Composable
fun GrowthHistoryScreen(
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
    val records = childState.growthRecords // newest-first
    val canDelete = childState.isChildOwner
    val ageMonthsNow = childViewModel.getAgeMonths()
    var recordToDelete by remember { mutableStateOf<GrowthRecord?>(null) }

    LaunchedEffect(child?.id, isThai) {
        val c = child ?: return@LaunchedEffect
        chatViewModel.loadGrowthInsight(c, isThai)
    }

    val latestWeight = records.firstOrNull { it.weightKg != null }
    val latestHeight = records.firstOrNull { it.heightCm != null }
    val weightTrend = latestWeight?.weightKg?.let { latest ->
        records.drop(records.indexOf(latestWeight) + 1).firstOrNull { it.weightKg != null }?.weightKg?.let { latest - it }
    }
    val heightTrend = latestHeight?.heightCm?.let { latest ->
        records.drop(records.indexOf(latestHeight) + 1).firstOrNull { it.heightCm != null }?.heightCm?.let { latest - it }
    }

    Scaffold(
        topBar = { LullabyTopBar(title = if (isThai) "ประวัติการเจริญเติบโต" else "Growth History", onBack = onBack, navController = navController) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            Box(Modifier.size(64.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                                val avatar = childState.childAvatarSignedUrl
                                if (!avatar.isNullOrBlank()) AsyncImage(model = avatar, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                                else Text("👶", fontSize = 28.sp)
                            }
                            Box(Modifier.size(20.dp).clip(CircleShape).background(Primary), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Stars, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                        Column {
                            Text(child?.name ?: (if (isThai) "ลูกของคุณ" else "Your child"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("$ageMonthsNow " + (if (isThai) "เดือน" else "months"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (childState.children.size > 1) {
                item {
                    Card(
                        Modifier.fillMaxWidth().clickable { navController.navigate(Screen.SiblingCompare.route) },
                        shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Primary)
                            Text(if (isThai) "เปรียบเทียบการเติบโตกับพี่น้อง" else "Compare growth with siblings", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Icon(Icons.Filled.Info, null, tint = Primary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    GrowthStatBentoCard(
                        icon = Icons.Filled.MonitorWeight, label = if (isThai) "น้ำหนักล่าสุด" else "Latest weight",
                        value = com.lullabyai.app.util.VitalsFormat.amount(latestWeight?.weightKg, isThai),
                        unit = if (latestWeight?.weightKg == null) "" else "kg",
                        trend = weightTrend, trendUnit = "kg",
                        color = Primary, modifier = Modifier.weight(1f)
                    )
                    GrowthStatBentoCard(
                        icon = Icons.Filled.Height, label = if (isThai) "ส่วนสูงล่าสุด" else "Latest height",
                        value = com.lullabyai.app.util.VitalsFormat.amount(latestHeight?.heightCm, isThai),
                        unit = if (latestHeight?.heightCm == null) "" else "cm",
                        trend = heightTrend, trendUnit = "cm",
                        color = Secondary, modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isThai) "บันทึกประวัติ" else "History", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { navController.navigate(Screen.AddGrowthRecord.route) }) {
                        Icon(Icons.Filled.AddCircle, null, tint = Primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (isThai) "เพิ่มข้อมูล" else "Add data", color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (records.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { Text(if (isThai) "ยังไม่มีบันทึก" else "No records yet", color = Outline) } }
            } else {
                items(records, key = { it.id }) { r ->
                    val ageMonths = child?.birthdate?.let { ageMonthsAt(it, r.recordedAt) } ?: 0
                    // Judged at the child's age on the day of this reading, for their sex (WHO).
                    val ageDays = GrowthReference.ageDaysAt(child?.birthdate, r.recordedAt)
                    val weightStatus = GrowthReference.weightStatus(r.weightKg, ageDays, child?.gender)
                    val heightStatus = GrowthReference.heightStatus(r.heightCm, ageDays, child?.gender)
                    // Row-level status/border keys off whichever metric is furthest from normal —
                    // a flagged weight shouldn't be hidden just because height that day was normal.
                    val severity = listOfNotNull(weightStatus, heightStatus).maxByOrNull {
                        when (it.color) { Color(0xFFC62828) -> 2; Color(0xFFEF6C00) -> 1; else -> 0 }
                    }
                    GrowthRecordRow(r, ageMonths, weightStatus, heightStatus, severity, isThai, canDelete) { recordToDelete = r }
                }
            }

            // Dedicated AI recommendation card (Stitch decision #2 / #13)
            item {
                AIInsightCard(
                    text = chatState.growthInsight ?: "",
                    isLoading = chatState.growthInsight == null
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }

    recordToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            icon = { Icon(Icons.Filled.Delete, null, tint = Error) },
            title = { Text(if (isThai) "ลบบันทึกนี้?" else "Delete this record?", fontWeight = FontWeight.Bold) },
            text = { Text(if (isThai) "ไม่สามารถย้อนกลับได้หลังจากลบ" else "This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { childViewModel.deleteGrowthRecord(target.id); recordToDelete = null }) {
                    Text(if (isThai) "ลบ" else "Delete", color = Error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { recordToDelete = null }) { Text(if (isThai) "ยกเลิก" else "Cancel") } }
        )
    }
}

@Composable
private fun GrowthStatBentoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, unit: String,
    trend: Double?, trendUnit: String, color: Color, modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(0.08f))
            .border(1.dp, color.copy(0.15f), RoundedCornerShape(20.dp))
    ) {
        Icon(icon, null, tint = color.copy(0.12f), modifier = Modifier.size(64.dp).align(Alignment.BottomEnd).offset(x = 8.dp, y = 8.dp))
        Column(Modifier.padding(18.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = color)
                Text(unit, style = MaterialTheme.typography.labelMedium, color = color, modifier = Modifier.padding(bottom = 4.dp))
            }
            trend?.let {
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(100.dp), color = Color(0xFF2E7D32).copy(0.1f)) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.AutoMirrored.Filled.TrendingUp, null, tint = Color(0xFF2E7D32), modifier = Modifier.size(12.dp))
                        Text((if (it >= 0) "+" else "") + "%.1f $trendUnit".format(it), style = MaterialTheme.typography.labelSmall, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun GrowthRecordRow(
    r: GrowthRecord, ageMonths: Int,
    weightStatus: GrowthReference.GrowthStatus?, heightStatus: GrowthReference.GrowthStatus?,
    severity: GrowthReference.GrowthStatus?, isThai: Boolean, canDelete: Boolean, onDelete: () -> Unit
) {
    val borderColor = severity?.color ?: Color(0xFF2E7D32)
    val flagged = severity != null && severity.color != Color(0xFF2E7D32)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(borderColor))
            Column(Modifier.padding(16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("$ageMonths " + (if (isThai) "เดือน" else "mo"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            severity?.let {
                                Surface(shape = RoundedCornerShape(100.dp), color = it.color.copy(0.12f)) {
                                    Text(if (isThai) it.labelTh else it.labelEn, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = it.color, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(com.lullabyai.app.util.TimeUtils.localDate(r.recordedAt) ?: "", style = MaterialTheme.typography.labelSmall, color = Outline)
                    }
                    if (canDelete) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Delete, null, tint = Outline, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    GrowthMiniStat(Icons.Filled.MonitorWeight, if (isThai) "น้ำหนัก" else "WEIGHT", r.weightKg?.let { "$it kg" } ?: "-", weightStatus?.color, Modifier.weight(1f))
                    GrowthMiniStat(Icons.Filled.Height, if (isThai) "ส่วนสูง" else "HEIGHT", r.heightCm?.let { "$it cm" } ?: "-", heightStatus?.color, Modifier.weight(1f))
                }
                if (flagged) {
                    Surface(shape = RoundedCornerShape(10.dp), color = severity.color.copy(0.1f)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Info, null, tint = severity.color, modifier = Modifier.size(14.dp))
                            Text(growthTip(severity, isThai), style = MaterialTheme.typography.labelSmall, color = severity.color, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GrowthMiniStat(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, tint: Color?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background((tint ?: Outline).copy(0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Outline)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tint ?: MaterialTheme.colorScheme.onSurface)
        }
    }
}

/** Short, generic guidance keyed off the flagged status — deliberately NOT a live/per-record AI
 *  call (that would mean one AI request per flagged history row just to render a list). Honest
 *  rule-based text, not claimed as AI-generated. */
private fun growthTip(status: GrowthReference.GrowthStatus, isThai: Boolean): String = when (status.color) {
    Color(0xFFC62828) -> if (isThai) "คำแนะนำ: ค่านี้ห่างจากเกณฑ์ WHO ตามอายุมาก ตรวจสอบตัวเลขที่บันทึก และควรพบแพทย์เพื่อตรวจประเมิน"
        else "Tip: This is far from the WHO range for this age — check the entry and see a doctor for an assessment."
    else -> if (isThai) "คำแนะนำ: ค่านี้อยู่นอกเกณฑ์ WHO ตามอายุ ติดตามต่อ และปรึกษาแพทย์หากครั้งถัดไปยังไม่เข้าเกณฑ์"
        else "Tip: This is outside the WHO range for this age — keep measuring and consult a doctor if the next reading is still outside."
}

@Composable
fun GrowthBandChart(birthdate: String?, records: List<GrowthRecord>, isWeight: Boolean, gender: String? = null) {
    val chronological = records.reversed() // oldest first for left-to-right plotting
    val points = chronological.mapNotNull { r ->
        val v = if (isWeight) r.weightKg else r.heightCm
        val age = birthdate?.let { ageMonthsAt(it, r.recordedAt) }
        if (v != null && age != null) age to v else null
    }
    if (points.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            Text("-", color = Outline)
        }
        return
    }
    val minAge = points.minOf { it.first }; val maxAge = (points.maxOf { it.first }).coerceAtLeast(minAge + 1)
    // WHO −2 SD … +2 SD for this sex at each month plotted; months past 5 years have no band.
    val bands = (minAge..maxAge).associateWith { GrowthReference.band(GrowthReference.monthsToDays(it), isWeight, gender) }
    val bandMax = bands.values.filterNotNull().maxOfOrNull { it.second } ?: points.maxOf { it.second }
    val bandMin = bands.values.filterNotNull().minOfOrNull { it.first } ?: points.minOf { it.second }
    val valueMax = maxOf(bandMax, points.maxOf { it.second }) * 1.05
    val valueMin = minOf(bandMin, points.minOf { it.second }) * 0.95

    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
        fun x(age: Int) = size.width * (age - minAge).toFloat() / (maxAge - minAge).toFloat()
        fun y(v: Double) = size.height * (1f - ((v - valueMin) / (valueMax - valueMin)).toFloat())

        // Reference band (min/max lines across the plotted age range)
        val bandTop = Path(); val bandBottom = Path()
        var started = false
        for (age in minAge..maxAge) {
            val (bMin, bMax) = bands[age] ?: continue
            val px = x(age)
            if (!started) { bandTop.moveTo(px, y(bMax)); bandBottom.moveTo(px, y(bMin)); started = true }
            else { bandTop.lineTo(px, y(bMax)); bandBottom.lineTo(px, y(bMin)) }
        }
        drawPath(bandTop, color = Primary.copy(0.3f), style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
        drawPath(bandBottom, color = Primary.copy(0.3f), style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))

        // Child's actual line
        val childPath = Path()
        points.forEachIndexed { i, (age, v) ->
            val px = x(age); val py = y(v)
            if (i == 0) childPath.moveTo(px, py) else childPath.lineTo(px, py)
            drawCircle(color = Primary, radius = 4.dp.toPx(), center = Offset(px, py))
        }
        drawPath(childPath, color = Primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}
