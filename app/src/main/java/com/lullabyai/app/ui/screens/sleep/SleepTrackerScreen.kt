@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.lullabyai.app.ui.screens.sleep

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.lullabyai.app.data.model.SleepLog
import com.lullabyai.app.data.sleep.SleepSoundEvent
import com.lullabyai.app.data.sleep.SleepWakeWindows
import com.lullabyai.app.data.VaccineSetupCatalog
import com.lullabyai.app.ui.localization.LocalStrings
import com.lullabyai.app.ui.navigation.Screen
import com.lullabyai.app.ui.screens.child.ChildScreenScaffold
import com.lullabyai.app.ui.theme.*
import com.lullabyai.app.util.NoiseTrack
import com.lullabyai.app.util.TimeUtils
import com.lullabyai.app.util.WhiteNoisePlayer
import com.lullabyai.app.viewmodel.ChatViewModel
import com.lullabyai.app.viewmodel.ChildViewModel
import com.lullabyai.app.viewmodel.SleepMonitorViewModel
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.launch

// Stitch sleep design tokens now live in SleepDesignTokens.kt (shared across the package).

/**
 * ponytail: the Stitch hero has the metric cards overlapping its rounded bottom edge
 * (`-mt-8`). Compose has no negative padding, so shrink the reported height of the
 * hero+metrics block instead of restructuring the list into an overlay.
 */
private fun Modifier.shrinkBottom(amount: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val h = (placeable.height - amount.roundToPx()).coerceAtLeast(0)
    layout(placeable.width, h) { placeable.place(0, 0) }
}

