@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.lullabyai.app.data.model.SuggestionItem
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.child.ChildScreenScaffold
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.ActivityGuide
import com.lullabyai.app.viewmodel.NutritionViewModel

private val Burn = Color(0xFFE65100)
private val Good = Color(0xFF00796B)

/**
 * Active burn — Stitch "เป้าหมายการเผาผลาญวันนี้", and the other half of the nutrition pages: what
 * the child ate is on the nutrition hub, what they moved is here, and the balance card reads both.
 *
 * The goal on this screen is MINUTES, from the WHO under-five guidance ([ActivityGuide]). Calories
 * burned appear beside it as an estimate of activities someone logged — a phone cannot measure a
 * crawling baby — and are never used to suggest the child should eat less.
 */
@Composable
fun ExerciseScreen(vm: NutritionViewModel, lang: String, navController: NavController) {
    val ui by vm.ui.collectAsState()
    val isThai = lang.startsWith("th")
    LaunchedEffect(lang) {
        if (ui.child == null) vm.load(lang) else vm.ensureSuggestions(lang)
    }
    LaunchedEffect(ui.foods, ui.todayMilk, lang) { vm.loadDayNutrition(lang) }

    val months = vm.ageMonths()
    val goalMinutes = ActivityGuide.dailyMinutesGoal(months)
    val todayLogs = ui.exerciseLogs
    val minutesToday = todayLogs.sumOf { it.durationMin ?: 0 }
    val burnedToday = todayLogs.sumOf { it.caloriesBurned ?: 0 }
    val progress = ActivityGuide.progress(minutesToday, goalMinutes)

    val intake = vm.dayIntakeKcal() ?: ui.foods.sumOf { it.calories ?: 0 }
    val targetKcal = ui.dri?.energyKcal ?: ui.targetKcal
    val balance = ActivityGuide.balance(intake, targetKcal, minutesToday, goalMinutes)
    val completedNames = todayLogs.map { it.activityName }.toSet()

    ChildScreenScaffold(
        title = if (isThai) "การเผาผลาญและกิจกรรม" else "Activity and burn",
        currentTab = "exercise",
        navController = navController
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            item {
                GoalCard(
                    months = months, minutesToday = minutesToday, goalMinutes = goalMinutes,
                    burnedToday = burnedToday, sessions = todayLogs.size, progress = progress, isThai = isThai
                )
            }

            item {
                BalanceCard(
                    balance = balance, intake = intake, targetKcal = targetKcal,
                    minutesToday = minutesToday, goalMinutes = goalMinutes, isThai = isThai,
                    onOpenNutrition = { navController.navigate(Screen.Nutrition.route) }
                )
            }

            item {
                Surface(shape = RoundedCornerShape(16.dp), color = Primary.copy(0.06f)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Primary, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (isThai) "บทวิเคราะห์จาก NongRak AI" else "NongRak AI", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Primary)
                                Surface(shape = RoundedCornerShape(100.dp), color = MaterialTheme.colorScheme.surface) {
                                    Text(
                                        if (isThai) "วัย $months เดือน" else "$months months",
                                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall, color = Primary, fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(activityInsight(months, minutesToday, goalMinutes, isThai), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { navController.navigate(Screen.LogExercise.route) },
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Icon(Icons.Filled.AddCircleOutline, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (isThai) "บันทึกกิจกรรมการเผาผลาญ" else "Record an activity", fontWeight = FontWeight.Bold)
                }
            }

            if (todayLogs.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (isThai) "กิจกรรมวันนี้ (${todayLogs.size})" else "Today (${todayLogs.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { navController.navigate(Screen.ExerciseHistory.route) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(if (isThai) "ดูย้อนหลัง" else "History", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                items(todayLogs, key = { it.id }) { log ->
                    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Burn.copy(0.12f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.DirectionsRun, null, tint = Burn, modifier = Modifier.size(19.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(log.activityName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(
                                        log.durationMin?.let { if (isThai) "$it นาที" else "$it min" },
                                        log.caloriesBurned?.let { if (isThai) "ประมาณ $it kcal" else "~$it kcal" }
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall, color = Outline
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(if (isThai) "กิจกรรมที่แนะนำตามวัย" else "Suggested for this age", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            if (isThai) "เล่นได้ที่บ้าน ไม่ต้องใช้อุปกรณ์พิเศษ" else "Playable at home, nothing special needed",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { vm.ensureSuggestions(lang, force = true) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(if (isThai) "ขอใหม่" else "Refresh", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (ui.activitiesLoading && ui.activities.isEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Primary)
                        Text(if (isThai) "กำลังคิดกิจกรรมที่เหมาะกับวัยนี้..." else "Finding activities for this age...", style = MaterialTheme.typography.bodySmall, color = Outline)
                    }
                }
            } else if (ui.activities.isEmpty()) {
                item {
                    Text(
                        if (isThai) "ยังไม่มีรายการแนะนำ แตะ ขอใหม่ เพื่อให้ AI คิดให้" else "No suggestions yet — tap Refresh",
                        style = MaterialTheme.typography.bodySmall, color = Outline
                    )
                }
            } else {
                items(ui.activities, key = { it.title }) { act ->
                    ActivityCard(
                        act = act, done = act.title in completedNames, isThai = isThai,
                        onOpen = { vm.loadExercise(act.title, lang); navController.navigate(Screen.ExerciseInstruction.route) }
                    )
                }
            }

            item { MetabolismNote(isThai) }
        }
    }
}

@Composable
private fun GoalCard(
    months: Int, minutesToday: Int, goalMinutes: Int, burnedToday: Int, sessions: Int,
    progress: ActivityGuide.Progress, isThai: Boolean
) {
    val share = if (goalMinutes > 0) minutesToday.toFloat() / goalMinutes else 0f
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.6f)) {
                        Text(
                            if (isThai) "กิจกรรมประจำวัน" else "DAILY ACTIVITY",
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(if (isThai) "เป้าหมายการขยับวันนี้" else "Today's movement goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.size(46.dp).clip(CircleShape).background(Burn.copy(0.12f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = Burn, modifier = Modifier.size(24.dp))
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text("$minutesToday", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Primary)
                Text(
                    if (isThai) " / $goalMinutes นาที" else " / $goalMinutes min",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Text("${(share * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Primary)
            }
            LinearProgressIndicator(
                progress = { share.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(100.dp)),
                color = Primary, trackColor = Primary.copy(0.15f)
            )
            Text(ActivityGuide.goalLabel(months, isThai), style = MaterialTheme.typography.labelSmall, color = Outline)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCell(
                    Modifier.weight(1f), Icons.Filled.DirectionsRun, if (isThai) "กิจกรรมที่บันทึก" else "Logged sessions",
                    if (isThai) "$sessions ครั้ง" else "$sessions", Primary
                )
                StatCell(
                    Modifier.weight(1f), Icons.Filled.LocalFireDepartment, if (isThai) "ประมาณการเผาผลาญ" else "Estimated burn",
                    if (burnedToday > 0) "~$burnedToday kcal" else "—", Burn
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    when (progress) {
                        ActivityGuide.Progress.MET -> Icons.Filled.CheckCircle
                        ActivityGuide.Progress.NONE_YET -> Icons.Filled.Info
                        else -> Icons.Filled.TrendingUp
                    },
                    null, tint = if (progress == ActivityGuide.Progress.MET) Good else Primary, modifier = Modifier.size(15.dp)
                )
                Text(
                    when (progress) {
                        ActivityGuide.Progress.MET -> if (isThai) "ครบเป้าหมายของวันนี้แล้ว" else "Today's goal is met"
                        ActivityGuide.Progress.ALMOST -> if (isThai) "ใกล้ครบแล้ว เหลืออีก ${goalMinutes - minutesToday} นาที" else "${goalMinutes - minutesToday} minutes to go"
                        ActivityGuide.Progress.STARTED -> if (isThai) "เริ่มแล้ว เหลืออีก ${goalMinutes - minutesToday} นาที" else "Started — ${goalMinutes - minutesToday} minutes to go"
                        ActivityGuide.Progress.NONE_YET -> if (isThai) "ยังไม่ได้บันทึกกิจกรรมวันนี้" else "Nothing logged today yet"
                    },
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
                    color = if (progress == ActivityGuide.Progress.MET) Good else Primary
                )
            }
        }
    }
}

@Composable
private fun StatCell(modifier: Modifier, icon: ImageVector, label: String, value: String, tint: Color) {
    Surface(modifier = modifier, shape = RoundedCornerShape(14.dp), color = tint.copy(0.07f)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

/** The link the nutrition pages were missing: what went in, beside what moved. */
@Composable
private fun BalanceCard(
    balance: ActivityGuide.Balance, intake: Int, targetKcal: Int,
    minutesToday: Int, goalMinutes: Int, isThai: Boolean, onOpenNutrition: () -> Unit
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Primary)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Insights, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Text(if (isThai) "กินและขยับวันนี้" else "Eating and moving today", Modifier.weight(1f), color = Color.White.copy(0.9f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Text(
                when (balance) {
                    ActivityGuide.Balance.BOTH_GOOD -> if (isThai) "สมดุลดีเยี่ยม" else "Nicely balanced"
                    ActivityGuide.Balance.MOVE_MORE -> if (isThai) "กินได้ดี ขยับเพิ่มอีกนิด" else "Eating well — a little more play"
                    ActivityGuide.Balance.EAT_MORE -> if (isThai) "ขยับได้ดี ทานเพิ่มอีกหน่อย" else "Moving well — a little more food"
                    ActivityGuide.Balance.EARLY -> if (isThai) "วันนี้เพิ่งเริ่ม" else "The day is still young"
                    ActivityGuide.Balance.NO_DATA -> if (isThai) "ยังไม่มีข้อมูลพอ" else "Not enough logged yet"
                },
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White
            )
            Text(
                when (balance) {
                    ActivityGuide.Balance.BOTH_GOOD -> if (isThai) "ทั้งอาหารและการเคลื่อนไหวอยู่ในเกณฑ์ของวัยนี้" else "Both food and movement are where they should be for this age"
                    ActivityGuide.Balance.MOVE_MORE -> if (isThai) "ลองเพิ่มเวลาเล่นบนพื้นอีกสักช่วง ไม่ต้องลดอาหาร" else "Add another spell of floor play — no need to cut food"
                    ActivityGuide.Balance.EAT_MORE -> if (isThai) "ขยับดีแล้ว ลองเสริมมื้อว่างที่มีประโยชน์" else "Movement is good; a nourishing snack would help"
                    ActivityGuide.Balance.EARLY -> if (isThai) "บันทึกเพิ่มระหว่างวัน แล้วค่อยดูภาพรวมอีกครั้ง" else "Log more through the day and check back"
                    ActivityGuide.Balance.NO_DATA -> if (isThai) "บันทึกอาหารและกิจกรรมเพื่อดูภาพรวมของวัน" else "Log food and activity to see the day"
                },
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.85f)
            )
            HorizontalDivider(color = Color.White.copy(0.25f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (isThai) "อาหารวันนี้" else "Eaten", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.75f))
                    Text(
                        if (targetKcal > 0) "%,d / %,d kcal".format(intake, targetKcal) else "%,d kcal".format(intake),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(if (isThai) "เวลาขยับ" else "Moved", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.75f))
                    Text(
                        if (isThai) "$minutesToday / $goalMinutes นาที" else "$minutesToday / $goalMinutes min",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White
                    )
                }
            }
            Surface(onClick = onOpenNutrition, shape = RoundedCornerShape(12.dp), color = Color.White.copy(0.15f), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Restaurant, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    Text(if (isThai) "ดูหน้าโภชนาการ" else "Open nutrition", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(act: SuggestionItem, done: Boolean, isThai: Boolean, onOpen: () -> Unit) {
    val minutes = act.tags.firstOrNull { it.contains("min", true) || it.contains("นาที") }
    val kcal = act.tags.firstOrNull { it.contains("kcal", true) || it.contains("แคล") }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Primary.copy(0.08f)), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = com.lullabyai.app.ui.screens.nutrition.foodImageUrl(act.title),
                    contentDescription = act.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(act.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOfNotNull(kcal, minutes).forEach { tag ->
                        Surface(shape = RoundedCornerShape(6.dp), color = Primary.copy(0.1f)) {
                            Text(tag, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Primary, maxLines = 1)
                        }
                    }
                }
                if (act.subtitle.isNotBlank()) {
                    Text(act.subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Box(Modifier.size(38.dp).clip(CircleShape).background(if (done) Good.copy(0.15f) else Primary.copy(0.12f)), contentAlignment = Alignment.Center) {
                Icon(
                    if (done) Icons.Filled.Check else Icons.Filled.PlayArrow,
                    if (isThai) "ดูวิธีเล่น" else "How to play",
                    tint = if (done) Good else Primary, modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun MetabolismNote(isThai: Boolean) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(0.35f)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.MonitorHeart, null, tint = Primary, modifier = Modifier.size(18.dp))
                Text(
                    if (isThai) "ระบบเผาผลาญในเด็กเล็กทำงานอย่างไร" else "How a small child's metabolism works",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                )
            }
            Text(
                if (isThai) "เด็กเล็กมีอัตราการเผาผลาญพื้นฐานต่อน้ำหนักตัวสูงกว่าผู้ใหญ่หลายเท่า เพราะร่างกายกำลังสร้างเนื้อเยื่อและสมองอย่างรวดเร็ว พลังงานส่วนใหญ่จึงถูกใช้ไปกับการเติบโต ไม่ใช่การเคลื่อนไหว"
                else "A small child's resting metabolism, per kilo, runs several times an adult's, because the body is building tissue and brain quickly. Most of that energy goes into growing, not into moving.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (isThai) "การขยับจึงวัดกันที่เวลาเล่น ไม่ใช่แคลอรี่ที่เผา และไม่ควรลดอาหารเพื่อให้ตัวเลขสองฝั่งเท่ากัน หากกังวลเรื่องน้ำหนัก ควรปรึกษากุมารแพทย์"
                else "So movement is measured in play time, not calories burned, and food should never be cut to make two numbers match. If weight worries you, ask a paediatrician.",
                style = MaterialTheme.typography.labelSmall, color = Outline
            )
        }
    }
}

private fun activityInsight(months: Int, minutesToday: Int, goalMinutes: Int, isThai: Boolean): String = when {
    months < 12 && minutesToday <= 0 ->
        if (isThai) "วัยนี้เน้นการนอนคว่ำและเล่นบนพื้นสั้นๆ หลายรอบต่อวัน ครั้งละ 3-5 นาทีก็นับ ช่วยให้คอ ไหล่ และหลังแข็งแรงพอสำหรับการคลานค่ะ"
        else "At this age short spells of tummy time count — three to five minutes at a go builds the neck, shoulders and back for crawling."
    months < 12 ->
        if (isThai) "บันทึกได้ $minutesToday นาทีแล้ว แบ่งเป็นช่วงสั้นๆ หลายรอบดีกว่ารอบเดียวยาวๆ และหยุดทันทีเมื่อลูกงอแงค่ะ"
        else "$minutesToday minutes so far. Several short spells beat one long one, and stop as soon as the baby fusses."
    minutesToday >= goalMinutes ->
        if (isThai) "ครบ $goalMinutes นาทีแล้ววันนี้ การเล่นที่ได้ขยับแขนขาเต็มที่ช่วยให้กินได้ดีขึ้นและหลับสนิทขึ้นในตอนกลางคืนค่ะ"
        else "That's $goalMinutes minutes today. Full-body play tends to improve both appetite and night sleep."
    else ->
        if (isThai) "เหลืออีก ${goalMinutes - minutesToday} นาที ลองแบ่งเป็นช่วงสั้นๆ 10-15 นาทีหลายรอบระหว่างวัน จะง่ายกว่าการเล่นยาวรอบเดียวค่ะ"
        else "${goalMinutes - minutesToday} minutes to go — several ten to fifteen minute spells are easier than one long session."
}