/** "HH:mm" -> minutes since midnight, or null when unparseable. */
private fun clockMinutes(hhmm: String?): Int? {
    val parts = hhmm?.split(":") ?: return null
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

@Composable
fun SleepTrackerScreen(
    childViewModel: ChildViewModel,
    chatViewModel: ChatViewModel,
    navController: NavController,
    sleepMonitorViewModel: SleepMonitorViewModel = viewModel()
) {
    val s = LocalStrings.current
    val state by childViewModel.uiState.collectAsState()
    val canDelete = state.isChildOwner
    val chatState by chatViewModel.uiState.collectAsState()
    val monitor by sleepMonitorViewModel.uiState.collectAsState()
    val active = state.activeSleepSession
    val logs = state.sleepLogs
    val child = state.child
    val context = LocalContext.current

    LaunchedEffect(state.child?.id) { childViewModel.loadSleep() }
    LaunchedEffect(child, logs.size) { child?.let { chatViewModel.loadSleepInsight(it, logs, s.isThai) } }
    LaunchedEffect(child?.id, active?.id) {
        sleepMonitorViewModel.bind(child?.id, active?.id)
    }

    val noisePlayer = remember { WhiteNoisePlayer() }
    DisposableEffect(Unit) {
        onDispose {
            noisePlayer.release()
            sleepMonitorViewModel.stopMonitoring()
        }
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMicPermission = granted
        if (granted) sleepMonitorViewModel.startMonitoring()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(active?.id) {
        if (active == null) return@LaunchedEffect
        while (true) { kotlinx.coroutines.delay(30_000); tick++ }
    }

    fun getEffectiveSleepDate(log: SleepLog): LocalDate? {
        val endLocalDate = log.endTime?.let { TimeUtils.parseInstant(it)?.atZone(ZoneId.systemDefault())?.toLocalDate() }
        val startLocalDate = TimeUtils.parseInstant(log.startTime)?.atZone(ZoneId.systemDefault())?.toLocalDate()
        return endLocalDate ?: startLocalDate
    }

    val elapsed = active?.let { TimeUtils.parseInstant(it.startTime)?.let { st -> Duration.between(st, Instant.now()) } }
    val today = LocalDate.now()
    val todayLogs = logs.filter { getEffectiveSleepDate(it) == today }
    val totalMinutesToday = todayLogs.sumOf { log ->
        val start = TimeUtils.parseInstant(log.startTime)
        val end = log.endTime?.let { TimeUtils.parseInstant(it) } ?: Instant.now()
        if (start != null) Duration.between(start, end).toMinutes().coerceAtLeast(0) else 0L
    }
    val targetHours = childViewModel.sleepTargetHours()
    val targetMinutes = targetHours * 60

    val last7Start = today.minusDays(6)
    val last7Logs = logs.filter { log ->
        val d = getEffectiveSleepDate(log)
        d != null && !d.isBefore(last7Start) && !d.isAfter(today)
    }
    val weeklyTotalMinutes = last7Logs.sumOf { log ->
        val start = TimeUtils.parseInstant(log.startTime)
        val end = log.endTime?.let { TimeUtils.parseInstant(it) } ?: Instant.now()
        if (start != null) Duration.between(start, end).toMinutes().coerceAtLeast(0) else 0L
    }
    val weeklyAvgMinutes = weeklyTotalMinutes / 7
    val daysWithMain = (0..6).count { offset ->
        val day = today.minusDays(offset.toLong())
        last7Logs.any { it.sleepType == "main" && getEffectiveSleepDate(it) == day }
    }
    val consistencyPct = daysWithMain * 100 / 7
    val todaysMoodTarget = active ?: todayLogs.maxByOrNull { it.startTime }
    val lastCompleted = logs.filter { it.endTime != null }.maxByOrNull { it.startTime }
    val lastSleepText = lastCompleted?.let { log ->
        val start = TimeUtils.parseInstant(log.startTime); val end = log.endTime?.let { TimeUtils.parseInstant(it) }
        if (start != null && end != null) {
            val d = Duration.between(start, end)
            "${d.toHours()}${if (s.isThai) "ชม." else "h"} ${d.toMinutes() % 60}${if (s.isThai) "น." else "m"}"
        } else "-"
    } ?: "-"
    val latestWake = logs.filter { it.sleepType == "main" && it.endTime != null }.maxByOrNull { it.startTime }
        ?.let { TimeUtils.localTime(it.endTime) } ?: "-"

    val ageMonths = VaccineSetupCatalog.ageMonthsFromBirthdate(child?.birthdate ?: "")
    val wakeBand = SleepWakeWindows.forAgeMonths(ageMonths)

    // --- Stitch "Metrics Grid" + "Daily Metrics" source data -----------------
    // Stitch labels the first metric card "Deep Sleep". We only track sleep
    // start/stop — there is no sleep-stage detection anywhere in this app — so
    // the card keeps the Stitch styling but is labelled for what it actually
    // measures (the last completed main sleep). Claiming a sleep stage we can't
    // detect would be a false health signal.
    val lastMain = logs.filter { it.sleepType == "main" && it.endTime != null }.maxByOrNull { it.startTime }
    val mainSleepMinutes = lastMain?.let { log ->
        val start = TimeUtils.parseInstant(log.startTime)
        val end = log.endTime?.let { TimeUtils.parseInstant(it) }
        if (start != null && end != null) Duration.between(start, end).toMinutes().coerceAtLeast(0) else null
    }
    val mainSleepDeltaPct = if (mainSleepMinutes != null && weeklyAvgMinutes > 0) {
        ((mainSleepMinutes - weeklyAvgMinutes) * 100 / weeklyAvgMinutes).toInt()
    } else null
    val ratedLogs = last7Logs.mapNotNull { it.qualityStars }
    val qualityPct = if (ratedLogs.isNotEmpty()) ratedLogs.sum() * 100 / (ratedLogs.size * 5) else null

    val (recBedStart, _) = childViewModel.recommendedBedtime()
    val bedtimeText = TimeUtils.localTime(lastMain?.startTime)
    val wakeText = TimeUtils.localTime(lastMain?.endTime)
    val bedtimeOnTime = run {
        val actual = clockMinutes(bedtimeText)
        val recommended = clockMinutes(recBedStart)
        if (actual == null || recommended == null) null else actual <= recommended + 30
    }
    val wakeRefreshed = mainSleepMinutes?.let { it >= targetMinutes * 0.75 }

    ChildScreenScaffold(
        // Stitch labels this ศูนย์บัญชาการการนอน / "Sleep Command Center"; shortened per product decision.
        title = if (s.isThai) "ศูนย์การนอน" else "Sleep Center",
        currentTab = "sleep",
        navController = navController,
        snackbarHostState = snackbarHostState
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            // Hero + overlapping metric cards (Stitch HeroSection + MetricsGrid)
            item {
                Column(Modifier.shrinkBottom(28.dp)) {
                    SleepCommandHero(
                        active = active != null,
                        saving = state.sleepSaving,
                        elapsed = elapsed,
                        totalMinutesToday = totalMinutesToday,
                        targetHours = targetHours,
                        onToggle = { childViewModel.toggleSleepSession() },
                        onGuidance = { navController.navigate(Screen.SleepGuidance.route) },
                        onSchedule = { navController.navigate(Screen.SleepSchedule.route) },
                        // บันทึกการนอนย้อนหลัง -> Record Sleep History, NOT Add Sleep Time.
                        // AddSleepTime hardcodes today's date and cannot log a past night; Add Sleep
                        // Time is reached from Child Sleep Schedule (add/edit) instead.
                        onHistory = { navController.navigate(RECORD_SLEEP_HISTORY_ROUTE) },
                        // Design's first tile is ติดตามการนอน -> Pro Sleep Hub, not Insights.
                        onInsights = { navController.navigate(PRO_SLEEP_HUB_ROUTE) }
                    )
                    SleepMetricsGrid(
                        mainSleepMinutes = mainSleepMinutes,
                        deltaPct = mainSleepDeltaPct,
                        qualityPct = qualityPct,
                        snoreCount = monitor.todaySnoreCount,
                        cryCount = monitor.todayCryCount,
                        // "ตื่นกี่ครั้ง" — derived, not stored: every finished sleep session today
                        // is one wake-up. A dedicated column would need a migration and would drift
                        // out of sync with the very logs it duplicates.
                        wakeCount = todayLogs.count { it.endTime != null },
                        modifier = Modifier.padding(horizontal = 16.dp).offset(y = (-28).dp)
                    )
                }
            }

            // Stitch "Daily Metrics" glass rows
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (s.isThai) "ข้อมูลรายวัน" else "Daily Metrics",
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = { navController.navigate(RECORD_SLEEP_HISTORY_ROUTE) }) {
                            Icon(Icons.Filled.History, null, tint = Primary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (s.isThai) "ประวัติ" else "History", style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    DailyMetricRow(
                        icon = Icons.Filled.Bed,
                        tileColor = HeroBottom.copy(0.10f),
                        label = if (s.isThai) "เวลานอน" else "Bedtime",
                        value = bedtimeText ?: "--:--",
                        badge = when (bedtimeOnTime) {
                            true -> (if (s.isThai) "ตรงเวลา" else "On Time") to true
                            false -> (if (s.isThai) "ดึกกว่าแนะนำ" else "Late") to false
                            null -> null
                        },
                        // These edit an ACTUAL night's times, so they open Record Sleep History.
                        // Add Sleep Time now writes a recurring schedule instead.
                        onClick = { navController.navigate(RECORD_SLEEP_HISTORY_ROUTE) }
                    )
                    DailyMetricRow(
                        icon = Icons.Filled.LightMode,
                        tileColor = SecondaryFixed.copy(0.55f),
                        label = if (s.isThai) "เวลาตื่น" else "Wake Up",
                        value = wakeText ?: "--:--",
                        badge = when (wakeRefreshed) {
                            true -> (if (s.isThai) "สดชื่น" else "Refreshed") to true
                            false -> (if (s.isThai) "นอนสั้น" else "Short") to false
                            null -> null
                        },
                        onClick = { navController.navigate(RECORD_SLEEP_HISTORY_ROUTE) }
                    )
                }
            }

            // AI insight — Stitch dark glass card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = HeroBottom)
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.Psychology, null, tint = Color.White, modifier = Modifier.size(20.dp))
                            Text(if (s.isThai) "ข้อมูลเชิงลึกจาก AI" else "AI Insight", style = MaterialTheme.typography.titleSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        com.lullabyai.app.ui.components.AiTextOrTimeout(
                            text = chatState.sleepInsight,
                            timeoutMessage = if (s.isThai) "ไม่สามารถโหลดข้อมูลเพิ่มเติม" else "Unable to load additional info",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(0.9f)
                        )
                    }
                }
            }

            // Wake window quick tip
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Primary.copy(0.06f))) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Filled.Schedule, null, tint = Primary, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (s.isThai) "ช่วงตื่นที่แนะนำตามวัย" else "Recommended wake window", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Primary)
                            Text(
                                if (s.isThai) "${wakeBand.ageRangeTh}: ${wakeBand.windowTh}"
                                else "${wakeBand.ageRangeEn}: ${wakeBand.windowEn}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Cry / sound monitoring — Stitch "Optimal Environment" grid
            item {
                SoundMonitorCard(
                    monitor = monitor,
                    hasMicPermission = hasMicPermission,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    onToggle = {
                        if (monitor.monitoring) {
                            sleepMonitorViewModel.stopMonitoring()
                        } else if (!hasMicPermission) {
                            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            val ok = sleepMonitorViewModel.startMonitoring()
                            if (!ok) scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (s.isThai) "ไม่สามารถเปิดไมโครโฟนได้" else "Could not open microphone"
                                )
                            }
                        }
                    },
                    recentEvents = monitor.events.take(5)
                )
            }

            // เส้นเวลาการนอน (24 ชม.), เวลานอนรวมวันนี้ / จำนวนครั้ง, and เช็กอารมณ์ live on the
            // ติดตามการนอน hub instead — MAIN linked to the same data twice.

            // Quality stars for today's target log
            todaysMoodTarget?.let { target ->
                item {
                    QualityStarsCard(
                        stars = target.qualityStars,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        onRate = { stars ->
                            sleepMonitorViewModel.setQualityStars(target.id, stars) {
                                childViewModel.loadSleep()
                            }
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (s.isThai) "บันทึกคุณภาพการนอนแล้ว" else "Sleep quality saved"
                                )
                            }
                        }
                    )
                }
            }

            // Stitch "Recommended" section — Relaxing Sounds
            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (s.isThai) "แนะนำสำหรับคุณ" else "RECOMMENDED",
                        style = MaterialTheme.typography.labelSmall,
                        color = Outline,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    // Stitch's MAIN spec stops at this row; the full เครื่องเล่นเสียงสีขาว player is
                    // on the ติดตามการนอน hub.
                    RelaxingSoundsRow(noisePlayer)
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SleepStat(if (s.isThai) "นอนครั้งล่าสุด" else "Last sleep", lastSleepText, Modifier.weight(1f))
                        SleepStat(if (s.isThai) "เฉลี่ยต่อวัน" else "Avg/day", "${weeklyAvgMinutes / 60}${if (s.isThai) " ชม." else "h"}", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SleepStat(if (s.isThai) "ความสม่ำเสมอ" else "Consistency", "$consistencyPct%", Modifier.weight(1f))
                        SleepStat(if (s.isThai) "เวลาตื่นล่าสุด" else "Latest wake", latestWake, Modifier.weight(1f))
                    }
                }
            }

            item {
                Text(
                    if (s.isThai) "บันทึกล่าสุด" else "Recent sessions",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            if (logs.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) { Text(if (s.isThai) "ยังไม่มีบันทึกการนอน" else "No sleep logs yet", color = Outline) } }
            } else {
                items(logs.take(15), key = { it.id }) { log ->
                    SleepLogRow(
                        log = log,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        canDelete = canDelete,
                        onDelete = { childViewModel.deleteSleepLog(log.id) },
                        onRate = { stars ->
                            sleepMonitorViewModel.setQualityStars(log.id, stars) { childViewModel.loadSleep() }
                        }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Stitch HeroSection — gradient "Sleep Command Center" with tap-to-start ring,
// sleep-goal progress and the 2x2 quick-nav into the rest of the Sleep section.
// ---------------------------------------------------------------------------
@Composable
private fun SleepCommandHero(
    active: Boolean,
    saving: Boolean,
    elapsed: Duration?,
    totalMinutesToday: Long,
    targetHours: Int,
    onToggle: () -> Unit,
    onGuidance: () -> Unit,
    onSchedule: () -> Unit,
    onHistory: () -> Unit,
    onInsights: () -> Unit
) {
    val s = LocalStrings.current
    val targetMinutes = targetHours * 60
    val goalFraction = (totalMinutesToday / targetMinutes.toFloat()).coerceIn(0f, 1f)
    val goalPct = (goalFraction * 100).toInt()

    val pulse = rememberInfiniteTransition(label = "sleepPulse")
    val ringScale by pulse.animateFloat(
        initialValue = 1f, targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(3000, easing = EaseInOut), RepeatMode.Reverse),
        label = "ringScale"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp))
            .background(Brush.verticalGradient(listOf(HeroTop, HeroBottom)))
    ) {
        HeroWave(Modifier.align(Alignment.BottomCenter))

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 32.dp, bottom = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size((240 * ringScale).dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.05f))
                )
                Box(
                    Modifier
                        .size((216 * ringScale).dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.08f))
                )
                Surface(
                    onClick = onToggle,
                    enabled = !saving,
                    modifier = Modifier.size(200.dp),
                    shape = CircleShape,
                    color = Color.White.copy(0.10f),
                    border = BorderStroke(4.dp, PrimaryFixedDim)
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier.size(76.dp).clip(CircleShape).background(HeroBottom),
                            contentAlignment = Alignment.Center
                        ) {
                            if (saving) {
                                CircularProgressIndicator(modifier = Modifier.size(30.dp), strokeWidth = 3.dp, color = Color.White)
                            } else {
                                Icon(
                                    if (active) Icons.Filled.Hotel else Icons.Filled.Bedtime,
                                    null, tint = Color.White, modifier = Modifier.size(36.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (active) (if (s.isThai) "กำลังหลับ" else "Sleeping")
                            else (if (s.isThai) "แตะเพื่อเริ่ม" else "Tap to Start"),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.sp
                        )
                        Text(
                            if (active && elapsed != null)
                                "${elapsed.toHours()}${if (s.isThai) " ชม. " else "h "}${elapsed.toMinutes() % 60}${if (s.isThai) " นาที" else "m"}"
                            else (if (s.isThai) "เริ่มการนอน" else "Tracking"),
                            color = PrimaryFixed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // Sleep goal
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text(
                        (if (s.isThai) "นอนแล้ว: " else "Sleep: ") +
                            "${totalMinutesToday / 60}${if (s.isThai) " ชม. " else "h "}${totalMinutesToday % 60}${if (s.isThai) " น." else "m"}" +
                            " / $targetHours${if (s.isThai) " ชม." else "h"}",
                        color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp
                    )
                    Text(
                        if (s.isThai) "เป้าหมายการนอน" else "Sleep goal",
                        color = PrimaryFixed, fontSize = 13.sp
                    )
                }
                Text("$goalPct%", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { goalFraction },
                modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(100.dp)),
                color = PrimaryFixed,
                trackColor = OnPrimaryFixed.copy(0.30f),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                gapSize = 0.dp,
                drawStopIndicator = {}
            )

            Spacer(Modifier.height(28.dp))

            // 2x2 quick nav into the rest of the Sleep section
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroNavTile(Icons.Filled.Bedtime, if (s.isThai) "ติดตามการนอน" else "Sleep tracking", Modifier.weight(1f), onInsights)
                HeroNavTile(Icons.Filled.Lightbulb, if (s.isThai) "คำแนะนำ" else "Guidance", Modifier.weight(1f), onGuidance)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeroNavTile(Icons.Filled.CalendarToday, if (s.isThai) "ตาราง" else "Schedule", Modifier.weight(1f), onSchedule)
                HeroNavTile(Icons.Filled.History, if (s.isThai) "บันทึกย้อนหลัง" else "Log history", Modifier.weight(1f), onHistory)
            }
        }
    }
}

/** The Stitch hero's decorative wave (same cubic path, white at 10%). */
@Composable
private fun HeroWave(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(112.dp)) {
        val sx = size.width / 500f
        val sy = size.height / 150f
        val path = Path().apply {
            moveTo(0f, 49.98f * sy)
            cubicTo(149.99f * sx, 150f * sy, 349.89f * sx, -49.98f * sy, 500f * sx, 49.98f * sy)
            lineTo(500f * sx, 150f * sy)
            lineTo(0f, 150f * sy)
            close()
        }
        drawPath(path, Color.White.copy(0.10f))
    }
}

@Composable
private fun HeroNavTile(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.White.copy(0.10f),
        border = BorderStroke(1.dp, Color.White.copy(0.20f))
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

// ---------------------------------------------------------------------------
// Stitch MetricsGrid — two square metric cards + full-width snore analysis.
// ---------------------------------------------------------------------------
@Composable
private fun SleepMetricsGrid(
    mainSleepMinutes: Long?,
    deltaPct: Int?,
    qualityPct: Int?,
    snoreCount: Int,
    cryCount: Int,
    wakeCount: Int,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // "ร้องไห้กี่ครั้ง / ตื่นกี่ครั้ง" — the two things a tired parent actually wants at a
        // glance, so they sit above the sleep-duration cards rather than below the fold.
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                icon = Icons.Filled.VolumeUp,
                tileColor = TertiaryFixed,
                iconTint = Tertiary,
                label = if (s.isThai) "ร้องไห้" else "Crying",
                value = if (s.isThai) "$cryCount ครั้ง" else "$cryCount×",
                caption = if (s.isThai) "คืนนี้ (จากไมโครโฟน)" else "Tonight (from mic)",
                captionColor = OnSurfaceVariant,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MetricCard(
                icon = Icons.Filled.Bedtime,
                tileColor = PrimaryFixed,
                iconTint = Primary,
                label = if (s.isThai) "ตื่น" else "Wake-ups",
                value = if (s.isThai) "$wakeCount ครั้ง" else "$wakeCount×",
                caption = if (s.isThai) "ช่วงการนอนที่จบแล้ววันนี้" else "Completed sessions today",
                captionColor = OnSurfaceVariant,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard(
                icon = Icons.Filled.DarkMode,
                tileColor = PrimaryFixed,
                iconTint = Primary,
                label = if (s.isThai) "นอนหลัก" else "Main sleep",
                value = mainSleepMinutes?.let { "${it / 60}${if (s.isThai) " ชม. " else "h "}${it % 60}${if (s.isThai) " น." else "m"}" } ?: "—",
                caption = when {
                    deltaPct == null -> if (s.isThai) "ยังไม่มีข้อมูลเปรียบเทียบ" else "No baseline yet"
                    deltaPct >= 0 -> (if (s.isThai) "+$deltaPct% จากค่าเฉลี่ย" else "+$deltaPct% from average")
                    else -> (if (s.isThai) "$deltaPct% จากค่าเฉลี่ย" else "$deltaPct% from average")
                },
                captionColor = if (deltaPct != null && deltaPct >= 0) StatusNormal else OnSurfaceVariant,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            MetricCard(
                icon = Icons.Filled.CheckCircle,
                tileColor = SecondaryFixed,
                iconTint = Secondary,
                label = if (s.isThai) "คุณภาพ" else "Quality",
                value = qualityPct?.let { "$it%" } ?: "—",
                valueColor = Secondary,
                caption = when {
                    qualityPct == null -> if (s.isThai) "ยังไม่ได้ให้ดาว" else "Not rated yet"
                    qualityPct >= 80 -> if (s.isThai) "พักผ่อนดีมาก" else "Excellent rest"
                    qualityPct >= 60 -> if (s.isThai) "พอใช้" else "Fair rest"
                    else -> if (s.isThai) "ควรปรับปรุง" else "Needs work"
                },
                captionColor = OnSurfaceVariant,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }

        // Snore analysis (full width)
        Card(
            modifier = Modifier.fillMaxWidth().border(1.dp, OutlineVariant.copy(0.35f), RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(16.dp)).background(TertiaryFixed), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Mic, null, tint = Tertiary, modifier = Modifier.size(22.dp))
                    }
                    Column {
                        Text(if (s.isThai) "การวิเคราะห์การกรน" else "Snore Analysis", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            if (s.isThai) "จากไมโครโฟน (ประมาณ)" else "Mic-based estimate",
                            style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        if (snoreCount == 0) (if (s.isThai) "ไม่พบ" else "None detected")
                        else "$snoreCount${if (s.isThai) " ครั้ง" else " times"}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (s.isThai) "วันนี้" else "TODAY",
                        style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    tileColor: Color,
    iconTint: Color,
    label: String,
    value: String,
    caption: String,
    captionColor: Color,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        modifier = modifier.border(1.dp, OutlineVariant.copy(0.35f), RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.fillMaxHeight().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(12.dp)).background(tileColor), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(14.dp))
            Column {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = valueColor)
                Text(caption, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = captionColor)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Stitch "Daily Metrics" glass row.
// ---------------------------------------------------------------------------
@Composable
private fun DailyMetricRow(
    icon: ImageVector,
    tileColor: Color,
    label: String,
    value: String,
    badge: Pair<String, Boolean>?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainerLowest.copy(0.72f),
        border = BorderStroke(1.dp, OutlineVariant.copy(0.35f)),
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(tileColor), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Primary, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(0.75f), fontWeight = FontWeight.Bold)
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            badge?.let { (text, good) ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (good) BadgeGoodBg else BadgeInfoBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (good) BadgeGoodFg else BadgeInfoFg)
                }
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Outline.copy(0.5f), modifier = Modifier.size(20.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Stitch "Optimal Environment" — live mic monitoring tiles.
// ---------------------------------------------------------------------------
@Composable
private fun SoundMonitorCard(
    monitor: com.lullabyai.app.viewmodel.SleepMonitorUiState,
    hasMicPermission: Boolean,
    onToggle: () -> Unit,
    recentEvents: List<SleepSoundEvent>,
    modifier: Modifier = Modifier
) {
    val s = LocalStrings.current
    val soundLabel = when (monitor.liveLabel) {
        "cry_like" -> if (s.isThai) "คล้ายเสียงร้อง" else "Cry-like"
        "loud" -> if (s.isThai) "ดัง" else "Loud"
        "ambient" -> if (s.isThai) "ปกติ" else "Ambient"
        else -> if (s.isThai) "เงียบ" else "Quiet"
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (s.isThai) "สภาพแวดล้อมที่เหมาะสม" else "Optimal Environment",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Box(
                Modifier.clip(RoundedCornerShape(6.dp)).background(Primary.copy(0.10f)).padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    if (monitor.monitoring) (if (s.isThai) "กำลังตรวจจับ" else "LIVE MONITORING")
                    else (if (s.isThai) "ปิดอยู่" else "OFF"),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Primary, letterSpacing = 0.8.sp
                )
            }
        }
        Text(
            if (s.isThai)
                "ใช้ไมโครโฟนประมาณระดับเสียงบนเครื่อง (ไม่ใช่การวินิจฉัยทางการแพทย์) ข้อมูลอยู่ในเครื่อง/บัญชีของคุณเท่านั้น"
            else
                "Uses the mic for on-device loudness heuristics — not a medical diagnosis. Events stay in your account.",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant
        )
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EnvTile(
                icon = Icons.Filled.VolumeDown,
                title = if (s.isThai) "ระดับเสียง" else "Noise Level",
                value = if (monitor.monitoring) "${monitor.liveDb.toInt()}dB" else "—",
                badge = if (monitor.monitoring) soundLabel else (if (s.isThai) "ปิด" else "Off"),
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            EnvTile(
                icon = Icons.Filled.GraphicEq,
                title = if (s.isThai) "เสียง" else "Sound",
                value = soundLabel,
                badge = if (s.isThai) "สด" else "Live",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            EnvTile(
                icon = Icons.Filled.RecordVoiceOver,
                title = if (s.isThai) "ร้องไห้" else "Cry",
                value = "${monitor.todayCryCount}${if (s.isThai) " ครั้ง" else ""}",
                badge = if (s.isThai) "วันนี้" else "Today",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            EnvTile(
                icon = Icons.Filled.Hearing,
                title = if (s.isThai) "เสียงดัง/กรน" else "Loud / Snore",
                value = "${monitor.todayLoudCount + monitor.todaySnoreCount}${if (s.isThai) " ครั้ง" else ""}",
                badge = if (s.isThai) "วันนี้" else "Today",
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
        Button(
            onClick = onToggle,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(100.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (monitor.monitoring) HeroBottom else Primary
            )
        ) {
            Icon(
                if (monitor.monitoring) Icons.Filled.Mic else Icons.Filled.MicOff,
                null, modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    monitor.monitoring -> if (s.isThai) "หยุดฟัง" else "Stop listening"
                    !hasMicPermission -> if (s.isThai) "อนุญาตใช้ไมโครโฟน" else "Allow microphone"
                    else -> if (s.isThai) "เริ่มฟังเสียง" else "Start listening"
                },
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge
            )
        }
        if (recentEvents.isNotEmpty()) {
            Text(if (s.isThai) "เหตุการณ์ล่าสุด" else "Recent events", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            recentEvents.forEach { ev -> SoundEventRow(ev) }
        }
    }
}

@Composable
private fun EnvTile(
    icon: ImageVector,
    title: String,
    value: String,
    badge: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.border(1.dp, SurfaceContainerHighest, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.fillMaxHeight().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Primary.copy(0.07f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = Primary, modifier = Modifier.size(19.dp))
                }
                Text(
                    title.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant, maxLines = 2
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Box(
                    Modifier.clip(RoundedCornerShape(4.dp)).background(Primary.copy(0.10f)).padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(badge, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Primary, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SoundEventRow(ev: SleepSoundEvent) {
    val s = LocalStrings.current
    val typeLabel = when (ev.eventType) {
        "cry" -> if (s.isThai) "ร้องไห้ (ประมาณ)" else "Cry (approx.)"
        "snore_like" -> if (s.isThai) "คล้ายกรน" else "Snore-like"
        else -> if (s.isThai) "เสียงดัง" else "Loud sound"
    }
    val time = TimeUtils.localTime(ev.detectedAt) ?: "—"
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(SurfaceVariant.copy(0.45f)).padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(typeLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(
            "$time · ${ev.peakDb?.toInt()?.let { "${it}dB" } ?: ""}",
            style = MaterialTheme.typography.labelSmall,
            color = OnSurfaceVariant
        )
    }
}

/** Stitch "Recommended → Relaxing Sounds" row; taps play/pause the ambient engine. */
@Composable
private fun RelaxingSoundsRow(player: WhiteNoisePlayer) {
    val s = LocalStrings.current
    val isPlaying by player.isPlaying.collectAsState()
    Surface(
        onClick = { if (isPlaying) player.pause() else player.play() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceContainer,
        border = BorderStroke(1.dp, OutlineVariant.copy(0.5f))
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(SurfaceContainerHighest), contentAlignment = Alignment.Center) {
                Text("🌙", fontSize = 18.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(if (s.isThai) "เสียงช่วยให้นอนหลับ" else "Relaxing Sounds", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (isPlaying) (if (s.isThai) "กำลังเล่น" else "Playing")
                    else (if (s.isThai) "แตะเพื่อเล่น" else "Tap to play"),
                    style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant
                )
            }
            Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.ChevronRight, null, tint = Outline, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun QualityStarsCard(stars: Int?, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    Card(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (s.isThai) "คุณภาพการนอน" else "Sleep quality", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(if (s.isThai) "ให้ดาว 1–5 สำหรับช่วงนอนล่าสุด/วันนี้" else "Rate today's sleep session 1–5 stars", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                (1..5).forEach { n ->
                    IconButton(onClick = { onRate(n) }) {
                        Icon(
                            if (stars != null && n <= stars) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "$n",
                            tint = if (stars != null && n <= stars) Color(0xFFFFB300) else Outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SleepTimelineChart(todayLogs: List<SleepLog>, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    Column {
        Canvas(modifier = modifier.fillMaxWidth().height(56.dp)) {
            val barTop = size.height * 0.15f
            val barHeight = size.height * 0.7f
            todayLogs.forEach { log ->
                val startInstant = TimeUtils.parseInstant(log.startTime)
                val endInstant = log.endTime?.let { TimeUtils.parseInstant(it) } ?: Instant.now()
                if (startInstant != null) {
                    val startSec = startInstant.atZone(ZoneId.systemDefault()).toLocalTime().toSecondOfDay()
                    val endSec = endInstant.atZone(ZoneId.systemDefault()).toLocalTime().toSecondOfDay()
                    val x0 = (startSec / 86400f) * size.width
                    val x1 = (endSec / 86400f) * size.width
                    drawRoundRect(
                        color = Primary,
                        topLeft = Offset(x0, barTop),
                        size = Size((x1 - x0).coerceAtLeast(4f), barHeight),
                        cornerRadius = CornerRadius(4f, 4f)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00:00", "06:00", "12:00", "18:00", if (s.isThai) "ตอนนี้" else "now").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = Outline)
            }
        }
    }
}

@Composable
internal fun MoodGrid(selectedMood: String?, onSelect: (String) -> Unit) {
    val s = LocalStrings.current
    val moods = listOf(
        Triple("good", if (s.isThai) "อารมณ์ดี" else "Happy", "😊"),
        Triple("fussy", if (s.isThai) "งอแง" else "Fussy", "😣"),
        Triple("sleepy", if (s.isThai) "ง่วงนอน" else "Sleepy", "😴"),
        Triple("energetic", if (s.isThai) "ร่าเริง" else "Energetic", "🧸")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            moods.take(2).forEach { (key, label, emoji) -> MoodTile(emoji, label, key == selectedMood, Modifier.weight(1f)) { onSelect(key) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            moods.drop(2).forEach { (key, label, emoji) -> MoodTile(emoji, label, key == selectedMood, Modifier.weight(1f)) { onSelect(key) } }
        }
    }
}

@Composable
private fun MoodTile(emoji: String, label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(14.dp),
        color = if (selected) Primary.copy(0.12f) else SurfaceContainerLowest,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Primary else OutlineVariant)) {
        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(emoji, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) Primary else OnSurfaceVariant, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            if (selected) Icon(Icons.Filled.CheckCircle, null, tint = Primary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
internal fun NoisePlayerCard(player: WhiteNoisePlayer) {
    val s = LocalStrings.current
    val isPlaying by player.isPlaying.collectAsState()
    val elapsed by player.elapsedSeconds.collectAsState()
    val track by player.selectedTrack.collectAsState()
    val volume by player.volume.collectAsState()
    val duration by player.durationSecondsFlow.collectAsState()
    fun fmt(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = HeroBottom)
    ) {
        Box {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 40.dp, y = (-40).dp)
                    .size(120.dp)
                    .background(Brush.radialGradient(listOf(PrimaryFixedDim.copy(0.35f), Color.Transparent)))
            )
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Waves, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (s.isThai) "เครื่องเล่นเสียงขาว" else "White Noise Player", style = MaterialTheme.typography.labelLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            (if (s.isThai) "กำลังเล่น: " else "Playing: ") + if (s.isThai) track.labelTh else track.labelEn,
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f)
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { (elapsed / duration.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(100.dp)),
                    color = Color.White, trackColor = Color.White.copy(0.2f),
                    gapSize = 0.dp, drawStopIndicator = {}
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(fmt(elapsed), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f))
                    Text(fmt(duration), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { player.previousTrack() }) {
                        Icon(Icons.Filled.SkipPrevious, null, tint = Color.White)
                    }
                    IconButton(
                        onClick = { if (isPlaying) player.pause() else player.play() },
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White)
                    ) {
                        Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, tint = HeroBottom, modifier = Modifier.size(28.dp))
                    }
                    IconButton(onClick = { player.nextTrack() }) {
                        Icon(Icons.Filled.SkipNext, null, tint = Color.White)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.VolumeDown, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(18.dp))
                    Slider(
                        value = volume,
                        onValueChange = { player.setVolume(it) },
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(0.25f))
                    )
                    Icon(Icons.Filled.VolumeUp, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(18.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60).forEach { min ->
                        val selected = duration == min * 60
                        Surface(
                            onClick = { player.setTimerMinutes(min) },
                            shape = RoundedCornerShape(100.dp),
                            color = if (selected) Color.White else Color.White.copy(0.12f)
                        ) {
                            Text(
                                "${min}${if (s.isThai) " นาที" else "m"}",
                                Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) HeroBottom else Color.White
                            )
                        }
                    }
                }
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NoiseTrack.entries.forEach { t ->
                        val selected = t == track
                        Surface(
                            onClick = { player.selectTrack(t) },
                            shape = RoundedCornerShape(100.dp),
                            color = if (selected) Color.White else Color.White.copy(0.12f)
                        ) {
                            Text(
                                if (s.isThai) t.labelTh else t.labelEn,
                                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) HeroBottom else Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SleepStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.border(1.dp, OutlineVariant.copy(0.35f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, color = Primary, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SleepLogRow(log: SleepLog, canDelete: Boolean, onDelete: () -> Unit, onRate: (Int) -> Unit, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    val startInstant = TimeUtils.parseInstant(log.startTime)
    val endInstant = log.endTime?.let { TimeUtils.parseInstant(it) }
    val durationText = if (startInstant != null) {
        val dur = Duration.between(startInstant, endInstant ?: Instant.now())
        "${dur.toHours()}${if (s.isThai) "ชม." else "h"} ${dur.toMinutes() % 60}${if (s.isThai) "น." else "m"}"
    } else ""

    val startZone = startInstant?.atZone(ZoneId.systemDefault())
    val endZone = endInstant?.atZone(ZoneId.systemDefault())
    val isCrossDay = startZone != null && endZone != null && startZone.toLocalDate() != endZone.toLocalDate()
    val dFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM")

    val timeRangeText = if (isCrossDay) {
        "${startZone.format(dFmt)} ${TimeUtils.localTime(log.startTime)} - ${endZone.format(dFmt)} ${TimeUtils.localTime(log.endTime)}"
    } else {
        val datePrefix = startZone?.format(dFmt)?.let { "$it • " } ?: ""
        "$datePrefix${TimeUtils.localTime(log.startTime) ?: ""} - ${TimeUtils.localTime(log.endTime) ?: (if (s.isThai) "กำลังหลับ" else "ongoing")}"
    }

    Card(
        modifier = modifier.fillMaxWidth().border(1.dp, OutlineVariant.copy(0.35f), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceContainerLowest),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Primary.copy(0.1f)), contentAlignment = Alignment.Center) {
                    Icon(if (log.sleepType == "nap") Icons.Filled.WbSunny else Icons.Filled.Bedtime, null, tint = Primary, modifier = Modifier.size(18.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (log.sleepType == "nap") (if (s.isThai) "งีบ" else "Nap") else (if (s.isThai) "นอนหลัก" else "Main sleep"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isCrossDay && endZone != null) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = Primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = if (s.isThai) "🌙 ข้ามคืน (นับ ${endZone.format(dFmt)})" else "🌙 Overnight (${endZone.format(dFmt)})",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                    Text(
                        timeRangeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant
                    )
                }
                if (durationText.isNotBlank()) Text(durationText, style = MaterialTheme.typography.labelMedium, color = Primary, fontWeight = FontWeight.Bold)
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) { Icon(Icons.Filled.DeleteOutline, null, tint = Error, modifier = Modifier.size(16.dp)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (s.isThai) "คุณภาพ" else "Quality", style = MaterialTheme.typography.labelSmall, color = Outline)
                val qualityStars = log.qualityStars
                (1..5).forEach { n ->
                    IconButton(onClick = { onRate(n) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            if (qualityStars != null && n <= qualityStars) Icons.Filled.Star else Icons.Filled.StarBorder,
                            null,
                            tint = if (qualityStars != null && n <= qualityStars) Color(0xFFFFB300) else Outline,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
